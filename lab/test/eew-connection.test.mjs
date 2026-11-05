import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine} from '../web/sync.mjs';
import {quakeStates,managedIncidentOptions} from '../web/managed-records.mjs';
const id=()=>crypto.randomUUID(),group='isolated-eew';
const a=id(),b=id(),roster={version:1,groupId:group,members:[{memberId:a,activityName:'管理者',role:'ADMIN',deviceIds:[a]},{memberId:b,activityName:'受信者',role:'MEMBER',deviceIds:[b]}]};
const incident='ee000001-0020-4000-8000-261030120000';
const eew=(actor=b,at=100)=>({groupId:group,eventId:id(),actorDeviceId:actor,operatedAt:at,action:'EEW',training:false,targetId:incident,eew:{source:'wolfx',eventId:'20261030120000',issuedAt:at,receivedAt:at}});
const open=async()=>{const s=await Store.open(id(),indexedDB);await s.importRoster(roster,a);return s;};
test('EEW evidence forms without human votes and materializes an immutable source START',async()=>{
 const s=await open(),e=eew();await s.mergeQuake(e);
 assert.equal(quakeStates(roster,await s.quakeEvents(group)).length,1);
 assert.equal((await s.quakeEvents(group)).filter(e=>e.action==='REPORT').length,0);
 const start=(await s.incidents(group))[0];assert.equal(start.eventId,incident);assert.equal(start.authorName,'緊急地震速報');
 await s.mergeQuake(eew(a,200));assert.equal((await s.incidents(group)).length,1);s.close();
});
test('different serial receipts cannot reopen the same earthquake after administrator END',async()=>{
 const left=await open(),right=await open(),first=eew(b,100),later=eew(a,400);
 await left.mergeQuake(first);await left.commandQuake({groupId:group,eventId:id(),actorDeviceId:a,operatedAt:200,action:'END',training:false,targetId:incident});
 await right.mergeQuake(later);const end=(await left.quakeEvents(group)).find(e=>e.action==='END');
 await right.mergeQuake(end);assert.equal(quakeStates(roster,await right.quakeEvents(group))[0].closed,true);
 for(const e of await left.quakeEvents(group))await right.mergeQuake(e);
 for(const e of await right.quakeEvents(group))await left.mergeQuake(e);
 assert.deepEqual(quakeStates(roster,await left.quakeEvents(group)),quakeStates(roster,await right.quakeEvents(group)));left.close();right.close();
});
test('manual and EEW formations preserve answers under reordered delivery',async()=>{
 const s=await open(),e=eew(b,100);await s.mergeQuake(e);
 const answer={groupId:group,eventId:id(),incidentId:incident,authorId:b,authorName:'受信者',sequence:1,createdAt:150,kind:'ANSWER',training:false,safety:'SAFE'};await s.appendIncident(answer);
 for(const [actor,at] of [[a,10],[b,20]])await s.commandQuake({groupId:group,eventId:id(),actorDeviceId:actor,operatedAt:at,action:'REPORT',training:false,title:'地震',area:'地域'});
 const view=managedIncidentOptions(await s.incidents(group),roster,await s.quakeEvents(group));assert.equal(view.length,1);assert.equal(view[0].answers[b].eventId,answer.eventId);
 await assert.rejects(s.commandQuake({groupId:group,eventId:id(),actorDeviceId:b,operatedAt:200,action:'END',training:false,targetId:incident}));s.close();
});
test('EEW proof and its derived START never travel in legacy version messages',async()=>{
 const s=await open(),e=eew();await s.mergeQuake(e);const sent=[],node=new SyncEngine(group,a,s,(_,m)=>sent.push(m));await node.connected('old');
 await node.receive('old',{version:3,groupId:group,type:'INCIDENT_REQUEST',entries:[{originId:incident,revision:1}]});
 assert.equal(sent.some(m=>m.version<7&&(m.incident?.eventId===incident||m.quake?.action==='EEW'||m.entries?.some(x=>x.originId===incident))),false);s.close();
});
test('v7 incident and journal before EEW proof are not acknowledged or downgraded',async()=>{
 const s=await open(),sent=[],errors=[],node=new SyncEngine(group,a,s,(_,m)=>sent.push(m),e=>errors.push(e));await node.connected('updated');await node.connected('old');sent.length=0;
 const end={groupId:group,eventId:id(),actorDeviceId:a,operatedAt:200,action:'END',training:false,targetId:incident};
 await node.receive('updated',{version:7,groupId:group,type:'QUAKE_RECORD',quake:end});
 assert.equal((await s.quakeEvents(group)).length,0);assert.equal(sent.length,0);
 const response={groupId:group,eventId:id(),actorDeviceId:a,operatedAt:100,sequence:1,incidentId:null,change:{type:'AVAILABILITY',state:'AVAILABLE'},replaces:[]};
 await node.receive('updated',{version:7,groupId:group,type:'RESPONSE_RECORD',response});assert.equal((await s.responseEvents(group)).length,0);assert.equal(sent.length,0);
 const start={groupId:group,eventId:incident,incidentId:incident,authorId:incident,authorName:'緊急地震速報',sequence:1,createdAt:0,kind:'START',title:'地震',area:'京都府南部',training:false};
 await node.receive('updated',{version:7,groupId:group,type:'INCIDENT_RECORD',incident:start});assert.equal((await s.incidents(group)).length,0);assert.equal(sent.length,0);
 await node.receive('updated',{version:7,groupId:group,type:'QUAKE_RECORD',quake:eew()});
 await node.receive('updated',{version:7,groupId:group,type:'QUAKE_RECORD',quake:end});
 await node.receive('updated',{version:7,groupId:group,type:'RESPONSE_RECORD',response});assert.equal((await s.responseEvents(group)).length,1);
 assert.ok(sent.some(m=>m.version===7&&m.type==='QUAKE_ACK'&&m.entries[0].originId===end.eventId));
 assert.equal(quakeStates(roster,await s.quakeEvents(group))[0].closed,true);assert.deepEqual(errors,[]);s.close();
});
test('provider switched continuation shares the JMA earthquake identity',async()=>{
 const s=await open(),first=eew(),next=eew(a,400);next.eew.source='p2pquake';
 await s.mergeQuake(first);await s.commandQuake({groupId:group,eventId:id(),actorDeviceId:a,operatedAt:200,action:'END',training:false,targetId:incident});
 await s.mergeQuake(next);assert.equal(quakeStates(roster,await s.quakeEvents(group)).length,1);assert.equal(quakeStates(roster,await s.quakeEvents(group))[0].closed,true);s.close();
});
test('independent manual FORM and EEW incidents merge both answers in either order and share END',async()=>{
 for(const reverse of [false,true]){
  const manual=await open(),warning=await open();let manualId;
  for(const [actor,at] of [[a,10],[b,20]]){const report={groupId:group,eventId:id(),actorDeviceId:actor,operatedAt:at,action:'REPORT',training:false,title:'地震',area:'地域'};manualId??=report.eventId;await manual.commandQuake(report);}
  const firstAnswer={groupId:group,eventId:id(),incidentId:manualId,authorId:a,authorName:'管理者',sequence:1,createdAt:30,kind:'ANSWER',training:false,safety:'SAFE'};
  await manual.appendIncident(firstAnswer);await warning.mergeQuake(eew());
  const secondAnswer={...firstAnswer,eventId:id(),incidentId:incident,authorId:b,authorName:'受信者',createdAt:150,safety:'SOS'};await warning.appendIncident(secondAnswer);
  assert.ok((await manual.quakeEvents(group)).some(e=>e.action==='FORM'));
  const quakes=[...await manual.quakeEvents(group),...await warning.quakeEvents(group)],journal=[...await manual.incidents(group),...await warning.incidents(group)];
  for(const s of [manual,warning]){for(const e of reverse?quakes.toReversed():quakes)await s.mergeQuake(e);for(const e of reverse?journal.toReversed():journal)await s.mergeIncident(e);}
  const views=managedIncidentOptions(await warning.incidents(group),roster,await warning.quakeEvents(group));assert.equal(views.length,1);assert.equal(views[0].answers[a].eventId,firstAnswer.eventId);assert.equal(views[0].answers[b].eventId,secondAnswer.eventId);
  await warning.commandQuake({groupId:group,eventId:id(),actorDeviceId:a,operatedAt:200,action:'END',training:false,targetId:incident});
  for(const e of await warning.quakeEvents(group))await manual.mergeQuake(e);
  assert.equal(quakeStates(roster,await manual.quakeEvents(group))[0].closed,true);manual.close();warning.close();
 }
});
