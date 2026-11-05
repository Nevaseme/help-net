import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine} from '../web/sync.mjs';
import {normalizePlace,validatePlace,placeBuildings} from '../web/places.mjs';
import {normalizeAddress,searchAddresses} from '../web/city-addresses.mjs';
test('address normalization and representative town points remain distinct from parcel positions',()=>{
 assert.equal(normalizeAddress('京都市 １２番地３号'),'京都市12-3');
 const rows=[['a','京都市中京区試験町',135,35,'town'],['b','京都市中京区試験町12-3',135.01,35.01,'parcel']];
 assert.equal(searchAddresses(rows,'試験町１２番地３号')[0].id,'b');assert.equal(searchAddresses(rows,'京都市中京区試験町999')[0].precision,'town');assert.equal(searchAddresses(rows,'存在しない町').length,0);
});
test('legacy bytes stay unchanged and address-linked buildings survive offline forward and correction',async t=>{
 const g='address-test',a=crypto.randomUUID(),b=crypto.randomUUID(),c=crypto.randomUUID(),id=crypto.randomUUID();
 const original={groupId:g,eventId:id,buildingId:id,authorId:a,authorName:'架空',sequence:1,createdAt:1,kind:'BUILDING',label:'試験住宅',latitude:35,longitude:135};
 assert.ok(!Object.hasOwn(normalizePlace(original),'locationReference'));
 const reference={address:'京都市中京区試験町12-3',plateauId:'bldg_test',dataset:'plateau-26100-kyoto-shi-2025'};
 assert.throws(()=>validatePlace({...original,locationReference:{...reference,dataset:null}}));
 const dbs=await Promise.all([0,1,2].map(()=>Store.open(crypto.randomUUID(),indexedDB)));t.after(()=>dbs.forEach(s=>s.close()));
 for(const db of dbs)await db.merge({groupId:g,originId:a,revision:1,updatedAt:1,name:'架空',latitude:35,longitude:135,floor:'',room:'',safety:'UNREPORTED'});
 await dbs[0].appendPlace({...original,locationReference:reference});let queue=[],seen=[];
 const engines=dbs.map((db,i)=>new SyncEngine(g,[a,b,c][i],db,(to,m)=>{seen.push(m);queue.push([i,+to,m]);}));
 const pump=async()=>{let n=0;while(queue.length){assert.ok(++n<200);const [from,to,m]=queue.shift();await engines[to].receive(String(from),m);}};
 await engines[0].connected('1');await engines[1].connected('0');await pump();engines[0].disconnected('1');engines[1].disconnected('0');
 await engines[1].connected('2');await engines[2].connected('1');await pump();
 assert.equal(seen.find(m=>m.type==='PLACE_RECORD').version,12);assert.deepEqual((await dbs[2].places(g))[0].locationReference,reference);
 const corrected=await dbs[0].appendPlace({...original,eventId:crypto.randomUUID(),locationReference:{...reference,address:'京都市中京区試験町12-4'}});
 assert.equal(placeBuildings(await dbs[0].places(g))[0].locationReference.address,corrected.locationReference.address);assert.equal((await dbs[0].places(g)).length,2);
});

test('address field order from canonical server storage is not a conflicting building',async t=>{
 const store=await Store.open(crypto.randomUUID(),indexedDB);t.after(()=>store.close());
 const id=crypto.randomUUID(),authorId=crypto.randomUUID();
 const row={groupId:'order-test',eventId:id,buildingId:id,authorId,authorName:'架空',sequence:1,createdAt:1,kind:'BUILDING',label:'住宅',latitude:35,longitude:135,locationReference:{address:'京都市試験町',plateauId:null,dataset:null}};
 await store.mergePlace(row);
 await store.mergePlace({...row,locationReference:{dataset:null,plateauId:null,address:'京都市試験町'}});assert.equal((await store.places('order-test')).length,1);
 await assert.rejects(()=>store.mergePlace({...row,locationReference:{...row.locationReference,address:'別の住所'}}));
});

test('search provenance survives intermediate offline relay unchanged',async t=>{
 const g='address-test',a=crypto.randomUUID(),b=crypto.randomUUID(),c=crypto.randomUUID(),id=crypto.randomUUID();
 const original={groupId:g,eventId:id,buildingId:id,authorId:a,authorName:'架空',sequence:1,createdAt:1,kind:'BUILDING',label:'試験住宅',latitude:35,longitude:135};
 assert.ok(!Object.hasOwn(normalizePlace(original),'locationReference'));
 const reference={address:'京都市中京区試験町12-3',plateauId:'bldg_test',dataset:'plateau-26100-kyoto-shi-2025',searchReference:{input:'讃州寺町203',label:'京都市左京区讃州寺町203',latitude:35.011046,longitude:135.774524,matchLevel:'parcel',precision:'block',source:'ISJ',sourceVersion:'24.0a'}};
 assert.throws(()=>validatePlace({...original,locationReference:{...reference,dataset:null}}));
 const dbs=await Promise.all([0,1,2].map(()=>Store.open(crypto.randomUUID(),indexedDB)));t.after(()=>dbs.forEach(s=>s.close()));
 for(const db of dbs)await db.merge({groupId:g,originId:a,revision:1,updatedAt:1,name:'架空',latitude:35,longitude:135,floor:'',room:'',safety:'UNREPORTED'});
 await dbs[0].appendPlace({...original,locationReference:reference});let queue=[],seen=[];
 const engines=dbs.map((db,i)=>new SyncEngine(g,[a,b,c][i],db,(to,m)=>{seen.push(m);queue.push([i,+to,m]);}));
 const pump=async()=>{let n=0;while(queue.length){assert.ok(++n<200);const [from,to,m]=queue.shift();await engines[to].receive(String(from),m);}};
 await engines[0].connected('1');await engines[1].connected('0');await pump();engines[0].disconnected('1');engines[1].disconnected('0');
 await engines[1].connected('2');await engines[2].connected('1');await pump();
 assert.equal(seen.find(m=>m.type==='PLACE_RECORD').version,16);assert.deepEqual((await dbs[2].places(g))[0].locationReference,reference);
 const corrected=await dbs[0].appendPlace({...original,eventId:crypto.randomUUID(),locationReference:{...reference,address:'京都市中京区試験町12-4'}});
 assert.equal(placeBuildings(await dbs[0].places(g))[0].locationReference.address,corrected.locationReference.address);assert.equal((await dbs[0].places(g)).length,2);
});

