import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';

const id=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const group='trial',incident=id(10);
const roster={version:1,groupId:group,members:[1,2].map(n=>({memberId:id(n),deviceIds:[id(n)],activityName:n===1?'確認班':'連絡班',role:n===1?'ADMIN':'MEMBER'}))};
const event=(n,change,replaces=[])=>({groupId:group,eventId:id(n),actorDeviceId:id(1),sequence:n,operatedAt:n,incidentId:incident,change,replaces});
async function setup(){
 const store=await Store.open(crypto.randomUUID(),indexedDB);
 await store.importRoster(roster,id(1));
 await store.mergeIncident({groupId:group,eventId:incident,incidentId:incident,authorId:id(1),authorName:'確認班',sequence:1,createdAt:1,kind:'START',title:'訓練',area:'地域'});
 for(let n=1;n<=4;n++)await store.merge({groupId:group,originId:id(n),revision:1,updatedAt:1,name:`住民${n}`,latitude:35,longitude:139,floor:'2階',room:'北',safety:'UNREPORTED'});
 for(let n=1;n<=3;n++)await store.mergeIncident({groupId:group,eventId:id(10+n),incidentId:incident,authorId:id(n),authorName:`住民${n}`,sequence:n+1,createdAt:10,kind:'ANSWER',safety:n===2?'SAFE':'SOS'});
 await store.mergeIncident({groupId:group,eventId:id(20),incidentId:incident,authorId:id(1),authorName:'確認班',sequence:5,createdAt:20,kind:'CARE',subjectId:id(3),action:'RESCUED'});
 return store;
}

test('CSV export reads durable records and hides safe and rescued names',async()=>{
 let store=await setup();const name=store.name;store.close();store=await Store.open(name,indexedDB);
 try{
  const csv=await store.exportCsv(group,incident);
  assert.ok(csv.startsWith('\uFEFF"地域","対象ID","種別","氏名"'));
  assert.ok(csv.includes('"住民1","SOS・未対応"'));
  assert.ok(csv.includes('"住民4","応答なし・未対応"'));
  assert.ok(!csv.includes('住民2'));assert.ok(!csv.includes('住民3'));
  assert.equal(csv.trimEnd().split('\r\n').length,5);
  await assert.rejects(store.exportCsv(group,id(999)));
  await assert.rejects(store.exportCsv('missing',incident));
 }finally{store.close();}
});

test('CSV reflects corrections, independent requests, assignments and selected group',async()=>{
 const store=await setup(),target={kind:'REPORT',id:id(30)};
 try{
  const records=[event(30,{type:'REPORT',reportId:id(30),category:'道路閉塞',description:'倒木',latitude:35,longitude:139}),
   event(31,{type:'REPORT',reportId:id(30),category:'道路障害',description:'訂正',latitude:35,longitude:139},[id(30)]),
   ...[1,2].map((n,i)=>event(32+i,{type:'ASSIGNMENT',target,memberId:id(n),active:true})),
   event(34,{type:'PROGRESS',target,status:'DONE',result:'確認,"完了"\n通行可'}),
   event(35,{type:'PRIORITY',target,urgency:'HIGH',reason:'通行不能'}),
   ...[36,37].map(n=>event(n,{type:'MATERIAL',target,requestId:id(n),name:'水',unit:'本',needed:3,arranged:1}))];
  for(const e of records.toReversed())await store.mergeResponse(e);
  await store.merge({groupId:'other',originId:id(1),revision:9,updatedAt:9,name:'別グループ',latitude:35,longitude:139,floor:'',room:'',safety:'SOS'});
  const csv=await store.exportCsv(group,incident,{[`REPORT:${id(30)}`]:['A町内会','A町内会']});
  assert.ok(csv.includes(`"A町内会","${id(30)}","通報：道路障害","","対応済み","高","確認班 / 連絡班"`));
  assert.ok(csv.includes('"確認,""完了""\n通行可"'));
  assert.equal(csv.split('水：必要3本・手配済み1本・不足2本').length-1,2);
  assert.ok(!csv.includes('道路閉塞'));assert.ok(!csv.includes('別グループ'));
 }finally{store.close();}
});

test('CSV escapes spreadsheet formulas and preserves new SOS after rescue',async()=>{
 const store=await setup();
 try{
  await store.merge({groupId:group,originId:id(1),revision:2,updatedAt:30,name:'=1+1',latitude:35,longitude:139,floor:'',room:'',safety:'SOS'});
  await store.mergeIncident({groupId:group,eventId:id(21),incidentId:incident,authorId:id(3),authorName:'住民3',sequence:8,createdAt:30,kind:'ANSWER',safety:'SOS'});
  const csv=await store.exportCsv(group,incident);
  assert.ok(csv.includes('"\'=1+1"'));assert.ok(csv.includes('"住民3","SOS・未対応"'));
 }finally{store.close();}
});
