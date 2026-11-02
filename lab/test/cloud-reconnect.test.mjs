import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {CloudClient} from '../web/cloud-client.mjs';

test('a group selected during a failed in-flight sync uploads only its own records and uses its own cursor',async()=>{
 const store=await Store.open(crypto.randomUUID(),indexedDB),device=await store.identity();
 const groups=[crypto.randomUUID(),crypto.randomUUID()];
 for(const [index,group]of groups.entries()){
  const roster={version:1,groupId:group,members:[{memberId:device,activityName:'架空',role:'ADMIN',deviceIds:[device]}]};
  await store.importCloudRoster({groupId:group,groupName:'架空',roster,rosterRevision:1,rosterHistory:[{revision:1,roster}]},device);
  await store.update('meta','cloud-session:'+group,()=>({groupId:group,token:'test-'+index}));
  await store.update('meta','cloud-cursor:'+group,()=>index+4);
  await store.merge({groupId:group,originId:device,revision:1,updatedAt:1,name:'架空'+index,latitude:35,longitude:139,floor:'',room:'',safety:'UNREPORTED'});
 }
 let rejectFirst,started;const firstStarted=new Promise(r=>started=r),uploaded=[];
 const client=new CloudClient(store,device,{fetcher:async(url,options)=>{
  const request=new URL(url,'https://test.invalid'),group=request.pathname.split('/')[4];
  if(group===groups[0]){started();return new Promise((_,reject)=>rejectFirst=reject);}
  assert.equal(group,groups[1]);assert.equal(options.headers.Authorization,'Bearer test-1');
  if(request.pathname.endsWith('/roster')){const roster={version:1,groupId:group,members:[{memberId:device,activityName:'架空',role:'ADMIN',deviceIds:[device]}]};return Response.json({groupId:group,groupName:'架空',roster,rosterRevision:1,rosterHistory:[{revision:1,roster}]});}
  if(options.method==='GET'){assert.equal(request.searchParams.get('after'),'5');return Response.json({records:[],nextCursor:5,hasMore:false,rosterRevision:1});}
  const row=JSON.parse(options.body).records[0];uploaded.push(row);
  return Response.json({acks:[{collection:row.collection,key:row.record.originId,status:'stored'}]});
 }});
 try{
  const first=client.sync(groups[0]),failed=assert.rejects(first,/connection lost/);await firstStarted;
  const second=client.sync(groups[1]);rejectFirst(Error('connection lost'));await failed;
  assert.equal((await second).sent,1);assert.equal(uploaded.length,1);assert.equal(uploaded[0].record.groupId,groups[1]);
  assert.equal(await store.read('meta','cloud-cursor:'+groups[0]),4);assert.equal(await store.read('meta','cloud-cursor:'+groups[1]),5);
  assert.equal((await store.get(groups[0],device)).name,'架空0');
 }finally{store.close();}
});
