import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine} from '../web/sync.mjs';
import {validateQuake,quakeStates,formationCandidates,managedIncidentOptions} from '../web/managed-records.mjs';
const id=()=>crypto.randomUUID(),group='admin-training';
const members=['ADMIN','MEMBER','LEADER','ADMIN'].map((role,i)=>{const memberId=id();return {memberId,deviceIds:[memberId],role,activityName:'架空'+i};});
const roster={version:1,groupId:group,members},devices=members.map(m=>m.memberId);
const start=(actor=devices[0],at=100)=>({groupId:group,eventId:id(),actorDeviceId:actor,operatedAt:at,action:'TRAINING_START',training:true,title:'避難訓練',area:'架空地区'});
const end=(incidentId,at=200,actor=devices[0])=>({groupId:group,eventId:id(),actorDeviceId:actor,operatedAt:at,action:'END',training:true,targetId:incidentId});
async function open(device=devices[0],r=roster){const s=await Store.open(id(),indexedDB);await s.importRoster(r,device);return s;}

test('v10 responses preserve cancellation and planned status through a training relay',async()=>{
 const a=await open(),b=await open(devices[1]),sent=[],errors=[];const training=start();
 try{
  await a.commandQuake(training);await b.mergeQuake(training);
  const reportId=id(),basic={groupId:group,eventId:reportId,actorDeviceId:devices[0],operatedAt:110,sequence:1,incidentId:training.eventId,change:{type:'REPORT',reportId,category:'道路閉塞',latitude:35,longitude:139,description:'倒木'},replaces:[]};
  const cancelled={...basic,eventId:id(),sequence:2,replaces:[reportId],change:{...basic.change,cancelled:true,cancelReason:'重複'}},planned={...basic,eventId:id(),sequence:3,change:{type:'PROGRESS',target:{kind:'REPORT',id:reportId},status:'PLANNED',result:''}};
  for(const record of [basic,planned,cancelled])await a.mergeResponse(record);
  const sender=new SyncEngine(group,devices[0],a,(_,m)=>sent.push(m),e=>errors.push(e));await sender.connected('B');sent.length=0;
  await sender.receive('B',{version:9,groupId:group,type:'RESPONSE_REQUEST',entries:[basic,planned,cancelled].map(e=>({originId:e.eventId,revision:1}))});
  const records=sent.filter(m=>m.type==='RESPONSE_RECORD');assert.deepEqual(records.map(m=>m.version),[9,10,10]);
  const replies=[],receiver=new SyncEngine(group,devices[1],b,(_,m)=>replies.push(m),e=>errors.push(e));await receiver.connected('A');replies.length=0;
  for(const record of records)await receiver.receive('A',record);
  assert.deepEqual(replies.filter(m=>m.type==='RESPONSE_ACK').map(m=>m.version),[9,10,10]);
  const heads=await b.responseHeads(group);assert.ok(heads.some(e=>e.change.cancelled));assert.ok(heads.some(e=>e.change.status==='PLANNED'));assert.deepEqual(errors,[]);
 }finally{a.close();b.close();}
});

test('one administrator explicitly starts training while actual and legacy training reports still require quorum',async()=>{
 const single={...roster,members:[members[0]]},s=await open(devices[0],single);
 try{
  for(const training of [false,true])await s.commandQuake({...start(),action:'REPORT',training});
  assert.equal(quakeStates(single,await s.quakeEvents(group)).length,0);assert.deepEqual(formationCandidates(single,await s.quakeEvents(group)),[]);
  const e=start();await s.commandQuake(e);const state=quakeStates(single,await s.quakeEvents(group));assert.equal(state.length,1);assert.equal(state[0].formation.incidentId,e.eventId);assert.equal(state[0].formation.training,true);
  assert.equal((await s.incidents(group))[0].kind,'START');assert.equal((await s.incidents(group))[0].eventId,e.eventId);
 }finally{s.close();}
});

test('training start rejects member, leader, false training and invalid evidence without changing records',async()=>{
 const s=await open();try{
  for(const actor of [devices[1],devices[2]])await assert.rejects(s.commandQuake(start(actor)));
  for(const change of [{training:false},{targetId:id()},{reportIds:[id()]},{title:''},{area:''},{eew:{}}])assert.throws(()=>validateQuake(roster,{...start(),...change}));
  assert.equal((await s.quakeEvents(group)).length,0);assert.equal((await s.incidents(group)).length,0);
 }finally{s.close();}
});

test('training start is idempotent, rejects local restart while active, and preserves answers across end and restoration',async()=>{
 const s=await open();const e=start();try{
  await s.commandQuake(e);assert.equal(await s.commandQuake(e),false);await assert.rejects(s.commandQuake(start()));
  await s.saveOwn({groupId:group,originId:devices[0],revision:1,updatedAt:100,name:'架空管理者',latitude:35,longitude:139,floor:'',room:'',safety:'UNREPORTED'});
  await s.appendIncident({groupId:group,eventId:id(),incidentId:e.eventId,authorId:devices[0],authorName:'架空管理者',createdAt:101,kind:'ANSWER',training:true,safety:'SAFE'});
  await assert.rejects(s.commandQuake(end(e.eventId,200,devices[1])));await s.commandQuake(end(e.eventId));
  const next=start(devices[0],300);await s.commandQuake(next);const name=s.name;s.close();const reopened=await Store.open(name,indexedDB);
  try{const views=managedIncidentOptions(await reopened.incidents(group),roster,await reopened.quakeEvents(group));assert.equal(views.length,2);const previous=views.find(v=>v.start.eventId===e.eventId);assert.equal(previous.closed,true);assert.equal(previous.answers[devices[0]].safety,'SAFE');assert.equal((await reopened.get(group,devices[0])).name,'架空管理者');}finally{reopened.close();}
 }finally{s.close();}
});

