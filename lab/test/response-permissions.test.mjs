import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {validateResponse,validateResponseAuthorization,responseHeads,responsePermissions,responseTargetPermissions} from '../web/managed-records.mjs';
const id=()=>crypto.randomUUID();
function fixture(){
 const members=['ADMIN','LEADER','MEMBER','MEMBER'].map(role=>({role,canTriage:role==='LEADER',memberId:id(),activityName:role,deviceIds:[id()]}));
 const roster={version:1,groupId:'permission-test',members},[admin,leader,owner,other]=members.map(m=>m.deviceIds[0]),incidentId=id();
 let sequence=0;const make=(actorDeviceId,change,eventId=id(),replaces=[])=>({groupId:roster.groupId,incidentId,eventId,actorDeviceId,sequence:++sequence,operatedAt:1,change,replaces});
 const reportId=id(),report=make(owner,{type:'REPORT',reportId,category:'道路',description:'倒木',latitude:35,longitude:135},reportId),target={kind:'REPORT',id:reportId};
 return {roster,admin,leader,owner,other,make,report,target,incidentId};
}

test('report actions use original member ownership after privileged corrections and reject unrelated viewers',()=>{
 const {roster,admin,leader,owner,other,make,report,target}=fixture(),corrected=make(admin,{...report.change,description:'確認済み'},id(),[report.eventId]),history=[report,corrected];
 for(const actor of [owner,admin,leader,other]){
  const allowed=actor!==other,p=responsePermissions(roster,history,actor,corrected);
  assert.equal(p.canEdit,allowed);assert.equal(p.canCancel,allowed);assert.equal(p.canChangePriority,[admin,leader].includes(actor));
  assert.equal(responseTargetPermissions(roster,history,actor,target,report).canEdit,allowed);
  for(const change of [{...report.change,cancelled:true,cancelReason:'重複'},{type:'ASSIGNMENT',target,memberId:roster.members.find(m=>m.deviceIds.includes(actor)).memberId,active:true},{type:'PROGRESS',target,status:'DONE',result:'確認完了'},{type:'MATERIAL',target,requestId:null,name:'水',unit:'本',needed:3,arranged:0}]){
   const eventId=id();if(change.type==='MATERIAL')change.requestId=eventId;
   const event=make(actor,change,eventId,change.type==='REPORT'?[corrected.eventId]:[]);
   if(allowed)assert.doesNotThrow(()=>validateResponseAuthorization(roster,history,event));else assert.throws(()=>validateResponseAuthorization(roster,history,event),{code:'response_forbidden'});
  }
  const priority=make(actor,{type:'PRIORITY',target,urgency:'HIGH',reason:'危険'});
  if([admin,leader].includes(actor))assert.doesNotThrow(()=>validateResponseAuthorization(roster,history,priority));else assert.throws(()=>validateResponseAuthorization(roster,history,priority));
 }
 const secondDevice=id();roster.members[2].deviceIds.push(secondDevice);assert.equal(responsePermissions(roster,history,secondDevice,corrected).canEdit,true);
 assert.equal(responsePermissions(roster,[corrected],owner,corrected).canEdit,false);
});

