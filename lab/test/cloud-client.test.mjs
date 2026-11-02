import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {CloudClient} from '../web/cloud-client.mjs';
import {eewIncidentId,quakeStates} from '../web/managed-records.mjs';
const id=()=>crypto.randomUUID();
const json=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'content-type':'application/json'}});
const profile=(group,device)=>({groupId:group,originId:device,revision:1,updatedAt:1,name:'架空',latitude:35,longitude:135,floor:'',room:'',safety:'UNREPORTED'});
async function setup(){const store=await Store.open(id(),indexedDB),device=await store.identity(),group=id(),member={memberId:device,activityName:'試験',role:'ADMIN',deviceIds:[device]},roster={version:1,groupId:group,members:[member]},membership={groupId:group,deviceId:device,groupName:'試験',role:'ADMIN',memberId:device,roster,rosterRevision:1,rosterHistory:[{revision:1,roster}]};return {store,device,group,membership};}

test('the default browser fetch retains its Window receiver',async()=>{
 const original=globalThis.fetch;
 globalThis.fetch=function(){assert.equal(this,globalThis);return Promise.resolve(json({ok:true}));};
 try{assert.deepEqual(await new CloudClient({},id()).request('/info'),{ok:true});}finally{globalThis.fetch=original;}
});

test('normal group sync refreshes municipality without requiring a membership revision',async()=>{
 const {store,device,group}=await setup();let calls=0;
 const client=new CloudClient(store,device);client.requireSession=async()=>({groupId:group});client.pull=async()=>0;
 client.roster=async()=>{calls++;await store.update('meta','municipality:'+group,()=>({code:'26100',name:'京都市'}));};
 try{await client.sync(group);assert.equal(calls,1);assert.equal((await store.read('meta','municipality:'+group)).code,'26100');}finally{store.close();}
});

test('client declares extended response support and a required update never advances its cursor',async()=>{
 const {store,device,group}=await setup();
 const client=new CloudClient(store,device,{fetcher:async(_,options)=>{assert.equal(options.headers['X-PlanD-Records-Version'],'6');return json({error:{code:'client_update_required',message:'最新版へ更新してください'}},426);}});
 try{await store.update('meta','cloud-cursor:'+group,()=>7);await assert.rejects(client.pull({groupId:group,token:'secret'}),{status:426});assert.equal(await store.read('meta','cloud-cursor:'+group),7);}finally{store.close();}
});
test('registration retries the same persisted secret after a lost response and retains existing device identity',async()=>{
 const {store,device,membership}=await setup();let fail=true;const bodies=[];
 const fetcher=async(_,options)=>{const b=JSON.parse(options.body);bodies.push(b);if(fail){fail=false;throw Error('network lost after commit');}return json({...membership,token:b.registrationToken});};
 try{await assert.rejects(new CloudClient(store,device,{fetcher}).register('create',{groupName:'試験',activityName:'試験'}));const session=await new CloudClient(store,device,{fetcher}).register('create',{groupName:'試験',activityName:'試験'});assert.deepEqual(bodies[0],bodies[1]);assert.equal(session.deviceId,device);assert.equal((await store.roster(session.groupId)).revision,1);}finally{store.close();}
});
test('failed merge never advances cursor; identical replay completes after restart',async()=>{
 const {store,device,group,membership}=await setup(),row=profile(group,device);await store.importCloudRoster(membership,device);
 const fetcher=async()=>json({records:[{cursor:1,collection:'residents',record:row}],nextCursor:1,hasMore:false,rosterRevision:1});
 const client=new CloudClient(store,device,{fetcher}),original=store.merge.bind(store);store.merge=async()=>{throw Error('disk full');};
 try{await assert.rejects(client.pull({groupId:group,token:'secret'}));assert.equal(await store.read('meta','cloud-cursor:'+group),undefined);store.merge=original;await client.pull({groupId:group,token:'secret'});assert.equal(await store.read('meta','cloud-cursor:'+group),1);assert.equal((await store.all(group)).length,1);}finally{store.close();}
});
test('uploads own committed records only and retries failed server saves without a false ACK',async()=>{
 const {store,device,group,membership}=await setup();await store.importCloudRoster(membership,device);await store.update('meta','cloud-session:'+group,()=>({groupId:group,token:'secret'}));await store.merge(profile(group,device));await store.merge(profile(group,id()));
 let fail=true;const uploaded=[];const fetcher=async(url,o)=>{if(url.endsWith('/roster'))return json(membership);if(o.method==='GET')return json({records:[],nextCursor:0,hasMore:false,rosterRevision:1});const r=JSON.parse(o.body).records[0];uploaded.push(r);return fail?json({error:{code:'storage_unavailable',message:'retry'}},503):json({acks:[{collection:r.collection,key:r.record.originId,cursor:1,status:'stored'}],rosterRevision:1});};
 try{const client=new CloudClient(store,device,{fetcher});assert.equal((await client.sync(group)).pending,1);fail=false;assert.equal((await client.sync(group)).sent,1);assert.equal((await client.sync(group)).sent,0);assert.equal(uploaded.length,2);assert.ok(uploaded.every(x=>x.record.originId===device));}finally{store.close();}
});