test('parallel administrator starts and late END converge regardless of delivery order',async()=>{
 const a=start(devices[0],100),b=start(devices[3],101),ended=end(b.eventId,200);
 const stores=await Promise.all([open(),open()]);try{
  for(const [s,events] of [[stores[0],[a,b,ended]],[stores[1],[ended,b,a]]])for(const e of events)await s.mergeQuake(e);
  const state=quakeStates(roster,await stores[0].quakeEvents(group));assert.equal(state.length,1);assert.equal(state[0].closed,true);assert.deepEqual(new Set(state[0].incidentIds),new Set([a.eventId,b.eventId]));assert.deepEqual(quakeStates(roster,await stores[1].quakeEvents(group)),state);
 }finally{for(const s of stores)s.close();}
});

test('v9 relay survives restart; old wire cannot acknowledge or downgrade explicit training',async()=>{
 const a=await open(),b=await open(devices[1]),c=await open(devices[2]),stores=[a,b,c],queue=[],errors=[],sent=[];
 const nodes=stores.map((s,i)=>new SyncEngine(group,devices[i],s,(to,m)=>{sent.push(m);queue.push([i,Number(to),m]);},e=>errors.push(e)));
 const drain=async()=>{let n=0;while(queue.length){assert.ok(++n<500);const[from,to,m]=queue.shift();await nodes[to].receive(String(from),m);}};
 try{
  const e=start();await a.commandQuake(e);await nodes[0].connected('1');await nodes[1].connected('0');await drain();assert.equal(quakeStates(roster,await b.quakeEvents(group)).length,1);
  assert.ok(sent.filter(m=>/^(QUAKE|INCIDENT|RESPONSE)_/.test(m.type)).every(m=>m.version===9));
  nodes[0].disconnected('1');nodes[1].disconnected('0');const name=b.name;b.close();nodes[1].store=await Store.open(name,indexedDB);
  await nodes[1].connected('2');await nodes[2].connected('1');await drain();assert.equal(quakeStates(roster,await c.quakeEvents(group))[0].formation.incidentId,e.eventId);assert.deepEqual(errors,[]);
  const fresh=await open(devices[1],{...roster,revision:1}),messages=[],node=new SyncEngine(group,devices[1],fresh,(_,m)=>messages.push(m));try{await node.connected('old');messages.length=0;for(const version of [6,7,8])await node.receive('old',{version,groupId:group,type:'QUAKE_RECORD',quake:e});assert.equal((await fresh.quakeEvents(group)).length,0);assert.equal(messages.length,0);}finally{fresh.close();}
 }finally{for(const n of nodes)n.store.close();}
});

test('v9 proof must commit before related records ACK, and roster-v8 replies cannot downgrade v9 requests',async()=>{
 const s=await open(devices[1],{...roster,revision:1}),sent=[],errors=[],node=new SyncEngine(group,devices[1],s,(_,m)=>sent.push(m),e=>errors.push(e)),e=start();
 try{
  await node.connected('sender');sent.length=0;
  await node.receive('sender',{version:9,groupId:group,type:'QUAKE_SUMMARY',entries:[{originId:e.eventId,revision:1}]});assert.equal(sent.at(-1).version,9);assert.equal(sent.at(-1).type,'QUAKE_REQUEST');sent.length=0;
  const records=[{type:'QUAKE_RECORD',quake:end(e.eventId)},{type:'INCIDENT_RECORD',incident:{groupId:group,eventId:e.eventId,incidentId:e.eventId,authorId:devices[0],authorName:'架空',sequence:1,createdAt:100,kind:'START',title:'避難訓練',area:'架空地区',training:true}},{type:'RESPONSE_RECORD',response:{groupId:group,eventId:id(),actorDeviceId:devices[0],operatedAt:110,sequence:1,incidentId:null,change:{type:'AVAILABILITY',state:'AVAILABLE'}}}];
  for(const r of records)await node.receive('sender',{version:9,groupId:group,...r});assert.equal(sent.length,0);assert.equal((await s.quakeEvents(group)).length,0);assert.equal((await s.incidents(group)).length,0);assert.equal((await s.responseEvents(group)).length,0);
  const original=s.mergeQuake.bind(s);s.mergeQuake=async()=>{throw Error('disk full');};await node.receive('sender',{version:9,groupId:group,type:'QUAKE_RECORD',quake:e});assert.equal(sent.length,0);s.mergeQuake=original;
  await node.receive('sender',{version:9,groupId:group,type:'QUAKE_RECORD',quake:e});assert.ok(sent.some(m=>m.type==='QUAKE_ACK'&&m.version===9));assert.equal((await s.incidents(group)).length,1);assert.deepEqual(errors,['disk full']);
 }finally{s.close();}
});