test('material correction and cancellation allows original material author, report owner and coordinators only',()=>{
 const f=fixture(),{roster,admin,leader,owner,other,make,report,target}=f;
 const materialId=id(),material=make(leader,{type:'MATERIAL',target,requestId:materialId,name:'水',unit:'本',needed:5,arranged:2},materialId),history=[report,material];
 for(const actor of [admin,leader,owner,other]){
  const correction=make(actor,{...material.change,cancelled:true,cancelReason:'不要と確認'},id(),[materialId]);
  assert.equal(responsePermissions(roster,history,actor,material).canCancel,actor!==other);
  if(actor===other)assert.throws(()=>validateResponseAuthorization(roster,history,correction));else assert.doesNotThrow(()=>validateResponseAuthorization(roster,history,correction));
 }
 const raw=JSON.stringify(material);validateResponse(roster,material);assert.equal(JSON.stringify(material),raw);
 for(const patch of [{cancelled:true,cancelReason:''},{cancelled:true,cancelReason:' '},{cancelled:null},{cancelReason:null},{cancelled:'yes'}])assert.throws(()=>validateResponse(roster,{...material,change:{...material.change,...patch}}));
 assert.doesNotThrow(()=>validateResponseAuthorization(roster,history,make(owner,{...material.change,unit:'箱',needed:1,arranged:0},id(),[materialId])));
 assert.throws(()=>validateResponseAuthorization(roster,history,make(other,{...material.change,unit:'箱',needed:1,arranged:0},id(),[materialId])));
 assert.throws(()=>validateResponseAuthorization(roster,history,make(admin,{...material.change,target:{kind:'PERSON',id:id()}},id(),[materialId])));
 const personMaterial={...material,actorDeviceId:other,change:{...material.change,target:{kind:'PERSON',id:id()}}};
 assert.equal(responsePermissions(roster,[personMaterial],other,personMaterial).canEdit,true);
 assert.equal(responsePermissions(roster,[personMaterial],owner,personMaterial).canEdit,false);
});

test('offline merge preserves signed bytes and hides forged edits even with out-of-order delivery',async()=>{
 const {roster,admin,owner,other,make,report,target}=fixture(),store=await Store.open(id(),indexedDB);await store.importRoster(roster,admin);
 try{
  const forged=[make(other,{...report.change,description:'不正な変更'},id(),[report.eventId]),make(other,{type:'PROGRESS',target,status:'DONE',result:'不正'}),make(other,{type:'ASSIGNMENT',target,memberId:roster.members[3].memberId,active:true})];
  const materialId=id();forged.push(make(other,{type:'MATERIAL',target,requestId:materialId,name:'不正',unit:'本',needed:1,arranged:0},materialId));
  const correction=make(owner,{...report.change,description:'正しい訂正'},id(),[report.eventId]);
  for(const event of [...forged,correction,report])await store.mergeResponse(event);
  const raw=await store.responseEvents(roster.groupId);assert.deepEqual(raw.find(e=>e.eventId===report.eventId),report);
  assert.deepEqual(responseHeads(roster,raw).map(e=>e.eventId),[correction.eventId]);
  const anotherIncident={...report,incidentId:id()};assert.throws(()=>validateResponseAuthorization(roster,[anotherIncident],make(owner,{type:'PROGRESS',target,status:'PLANNED',result:''})));
 }finally{store.close();}
});

test('material cancellation drops shortages and CSV entries while restore preserves its original unit',async()=>{
 const {roster,admin,owner,make,report,target,incidentId}=fixture(),store=await Store.open(id(),indexedDB);await store.importRoster(roster,admin);
 try{
  await store.commandQuake({groupId:roster.groupId,eventId:incidentId,actorDeviceId:admin,operatedAt:0,action:'TRAINING_START',training:true,title:'訓練',area:'試験'});
  await store.appendResponse(report);
  const requestId=id(),material=await store.appendResponse(make(owner,{type:'MATERIAL',target,requestId,name:'確認用資材',unit:'本',needed:5,arranged:1},requestId));
  assert.match(await store.exportCsv(roster.groupId,incidentId),/確認用資材/);
  const cancelled=await store.appendResponse(make(owner,{...material.change,cancelled:true,cancelReason:'不要'},id(),[material.eventId]));
  assert.doesNotMatch(await store.exportCsv(roster.groupId,incidentId),/確認用資材/);
  await store.appendResponse(make(owner,{...cancelled.change,cancelled:false,cancelReason:''},id(),[cancelled.eventId]));
  assert.match(await store.exportCsv(roster.groupId,incidentId),/確認用資材/);
 }finally{store.close();}
});