test('a rejected invitation can be corrected without losing an uncertain committed registration',async()=>{
 const {store,device,group,membership}=await setup();let calls=0;
 const fetcher=async(_,options)=>{calls++;const body=JSON.parse(options.body);return body.code==='bad'?json({error:{code:'invalid_invite',message:'expired'}},403):json(membership);};
 try{const client=new CloudClient(store,device,{fetcher});await assert.rejects(client.register('join',{groupId:group,code:'bad',activityName:'試験'}));await client.register('join',{groupId:group,code:'corrected',activityName:'試験'});assert.equal(calls,2);}finally{store.close();}
});

test('registration survives selection failure and copies only own profile once with unreported safety',async()=>{
 const {store,device,group,membership}=await setup(),source=id();await store.update('meta','group',()=>source);await store.merge({...profile(source,device),safety:'SOS'});await store.merge(profile(source,id()));
 const original=store.update.bind(store);let fail=true;store.update=async(table,key,change)=>{if(key==='group'&&fail){fail=false;throw Error('disk full');}return original(table,key,change);};
 const client=new CloudClient(store,device,{fetcher:async()=>json(membership)}),input={groupName:'試験',activityName:'試験'};
 try{await assert.rejects(client.register('create',input));assert.ok(await client.session(group));assert.ok(await store.read('meta','cloud-registration:create'));assert.equal((await store.get(group,device)).safety,'UNREPORTED');await store.saveOwn({...profile(group,device),safety:'SAFE'});await client.register('create',input);assert.equal(await store.read('meta','group'),group);assert.equal(await store.read('meta','cloud-registration:create'),null);assert.equal((await store.get(group,device)).safety,'SAFE');assert.equal((await store.all(group)).length,1);assert.equal((await store.get(source,device)).safety,'SOS');assert.equal(await store.identity(),device);}finally{store.close();}
});

