import test from 'node:test';import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {validHome,placeBuildings} from '../web/places.mjs';
test('profile and home relation commit together and a move replaces the old building',async t=>{
 const s=await Store.open(crypto.randomUUID(),indexedDB);t.after(()=>s.close());
 assert.equal(typeof s.saveProfileWithHome,'function');
 const r={groupId:'home-test',originId:crypto.randomUUID(),name:'試験',latitude:35,longitude:135,floor:'2',room:'東',safety:'UNREPORTED'};
 await s.saveProfileWithHome(r,{address:'京都市試験町1',plateauId:null});
 let rows=await s.places(r.groupId),h=validHome(rows,r.originId),first=h.buildingId;
 assert.equal(placeBuildings(rows).find(b=>b.buildingId===first).locationReference.address,'京都市試験町1');
 await s.saveProfileWithHome({...r,latitude:35.01,room:''},{address:'京都市別町2',plateauId:null});
 rows=await s.places(r.groupId);h=validHome(rows,r.originId);assert.notEqual(h.buildingId,first);assert.equal(h.unitId,null);
 assert.equal((await s.get(r.groupId,r.originId)).latitude,35.01);
 const before=JSON.stringify(await s.places(r.groupId));
 await assert.rejects(()=>s.saveProfileWithHome({...r,latitude:NaN},{address:'失敗',plateauId:null}));
 assert.equal(JSON.stringify(await s.places(r.groupId)),before);assert.equal((await s.get(r.groupId,r.originId)).latitude,35.01);
});
test('a home cannot link a unit belonging to another building',async t=>{
 const s=await Store.open(crypto.randomUUID(),indexedDB);t.after(()=>s.close());const originId=crypto.randomUUID(),groupId='unit-test';
 const r={groupId,originId,name:'試験',latitude:35,longitude:135,floor:'',room:'',safety:'UNREPORTED'};
 await s.saveProfileWithHome(r,{address:'旧住居'});const first=validHome(await s.places(groupId),originId).buildingId;
 const unitId=crypto.randomUUID();const unit=await s.appendPlace({groupId,eventId:unitId,unitId,buildingId:first,authorId:originId,authorName:'試験',createdAt:1,kind:'UNIT',label:'201'});
 await assert.rejects(()=>s.saveProfileWithHome({...r,latitude:35.01},{address:'新住居',unitId:unit.unitId}));
 assert.equal((await s.get(groupId,originId)).latitude,35);
});
