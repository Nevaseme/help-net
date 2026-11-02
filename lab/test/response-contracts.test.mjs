import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {validateResponse,responseHeads} from '../web/managed-records.mjs';
const id=()=>crypto.randomUUID(),group='response-contracts';
async function fixture(){
 const ids=[id(),id(),id()],roster={groupId:group,version:1,members:ids.map((x,i)=>({memberId:x,deviceIds:[x],activityName:['管理者','確認班','連絡班'][i],role:i?'MEMBER':'ADMIN'}))};
 const store=await Store.open(id(),indexedDB);await store.importRoster(roster,ids[0]);const incident=id();
 await store.commandQuake({groupId:group,eventId:incident,actorDeviceId:ids[0],operatedAt:1,action:'TRAINING_START',training:true,title:'訓練',area:'試験地域'});
 const make=(change,actor=ids[1],eventId=id(),replaces=[])=>({groupId:group,eventId,actorDeviceId:actor,incidentId:incident,operatedAt:2,change,replaces});
 const reportId=id(),report=await store.appendResponse(make({type:'REPORT',reportId,category:'道路・未設定',description:'取消ではない本文',latitude:35,longitude:139,regionIds:['north']},ids[1],reportId));
 return {store,ids,roster,incident,make,report,target:{kind:'REPORT',id:reportId}};
}
test('report cancellation requires a reason, original ownership and stable ID; restore retains full history',async()=>{
 const f=await fixture();const {store,ids,report,make,incident}=f;
 try{
  await assert.rejects(store.appendResponse(make({...report.change,cancelled:true,cancelReason:' '},ids[1],id(),[report.eventId])));
  await assert.rejects(store.appendResponse(make({...report.change,cancelled:true,cancelReason:'誤報'},ids[2],id(),[report.eventId])));
  await assert.rejects(store.appendResponse(make({...report.change,reportId:id(),description:'別ID'},ids[1],id(),[report.eventId])));
  const cancel=await store.appendResponse(make({...report.change,cancelled:true,cancelReason:'重複を確認'},ids[1],id(),[report.eventId]));
  assert.ok(!(await store.exportCsv(group,incident)).includes(report.change.reportId));
  const catalog={regions:[{id:'north',name:'北地区'}]};
  assert.equal((await store.reportRegionSummary(group,incident,catalog)).regions[0].count,0);
  const restore=await store.appendResponse(make({...cancel.change,cancelled:false,cancelReason:''},ids[0],id(),[cancel.eventId]));
  assert.equal(restore.change.reportId,report.change.reportId);
  assert.equal((await store.responseEvents(group)).length,3);
  assert.equal((await store.reportRegionSummary(group,incident,catalog)).regions[0].count,1);
  assert.ok((await store.exportCsv(group,incident)).includes(report.change.reportId));
 }finally{store.close();}
});
test('cancelled concurrent head does not hide an unresolved active correction',async()=>{
 const f=await fixture();const {store,ids,report,make,incident,roster}=f;
 try{
  const cancel=await store.appendResponse(make({...report.change,cancelled:true,cancelReason:'誤報'},ids[1],id(),[report.eventId]));
  const other={...make({...report.change,category:'現場確認',description:'実在する'},ids[0],id(),[report.eventId]),sequence:1};await store.mergeResponse(other);
  assert.equal(responseHeads(roster,await store.responseEvents(group)).length,2);
  assert.ok((await store.exportCsv(group,incident)).includes('現場確認'));
  assert.ok(!(await store.exportCsv(group,incident)).includes('通報：道路・未設定'));
  assert.equal(cancel.change.cancelled,true);
 }finally{store.close();}
});
test('legacy report validation does not insert optional defaults and rejects malformed regions',async()=>{
 const f=await fixture();try{
  const raw={...f.report,change:{...f.report.change}};delete raw.change.regionIds;
  const before=JSON.stringify(raw);validateResponse(f.roster,raw);assert.equal(JSON.stringify(raw),before);
  for(const regionIds of[null,['north','north'],['bad space']])assert.throws(()=>validateResponse(f.roster,{...raw,change:{...raw.change,regionIds}}));
 }finally{f.store.close();}
});
test('startWork atomically self-assigns and starts PLANNED, then is idempotent and preserves DONE',async()=>{
 const f=await fixture();const {store,ids,target,make,incident}=f;
 try{
  const planned=await store.appendResponse(make({type:'PROGRESS',target,status:'PLANNED',result:'明朝確認'}));
  const [a,b]=await Promise.all([store.startWork(group,incident,ids[1],target,3),store.startWork(group,incident,ids[1],target,3)]);
  assert.equal(a.length+b.length,2);const progress=(await store.responseHeads(group)).find(e=>e.change.type==='PROGRESS');
  assert.equal(progress.change.status,'IN_PROGRESS');assert.deepEqual(progress.replaces,[planned.eventId]);
  const done=await store.appendResponse(make({type:'PROGRESS',target,status:'DONE',result:'完了'},ids[1],id(),[progress.eventId]));
  assert.deepEqual(await store.startWork(group,incident,ids[1],target,4),[]);
  assert.ok((await store.responseHeads(group)).some(e=>e.eventId===done.eventId));
 }finally{store.close();}
});
test('startWork preserves concurrent progress heads and a failure on second write rolls back first',async()=>{
 const f=await fixture();const {store,ids,target,make,incident}=f;
 try{
  const transaction=store.managedTransaction.bind(store);let writes=0;
  store.managedTransaction=work=>transaction((tables,put)=>work(tables,(name,value)=>{put(name,value);if(++writes===2)throw Error('simulated second-write failure');}));
  await assert.rejects(store.startWork(group,incident,ids[1],target,3),/second-write/);
  store.managedTransaction=transaction;assert.equal((await store.responseEvents(group)).length,1);
  const one=await store.appendResponse(make({type:'PROGRESS',target,status:'PLANNED',result:''}));
  const two={...make({type:'PROGRESS',target,status:'DONE',result:'別端末で完了'},ids[0]),sequence:1};await store.mergeResponse(two);
  const started=await store.startWork(group,incident,ids[1],target,4);assert.equal(started.length,1);assert.equal(started[0].change.type,'ASSIGNMENT');
  assert.deepEqual(new Set((await store.responseHeads(group)).filter(e=>e.change.type==='PROGRESS').map(e=>e.eventId)),new Set([one.eventId,two.eventId]));
 }finally{store.close();}
});
test('English CSV translates only fixed labels, preserving report categories, region names and user text',async()=>{
 const f=await fixture();const {store,ids,target,make,incident}=f;
 try{
  await store.appendResponse(make({type:'PROGRESS',target,status:'PLANNED',result:'未設定・対応中を確認'}));
  const requestId=id();await store.appendResponse(make({type:'MATERIAL',target,requestId,name:'必要・水',unit:'本',needed:3,arranged:1},ids[1],requestId));
  const csv=await store.exportCsv(group,incident,{[`REPORT:${target.id}`]:['未設定']},{language:'en'});
  assert.ok(csv.startsWith('\uFEFF"Region","Target ID"'));assert.ok(csv.includes('"Report: 道路・未設定"'));assert.ok(csv.includes('"Planned"'));
  assert.ok(csv.includes('"未設定"'));assert.ok(csv.includes('未設定・対応中を確認'));assert.ok(csv.includes('必要・水: needed 3本; arranged 1本; shortfall 2本'));
  assert.ok((await store.exportCsv(group,incident)).startsWith('\uFEFF"地域"'));
 }finally{store.close();}
});