test('two tabs share one persisted registration token when a committed response is lost',async()=>{
 const {store,device,group,membership}=await setup(),bodies=[];let release;const arrived=new Promise(resolve=>release=resolve);
 const fetcher=async(_,options)=>{const body=JSON.parse(options.body);bodies.push(body);if(bodies.length===1){await arrived;throw Error('first response lost');}release();return json(membership);};
 try{const input={groupId:group,code:'test',activityName:'試験'};const results=await Promise.allSettled([new CloudClient(store,device,{fetcher}).register('join',input),new CloudClient(store,device,{fetcher}).register('join',input)]);assert.deepEqual(bodies[0],bodies[1]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.equal((await new CloudClient(store,device).session(group)).token,bodies[0].registrationToken);}finally{store.close();}
});

test('a delayed rejection cannot erase a corrected invitation pending in another tab',async()=>{
 const {store,device,group}=await setup();let release,started;const waiting=new Promise(r=>started=r);const delayed=new Promise(r=>release=r);
 const fetcher=async(_,o)=>{if(JSON.parse(o.body).code==='old'){started();await delayed;return json({error:{code:'invalid_invite'}},403);}throw Error('new response lost');};
 const client=new CloudClient(store,device,{fetcher}),old={groupId:group,code:'old',activityName:'test'};
 try{const first=client.register('join',old);const firstRejected=assert.rejects(first);await waiting;await store.update('meta','cloud-registration:join',()=>null);await assert.rejects(client.register('join',{...old,code:'corrected'}));const pending=await store.read('meta','cloud-registration:join');release();await firstRejected;assert.deepEqual(await store.read('meta','cloud-registration:join'),pending);}finally{store.close();}
});

const warning=(group,device,eventId,time)=>({groupId:group,eventId:id(),actorDeviceId:device,operatedAt:time,action:'EEW',training:false,targetId:eewIncidentId({eventId}),eew:{source:'wolfx',eventId,issuedAt:time,receivedAt:time}});
const ending=(group,device,incidentId,time)=>({groupId:group,eventId:id(),actorDeviceId:device,operatedAt:time,action:'END',training:false,targetId:incidentId});
const journalFetcher=records=>async url=>{const after=Number(new URL(url,'https://test.invalid').searchParams.get('after'));const rows=records.map((record,i)=>({collection:'quakes',record,cursor:i+1})).filter(row=>row.cursor>after);return json({records:rows,nextCursor:rows.at(-1)?.cursor??after,hasMore:false,rosterRevision:1});};

test('a new actual disaster replaces a historical selection and selection recovers after durable merge interruption',async()=>{
 const {store,device,group,membership}=await setup();await store.importCloudRoster(membership,device);
 const old=warning(group,device,'20261030120000',100),next=warning(group,device,'20261030130000',200),selection='incident:'+group;
 await store.mergeQuake(old,device);await store.mergeQuake(ending(group,device,old.targetId,110),device);await store.update('meta',selection,()=>old.targetId);
 const original=store.update.bind(store);let fail=true;store.update=async(table,key,change)=>{if(key===selection&&fail){fail=false;throw Error('selection interrupted');}return original(table,key,change);};
 const fetcher=journalFetcher([next]);
 try{
  await assert.rejects(new CloudClient(store,device,{fetcher}).pull({groupId:group}),/selection interrupted/);
  assert.ok((await store.quakeEvents(group)).some(e=>e.eventId===next.eventId));
  assert.equal(await store.read('meta',selection),old.targetId);
  assert.equal(await store.read('meta','cloud-cursor:'+group),undefined);
  store.update=original;
  await new CloudClient(store,device,{fetcher}).pull({groupId:group});
  assert.equal(await store.read('meta',selection),next.targetId);
  assert.equal(await store.read('meta','cloud-cursor:'+group),1);
 }finally{store.close();}
});

test('a new actual disaster replaces a training selection but duplicate arrivals do not select again',async()=>{
 const {store,device,group,membership}=await setup(),other=id(),training=id();
 const roster={...membership.roster,members:[...membership.roster.members,{memberId:other,activityName:'Member',role:'MEMBER',deviceIds:[other]}]};
 await store.importCloudRoster({...membership,roster,rosterHistory:[{revision:1,roster}]},device);
 for(const [actor,eventId,time] of [[device,training,10],[other,id(),11]])await store.mergeQuake({groupId:group,eventId,actorDeviceId:actor,operatedAt:time,action:'REPORT',training:true,title:'training',area:'test'},device);
 assert.ok(quakeStates(await store.roster(group),await store.quakeEvents(group)).some(s=>s.formation.training&&!s.closed));
 await store.update('meta','incident:'+group,()=>training);
 const next=warning(group,device,'20261030130000',200),fetcher=journalFetcher([next]);
 try{
  await new CloudClient(store,device,{fetcher}).pull({groupId:group});
  assert.equal(await store.read('meta','incident:'+group),next.targetId);
  await store.update('meta','incident:'+group,()=>training);
  // A later journal row may contain another device's receipt of the same JMA event.
  await new CloudClient(store,device,{fetcher:journalFetcher([next,{...next,eventId:id(),operatedAt:201,eew:{...next.eew,receivedAt:201}}])}).pull({groupId:group});
  assert.equal(await store.read('meta','incident:'+group),training);
 }finally{store.close();}
});

test('an already selected active actual incident survives a delayed earlier receipt that merges aliases',async()=>{
 const {store,device,group,membership}=await setup();await store.importCloudRoster(membership,device);
 const selected=warning(group,device,'20261030130000',200),earlier=warning(group,device,'20261030120000',100);
 await store.mergeQuake(selected,device);await store.update('meta','incident:'+group,()=>selected.targetId);
 try{await new CloudClient(store,device,{fetcher:journalFetcher([earlier])}).pull({groupId:group});assert.equal(await store.read('meta','incident:'+group),selected.targetId);}finally{store.close();}
});

test('an actual incident already ended in the received page never replaces the selected view',async()=>{
 const {store,device,group,membership}=await setup();await store.importCloudRoster(membership,device);
 const next=warning(group,device,'20261030130000',200);await store.update('meta','incident:'+group,()=> 'legacy');
 try{await new CloudClient(store,device,{fetcher:journalFetcher([next,ending(group,device,next.targetId,210)])}).pull({groupId:group});assert.equal(await store.read('meta','incident:'+group),'legacy');assert.equal(quakeStates(await store.roster(group),await store.quakeEvents(group))[0].closed,true);}finally{store.close();}
});
