import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {CloudClient} from '../web/cloud-client.mjs';
import {appendScopedIncident} from '../web/incidents.mjs';
const id=()=>crypto.randomUUID();
test('venue registration retains one identity after lost response without storing a password or fake home',async()=>{
 const store=await Store.open(id(),indexedDB),device=await store.identity(),group=id(),incident=id();
 const member={memberId:id(),deviceIds:[device],activityName:'会場参加者',role:'MEMBER'},admin={memberId:id(),deviceIds:[id()],activityName:'主催者',role:'ADMIN'};
 const roster={version:1,groupId:group,members:[admin,member]};
 const venue={version:1,mode:'VENUE_DEMO',groupId:group,sessionId:incident,incidentId:incident,areaId:'kyoto-gosho-south',closed:false,placements:[{sessionId:incident,deviceId:device,memberId:member.memberId,name:member.activityName,slotId:0,latitude:35.014,longitude:135.763,source:'DEMO_ASSIGNED'}]};
 const bodies=[];let lost=true;
 const client=new CloudClient(store,device,{fetcher:async(url,options)=>{
  assert.ok(url.endsWith('/venue/join'));const body=JSON.parse(options.body);bodies.push(body);if(lost){lost=false;throw Error('lost after commit');}
  return Response.json({groupId:group,groupName:'会場',deviceId:device,memberId:member.memberId,role:'MEMBER',token:body.registrationToken,roster,rosterRevision:1,rosterHistory:[{revision:1,roster}],venue});
 }});
 const input={groupName:'会場',password:'fictional-venue-password',activityName:member.activityName};
 try {
  assert.equal(typeof client.joinVenue,'function','Venue joining must be implemented');
  await assert.rejects(client.joinVenue(input),/lost/);
  assert.equal(JSON.stringify(await store.read('meta')).includes(input.password),false);
  await client.joinVenue(input);assert.equal(bodies[0].registrationToken,bodies[1].registrationToken);
  assert.deepEqual(await store.read('meta','venue:'+group),venue);assert.equal((await store.all(group)).length,0);
  await store.mergeQuake({groupId:group,eventId:incident,actorDeviceId:admin.deviceIds[0],operatedAt:1,action:'TRAINING_START',training:true,title:'会場デモ',area:'京都'},device);
  const answer=await appendScopedIncident(store,{groupId:group,incidentId:incident,authorId:device},'ANSWER',{safety:'SAFE'});
  assert.equal(answer.authorName,member.activityName);assert.equal((await store.all(group)).length,0);
  await assert.rejects(appendScopedIncident(store,{groupId:group,incidentId:id(),authorId:device},'ANSWER',{safety:'SAFE'}));
 }finally {store.close();}
});
