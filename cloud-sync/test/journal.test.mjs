import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Journal,hashSecret} from '../src/journal.mjs';
import {relayKey,signedItem,derSignature} from './relay-helpers.mjs';

const id=()=>crypto.randomUUID();
function storage(db){return {all:(q,...p)=>db.prepare(q).all(...p),run:(q,...p)=>db.prepare(q).run(...p),transaction:fn=>{db.exec('BEGIN IMMEDIATE');try{const r=fn();db.exec('COMMIT');return r;}catch(e){db.exec('ROLLBACK');throw e;}},flush:async()=>{}};}
function fixture(t){const dir=mkdtempSync(join(tmpdir(),'pland-sync-')),file=join(dir,'group.sqlite');let db=new DatabaseSync(file),s=storage(db),j=new Journal(s);t.after(()=>{db.close();rmSync(dir,{recursive:true,force:true});});return {get j(){return j;},get s(){return s;},restart(){db.close();db=new DatabaseSync(file);s=storage(db);j=new Journal(s);}};}
const registrationToken=()=>crypto.randomUUID().replaceAll('-','')+crypto.randomUUID().replaceAll('-','');
test('nearby credential is durable, member-only and different between groups',async t=>{
 const a=fixture(t),b=fixture(t),admin=await create(a.j),peer=await member(a.j,admin),other=await create(b.j);
 const key=await a.j.nearby(admin.token);
 assert.match(key.key,/^[a-f0-9]{64}$/);
 assert.deepEqual(await a.j.nearby(peer.token),key);
 a.restart();assert.deepEqual(await a.j.nearby(admin.token),key);
 assert.notEqual((await b.j.nearby(other.token)).key,key.key);
 await assert.rejects(a.j.nearby(other.token),{code:'unauthorized'});
 assert.equal(JSON.stringify(a.j.snapshot()).includes(key.key),false);
});
async function create(j){return j.create({groupId:id(),deviceId:id(),activityName:'管理者',groupName:'試験班',registrationToken:registrationToken()});}
async function member(j,a){const invite=await j.invite(a.token);return j.join({deviceId:id(),activityName:'参加者',code:invite.code,registrationToken:registrationToken()});}
const resident=(a,revision=1)=>({groupId:a.groupId,originId:a.deviceId,revision,updatedAt:1,name:'本人',floor:'',room:'',latitude:35,longitude:135,safety:'SAFE'});
const push=(j,a,record,collection='residents')=>j.push(a.token,{records:[{collection,record}]});

test('report viewers cannot mutate work or materials through direct or signed offline uploads',async t=>{
 const f=fixture(t),admin=await create(f.j),owner=await member(f.j,admin),other=await member(f.j,admin),key=await relayKey();
 await f.j.registerRelayKey(other.token,key.registration);
 let sequence=0;const make=(who,change,eventId=id(),replaces=[])=>({groupId:admin.groupId,incidentId,actorDeviceId:who.deviceId,eventId,sequence:++sequence,operatedAt:1,change,replaces});
 const incidentId=id(),reportId=id(),report=make(owner,{type:'REPORT',reportId,category:'道路',description:'倒木',latitude:35,longitude:135},reportId),target={kind:'REPORT',id:reportId};
 await push(f.j,owner,report,'responses');
 const requestId=id(),material=make(admin,{type:'MATERIAL',target,requestId,name:'水',unit:'本',needed:5,arranged:1},requestId);await push(f.j,admin,material,'responses');
 const materialId=id(),forged=[
  make(other,{...report.change,cancelled:true,cancelReason:'偽の取消'},id(),[reportId]),
  make(other,{type:'ASSIGNMENT',target,memberId:other.memberId,active:true}),
  make(other,{type:'PROGRESS',target,status:'DONE',result:'偽の完了'}),
  make(other,{type:'MATERIAL',target,requestId:materialId,name:'偽の資材',unit:'本',needed:1,arranged:0},materialId),
  make(other,{...material.change,cancelled:true,cancelReason:'偽の取消'},id(),[requestId])
 ];
 for(const event of forged){
  await assert.rejects(push(f.j,other,event,'responses'),{status:403,code:'response_forbidden'});
  await assert.rejects(f.j.push(admin.token,{records:[await signedItem(key,'responses',event)]}),{status:403,code:'response_forbidden'});
 }
 assert.equal((await f.j.pull(owner.token,0,100)).records.length,2);
 // The report's original reporter may correct a coordinator-created material, including its unit.
 const corrected=make(owner,{...material.change,unit:'箱',needed:1,arranged:0},id(),[requestId]);await push(f.j,owner,corrected,'responses');
 const cancel=make(owner,{...corrected.change,cancelled:true,cancelReason:'不要'},id(),[corrected.eventId]);await push(f.j,owner,cancel,'responses');
 f.restart();const records=(await f.j.pull(owner.token,0,100)).records.map(row=>row.record);
 assert.deepEqual(records[1],material);assert.deepEqual(records.at(-1),cancel);
 const ownerKey=await relayKey();await f.j.registerRelayKey(owner.token,ownerKey.registration);
 const restored=make(owner,{...cancel.change,cancelled:false,cancelReason:''},id(),[cancel.eventId]);
 assert.equal((await f.j.push(other.token,{records:[await signedItem(ownerKey,'responses',restored)]})).acks[0].status,'stored');
 assert.equal((await push(f.j,other,report,'responses')).acks[0].status,'duplicate');
});

test('relay key registration is authenticated, immutable per device, group scoped and durably acknowledged',async t=>{
 const f=fixture(t),other=fixture(t),a=await create(f.j),b=await member(f.j,a),outsider=await create(other.j),key=await relayKey();
 await assert.rejects(f.j.registerRelayKey(outsider.token,key.registration),{code:'unauthorized'});
 await assert.rejects(f.j.relayKeys(outsider.token),{code:'unauthorized'});
 const registered=await f.j.registerRelayKey(a.token,key.registration);
 assert.deepEqual(registered,{deviceId:a.deviceId,...key.registration});
 assert.deepEqual(await f.j.registerRelayKey(a.token,key.registration),registered);
 await assert.rejects(f.j.registerRelayKey(b.token,key.registration),{code:'relay_key_conflict'});
 await assert.rejects(f.j.registerRelayKey(a.token,(await relayKey()).registration),{code:'relay_key_conflict'});
 await assert.rejects(f.j.registerRelayKey(b.token,{...key.registration,keyId:'0'.repeat(64)}),{code:'invalid_relay_key'});
 await assert.rejects(f.j.registerRelayKey(b.token,(await relayKey('P-384')).registration),{code:'invalid_relay_key'});
 await assert.rejects(f.j.registerRelayKey(b.token,{keyId:'0'.repeat(64),publicKeySpki:'%%%='}),{code:'invalid_relay_key'});
 f.restart();assert.deepEqual(await f.j.relayKeys(b.token),{keys:[registered]});
 assert.deepEqual(await other.j.relayKeys(outsider.token),{keys:[]});
 f.s.flush=async()=>{throw Error('disk unavailable');};
 await assert.rejects(f.j.registerRelayKey(a.token,key.registration),/disk unavailable/);
});

test('signed offline author record forwards through another member and retains durable duplicate cursor',async t=>{
 const f=fixture(t),a=await create(f.j),b=await member(f.j,a),key=await relayKey();await f.j.registerRelayKey(a.token,key.registration);
 const item=await signedItem(key,'residents',resident(a)),saved=await f.j.push(b.token,{records:[item]});
 assert.equal(saved.acks[0].status,'stored');f.restart();
 const replay=await f.j.pull(b.token);assert.deepEqual(replay.records[0].record,resident(a));
 const retry=await f.j.push(b.token,{records:[item]});assert.equal(retry.acks[0].status,'duplicate');assert.equal(retry.acks[0].cursor,saved.acks[0].cursor);
 await assert.rejects(push(f.j,b,resident(a,2)),{code:'original_author_required'});
 await push(f.j,a,resident(a,2));
 f.s.flush=async()=>{throw Error('disk unavailable');};
 await assert.rejects(f.j.push(b.token,{records:[await signedItem(key,'residents',resident(a,3))]}),/disk unavailable/);
});

test('relay signatures bind exact raw record, domain, collection, group and registered author',async t=>{
 const {j}=fixture(t),a=await create(j),b=await member(j,a),key=await relayKey(),wrongKey=await relayKey();
 await j.registerRelayKey(a.token,key.registration);await j.registerRelayKey(b.token,wrongKey.registration);
 const item=await signedItem(key,'residents',resident(a));
 const variants=[
  {...item,record:{...item.record,name:'改ざん'}},
  {...item,record:{...item.record,extra:'normalizer would discard this'}},
  {...item,collection:'positions'},
  {...item,relayProof:{...item.relayProof,body:item.relayProof.body.replace('本人','別人')}},
  {...item,relayProof:{...item.relayProof,version:2}},
  {...item,relayProof:{...item.relayProof,keyId:wrongKey.registration.keyId}},
  await signedItem(wrongKey,'residents',resident(a)),
  await signedItem(key,'residents',resident(b)),
 ];
 for(const override of [{domain:'other'},{groupId:id()},{collection:'positions'}])variants.push(await signedItem(key,'residents',resident(a),JSON.stringify({domain:'pland-relay-1',groupId:a.groupId,collection:'residents',record:resident(a),...override})));
 const corrupted=Buffer.from(item.relayProof.signature,'base64');corrupted[corrupted.length-1]^=1;
 variants.push({...item,relayProof:{...item.relayProof,signature:corrupted.toString('base64')}});
 for(const variant of variants)await assert.rejects(j.push(b.token,{records:[variant]}),{code:'invalid_relay_proof'});
 assert.equal((await j.pull(a.token)).records.length,0);
 // A signature is checked even for the caller's own record or a previously stored duplicate.
 await j.push(b.token,{records:[item]});
 await assert.rejects(j.push(a.token,{records:[variants[3]]}),{code:'invalid_relay_proof'});
 // Insignificant JSON ordering is allowed, but the original signed UTF-8 bytes are verified.
 const reordered={...item,record:Object.fromEntries(Object.entries(item.record).reverse())};
 assert.equal((await j.push(b.token,{records:[reordered]})).acks[0].status,'duplicate');
});

test('relay proof verification precedes normalizing managed defaults and all batch writes',async t=>{
 const {j}=fixture(t),a=await create(j),b=await member(j,a),key=await relayKey();await j.registerRelayKey(a.token,key.registration);
 const record={groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,sequence:1,operatedAt:1,incidentId:null,change:{type:'AVAILABILITY',state:'AVAILABLE'}};
 const item=await signedItem(key,'responses',record);
 await assert.rejects(j.push(b.token,{records:[{...item,record:{...record,replaces:[]}}]}),{code:'invalid_relay_proof'});
 const bad={...item,relayProof:{...item.relayProof,signature:'AAAA'}};
 await assert.rejects(j.push(b.token,{records:[await signedItem(key,'residents',resident(a)),bad]}),{code:'invalid_relay_proof'});
 assert.equal((await j.pull(a.token)).records.length,0);
 await j.push(b.token,{records:[item]});
 assert.deepEqual((await j.pull(a.token)).records[0].record.replaces,[]);
 assert.equal((await j.push(b.token,{records:[item]})).acks[0].status,'duplicate');
});

test('relay authorization uses author role and member identity instead of relayer privilege',async t=>{
 const {j}=fixture(t),admin=await create(j),memberA=await member(j,admin),memberB=await member(j,admin),adminKey=await relayKey(),keyA=await relayKey(),keyB=await relayKey();
 for(const [actor,key] of [[admin,adminKey],[memberA,keyA],[memberB,keyB]])await j.registerRelayKey(actor.token,key.registration);
 const training=who=>({groupId:who.groupId,eventId:id(),actorDeviceId:who.deviceId,operatedAt:1,training:true,action:'TRAINING_START',title:'訓練',area:'京都'});
 await assert.rejects(j.push(admin.token,{records:[await signedItem(keyA,'quakes',training(memberA))]}),{code:'admin_required'});
 await j.push(memberA.token,{records:[await signedItem(adminKey,'quakes',training(admin))]});
 const original=report(memberA,10);await j.push(memberB.token,{records:[await signedItem(keyA,'quakes',original)]});
 const cancel={...report(memberA,11),action:'CANCEL',targetId:original.eventId,title:'',area:''};
 await j.push(memberB.token,{records:[await signedItem(keyA,'quakes',cancel)]});
 await assert.rejects(j.push(admin.token,{records:[await signedItem(keyB,'quakes',{...cancel,eventId:id(),actorDeviceId:memberB.deviceId})]}),{code:'invalid_cancel'});
});

test('malformed ASN.1 DER cannot be accepted as an ECDSA relay signature',async t=>{
 const {j}=fixture(t),a=await create(j),b=await member(j,a),key=await relayKey();await j.registerRelayKey(a.token,key.registration);
 const item=await signedItem(key,'residents',resident(a));
 const raw=new Uint8Array(await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},key.pair.privateKey,new TextEncoder().encode(item.relayProof.body))),der=derSignature(raw);
 const values=[Buffer.from([]),Buffer.from([48,6,2,1,0,2,1,0]),Buffer.from([48,6,2,1,128,2,1,1]),Buffer.from([48,7,2,2,0,1,2,1,1]),Buffer.concat([der,Buffer.from([0])]),der.subarray(0,der.length-1),raw,Buffer.from([48,129,6,2,1,1,2,1,1]),Buffer.from([48,6,3,1,1,2,1,1])];
 // These encode the real valid r/s noncanonically: permissive parsers would verify them.
 values.push(Buffer.concat([Buffer.from([48,129,der[1]]),der.subarray(2)]));
 values.push(Buffer.concat([Buffer.from([48,der[1]+1,2,der[3]+1,0]),der.subarray(4)]));
 values.push(Buffer.concat([Buffer.from([48,der[1]+1]),der.subarray(2),Buffer.from([0])]));
 if(der[4]===0)values.push(Buffer.concat([Buffer.from([48,der[1]-1,2,der[3]-1]),der.subarray(5)]));
 for(const value of values)await assert.rejects(j.push(b.token,{records:[{...item,relayProof:{...item.relayProof,signature:Buffer.from(value).toString('base64')}}]}),{code:'invalid_relay_proof'});
 assert.equal((await j.pull(a.token)).records.length,0);
});

test('registered keys do not let nonmembers or another group forward an author record',async t=>{
 const f=fixture(t),other=fixture(t),a=await create(f.j),b=await member(f.j,a),outsider=await create(other.j),key=await relayKey();await f.j.registerRelayKey(a.token,key.registration);
 const item=await signedItem(key,'residents',resident(a));
 await assert.rejects(f.j.push(outsider.token,{records:[item]}),{code:'unauthorized'});
 await assert.rejects(other.j.push(outsider.token,{records:[item]}),{code:'member_required'});
 const roster=f.j.snapshot();roster.roster.members=roster.roster.members.filter(m=>m.memberId!==a.memberId);
 f.s.run('INSERT INTO roster_history(revision,body) VALUES (?,?)',roster.rosterRevision+1,JSON.stringify(roster.roster));
 await assert.rejects(f.j.push(b.token,{records:[item]}),{code:'member_required'});
 await assert.rejects(f.j.push(a.token,{records:[item]}),{code:'member_required'});
});

test('restart preserves authentication, journal, replay and duplicate cursor',async t=>{const f=fixture(t),a=await create(f.j),r=resident(a);const saved=await push(f.j,a,r);f.restart();const replay=await f.j.pull(a.token,0,100);assert.equal(replay.records.length,1);assert.deepEqual(replay.records[0].record,r);const duplicate=await push(f.j,a,r);assert.equal(duplicate.acks[0].status,'duplicate');assert.equal(duplicate.acks[0].cursor,saved.acks[0].cursor);assert.equal((await f.j.pull(a.token,replay.nextCursor,100)).records.length,0);});

test('planned work and report cancellation/restoration retain history across cloud restart',async t=>{
 const f=fixture(t),a=await create(f.j),b=await member(f.j,a),reportId=id(),incidentId=id();
 const original={groupId:a.groupId,eventId:reportId,actorDeviceId:a.deviceId,sequence:1,operatedAt:10,incidentId,replaces:[],change:{type:'REPORT',reportId,category:'道路閉塞',description:'架空の倒木',latitude:35,longitude:135,regionIds:['north']}};
 await push(f.j,a,original,'responses');
 const planned={...original,eventId:id(),sequence:2,change:{type:'PROGRESS',target:{kind:'REPORT',id:reportId},status:'PLANNED',result:''}};await push(f.j,a,planned,'responses');
 const canceled={...original,eventId:id(),sequence:3,replaces:[reportId],change:{...original.change,cancelled:true,cancelReason:'重複する通報'}};await push(f.j,a,canceled,'responses');
 await assert.rejects(push(f.j,b,{...canceled,eventId:id(),actorDeviceId:b.deviceId,sequence:1,replaces:[canceled.eventId],change:{...original.change,cancelled:false,cancelReason:''}},'responses'),{code:'response_forbidden',status:403});
 f.restart();const restored={...original,eventId:id(),sequence:4,replaces:[canceled.eventId],change:{...original.change,cancelled:false,cancelReason:''}};await push(f.j,a,restored,'responses');
 const page=await f.j.pull(b.token,0,100);assert.deepEqual(page.records.map(r=>r.record),[original,planned,canceled,restored]);
 assert.equal((await push(f.j,a,canceled,'responses')).acks[0].status,'duplicate');
});
test('membership has no A/B/C limit and snapshots are immutable across joins',async t=>{const {j}=fixture(t),a=await create(j);for(let i=0;i<5;i++)await member(j,a);const r=await j.roster(a.token);assert.equal(r.roster.members.length,6);assert.equal(r.rosterHistory.length,6);assert.equal(r.rosterHistory[0].roster.members.length,1);assert.deepEqual(r.rosterHistory[0].roster.members[0],r.roster.members[0]);});
test('group and actor boundaries reject unknown foreign uploads, allow exact existing retransmission',async t=>{const {j}=fixture(t),a=await create(j),b=await member(j,a);await assert.rejects(push(j,b,resident(a)),{code:'original_author_required'});await push(j,a,resident(a));assert.equal((await push(j,b,resident(a))).acks[0].status,'duplicate');await assert.rejects(push(j,a,{...resident(a),groupId:id()}),{code:'wrong_group'});await assert.rejects(j.pull('unknown',0,10),{code:'unauthorized'});});
test('same revision conflict rejects whole batch; no cursor for rollback',async t=>{const {j}=fixture(t),a=await create(j);await push(j,a,resident(a));await assert.rejects(j.push(a.token,{records:[{collection:'residents',record:resident(a,2)},{collection:'residents',record:{...resident(a),name:'conflict'}}]}),{code:'record_conflict'});assert.equal((await j.pull(a.token,0,100)).records.length,1);});
test('older unseen revisions are retained in cursor history',async t=>{const {j}=fixture(t),a=await create(j);await push(j,a,resident(a,3));await push(j,a,resident(a,1));assert.deepEqual((await j.pull(a.token,0,100)).records.map(x=>x.record.revision),[3,1]);});
test('only admin invites; duplicate identity never grants replacement credential',async t=>{const {j}=fixture(t),a=await create(j),b=await member(j,a);await assert.rejects(j.invite(b.token),{code:'admin_required'});const inv=await j.invite(a.token);await assert.rejects(j.join({deviceId:a.deviceId,activityName:'fake',code:inv.code,registrationToken:registrationToken()}),{code:'device_exists'});await assert.rejects(j.join({deviceId:id(),activityName:'fake',code:'bad',registrationToken:registrationToken()}),{code:'invalid_invitation'});});
test('stored secrets are hashes, not bearer plaintext',async t=>{const f=fixture(t),a=await create(f.j);const row=f.s.all('SELECT * FROM devices')[0];assert.equal(row.token_hash,await hashSecret(a.token));assert.ok(!JSON.stringify(row).includes(a.token));});
test('storage flush failure returns no success acknowledgement',async t=>{const f=fixture(t),a=await create(f.j);f.s.flush=async()=>{throw Error('disk unavailable');};await assert.rejects(push(f.j,a,resident(a)),/disk unavailable/);});
test('nonadmin END and foreign author fields are rejected',async t=>{const {j}=fixture(t),a=await create(j),b=await member(j,a);const end={groupId:a.groupId,eventId:id(),actorDeviceId:b.deviceId,operatedAt:3,training:false,action:'END',targetId:id()};await assert.rejects(push(j,b,end,'quakes'),{code:'admin_required'});});
test('cursor pagination neither skips nor duplicates committed updates',async t=>{const {j}=fixture(t),a=await create(j);for(let i=1;i<=5;i++)await push(j,a,resident(a,i));let after=0,count=0;do{const page=await j.pull(a.token,after,2);count+=page.records.length;after=page.nextCursor;if(!page.hasMore)break;}while(true);assert.equal(count,5);await assert.rejects(j.pull(a.token,-1,2),{code:'invalid_request'});});
test('lost create/join responses can recover using persisted registration proof after restart',async t=>{const f=fixture(t),request={groupId:id(),deviceId:id(),activityName:'Admin',groupName:'Group',registrationToken:registrationToken()},first=await f.j.create(request);f.restart();assert.equal((await f.j.create(request)).memberId,first.memberId);const invite=await f.j.invite(first.token),joinRequest={deviceId:id(),activityName:'Member',code:invite.code,registrationToken:registrationToken()},joined=await f.j.join(joinRequest);f.s.run('UPDATE invitations SET expires_at=0');f.restart();assert.equal((await f.j.join(joinRequest)).memberId,joined.memberId);await assert.rejects(f.j.join({...joinRequest,deviceId:id()}),{code:'invalid_invitation'});assert.equal((await f.j.roster(first.token)).roster.members.length,2);});
const report=(a,time)=>({groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:time,training:false,action:'REPORT',title:'地震',area:'京都'});
test('administrator-only explicit training persists, deduplicates and replays with answers after restart',async t=>{
 const f=fixture(t),a=await create(f.j),e={...report(a,10),action:'TRAINING_START',training:true};
 await push(f.j,a,resident(a));const saved=await push(f.j,a,e,'quakes');assert.equal((await push(f.j,a,e,'quakes')).acks[0].cursor,saved.acks[0].cursor);
 const answer={groupId:a.groupId,eventId:id(),incidentId:e.eventId,authorId:a.deviceId,authorName:'本人',sequence:1,createdAt:11,kind:'ANSWER',training:true,safety:'SAFE'};
 await assert.rejects(push(f.j,a,answer,'incidents'),{code:'sequence_conflict'});await push(f.j,a,{...answer,sequence:2},'incidents');
 await push(f.j,a,{groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:20,training:true,action:'END',targetId:e.eventId},'quakes');f.restart();
 const b=await member(f.j,a),page=await f.j.pull(b.token,0,100);assert.equal(page.records.length,4);assert.equal(page.records.find(x=>x.record.eventId===answer.eventId).record.safety,'SAFE');
 await assert.rejects(push(f.j,b,{...e,eventId:id(),actorDeviceId:b.deviceId},'quakes'),{code:'admin_required'});
 await assert.rejects(push(f.j,b,{...e,eventId:id()},'quakes'),{code:'original_author_required'});
 await assert.rejects(push(f.j,a,{...e,eventId:id(),training:false},'quakes'),{code:'invalid_record'});
});
test('single ordinary report cannot form and training START arriving after another START or END is retained',async t=>{
 const {j}=fixture(t),a=await create(j),r=report(a,1);await push(j,a,r,'quakes');
 await assert.rejects(push(j,a,{groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:2,training:false,action:'FORM',targetId:r.eventId,reportIds:[r.eventId],rosterRevision:1},'quakes'),{code:'missing_formation_evidence'});
 const first={...report(a,10),action:'TRAINING_START',training:true},late={...report(a,11),action:'TRAINING_START',training:true};await push(j,a,first,'quakes');await push(j,a,{groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:20,training:true,action:'END',targetId:first.eventId},'quakes');await push(j,a,late,'quakes');
 assert.equal((await j.pull(a.token,0,100)).records.filter(x=>x.record.action==='TRAINING_START').length,2);
});
async function addEew(j,a,sourceId='20261030120000'){
 const incidentId=`ee000001-0020-4000-8000-${sourceId.slice(-12)}`;
 await push(j,a,{groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:1000,training:false,action:'EEW',targetId:incidentId,eew:{source:'wolfx',eventId:sourceId,issuedAt:1000,receivedAt:1000}},'quakes');return incidentId;
}
test('delayed historical FORM remains valid after membership expands and concurrent ENDs are retained',async t=>{const {j}=fixture(t),a=await create(j),b=await member(j,a),r1=report(a,10),r2=report(b,11),form={groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:12,training:false,action:'FORM',targetId:r1.eventId,reportIds:[r1.eventId,r2.eventId],rosterRevision:2};await assert.rejects(push(j,a,form,'quakes'),{code:'missing_formation_evidence'});await push(j,a,r1,'quakes');await push(j,b,r2,'quakes');for(let i=0;i<5;i++)await member(j,a);assert.equal((await push(j,a,form,'quakes')).acks[0].rosterRevision,2);const end={groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:20,training:false,action:'END',targetId:r1.eventId};await push(j,a,end,'quakes');await push(j,a,{...end,eventId:id(),operatedAt:21},'quakes');assert.equal((await j.pull(a.token,0,100)).records.filter(e=>e.record.action==='END').length,2);});
test('FORM cannot silently adopt a new roster or include missing evidence',async t=>{const {j}=fixture(t),a=await create(j),b=await member(j,a),r1=report(a,10),r2=report(b,11);await push(j,a,r1,'quakes');await push(j,b,r2,'quakes');const form={groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:12,training:false,action:'FORM',targetId:r1.eventId,reportIds:[r1.eventId,r2.eventId]};await assert.rejects(push(j,a,form,'quakes'),{code:'roster_revision_required'});await assert.rejects(j.push(a.token,{records:[{collection:'quakes',record:{...form,rosterRevision:2},rosterRevision:1}]}),{code:'roster_revision_mismatch'});});
test('EEW-derived incident accepts own ANSWER without synthetic START upload',async t=>{const {j}=fixture(t),a=await create(j),incidentId='ee000001-0020-4000-8000-261030120000',eew={groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:1000,training:false,action:'EEW',targetId:incidentId,eew:{source:'wolfx',eventId:'20261030120000',issuedAt:1000,receivedAt:1000}};await push(j,a,eew,'quakes');await push(j,a,{groupId:a.groupId,eventId:id(),incidentId,authorId:a.deviceId,authorName:'本人',sequence:1,createdAt:1001,kind:'ANSWER',training:false,safety:'SAFE'},'incidents');assert.equal((await j.pull(a.token,0,10)).records.length,2);});
test('concurrent offline response corrections retain both branches; member cannot set priority',async t=>{const {j}=fixture(t),a=await create(j),b=await member(j,a),rootId=id(),incidentId=id(),root={groupId:a.groupId,eventId:rootId,actorDeviceId:b.deviceId,sequence:1,operatedAt:1,incidentId,replaces:[],change:{type:'REPORT',reportId:rootId,category:'fire',description:'現場報告',latitude:35,longitude:135}};await push(j,b,root,'responses');await push(j,b,{...root,eventId:id(),sequence:2,operatedAt:2,replaces:[rootId],change:{...root.change,description:'訂正1'}},'responses');await push(j,b,{...root,eventId:id(),sequence:3,operatedAt:3,replaces:[rootId],change:{...root.change,description:'訂正2'}},'responses');const priority={...root,eventId:id(),sequence:4,change:{type:'PRIORITY',target:{kind:'REPORT',id:rootId},urgency:'HIGH',reason:'危険'}};await assert.rejects(push(j,b,priority,'responses'),{code:'response_forbidden'});assert.equal((await j.pull(a.token,0,100)).records.length,3);});
test('mutable memo identity cannot be reassigned; immutable event ID conflicts',async t=>{const {j}=fixture(t),a=await create(j),memo={groupId:a.groupId,incidentId:id(),authorId:a.deviceId,answerId:id(),revision:1,updatedAt:1,text:'待機'};await push(j,a,memo,'memos');await assert.rejects(push(j,a,{...memo,revision:2,incidentId:id()},'memos'),{code:'record_conflict'});const event={groupId:a.groupId,eventId:id(),subjectId:id(),authorId:a.deviceId,authorName:'本人',createdAt:1,note:'',action:'CHECKING',supersedes:null};await push(j,a,event,'events');await assert.rejects(push(j,a,{...event,note:'changed'},'events'),{code:'record_conflict'});});
for(const collection of ['responses','places','incidents'])test(`${collection}: sequence uniqueness matches client merge scope and failed batch leaves no ACK`,async t=>{
 const {j}=fixture(t),a=await create(j),b=await member(j,a),incidentId=collection==='incidents'?await addEew(j,a):id();
 const make=(who,eventId=id(),sequence=1)=>collection==='responses'?{groupId:who.groupId,eventId,actorDeviceId:who.deviceId,sequence,operatedAt:1,incidentId:null,change:{type:'AVAILABILITY',state:'AVAILABLE'}}:collection==='places'?{groupId:who.groupId,eventId,authorId:who.deviceId,authorName:'本人',sequence,createdAt:1,kind:'BUILDING',buildingId:eventId,label:'建物',latitude:35,longitude:135}:{groupId:who.groupId,eventId,incidentId,authorId:who.deviceId,authorName:'本人',sequence,createdAt:1,kind:'ANSWER',training:false,safety:'SAFE'};
 const first=make(a);await push(j,a,first,collection);assert.equal((await push(j,a,first,collection)).acks[0].status,'duplicate');
 await assert.rejects(j.push(a.token,{records:[{collection,record:make(a,id(),2)},{collection,record:make(a)}]}),{code:'sequence_conflict'});
 assert.equal((await j.pull(a.token,0,100)).records.length,collection==='incidents'?2:1);
 await push(j,b,make(b),collection);
 if(collection==='incidents')await push(j,a,{...make(a),incidentId:await addEew(j,a,'20261030120001')},collection);
 if(collection==='responses')await assert.rejects(push(j,a,{...make(a),incidentId:id(),change:{type:'REPORT',reportId:id(),category:'other',description:'報告',latitude:35,longitude:135}},collection),{code:'sequence_conflict'});
});
test('late EEW merges incident aliases; correction authorizes canonical IDs but preserves raw history',async t=>{
 const {j}=fixture(t),a=await create(j),firstId='ee000001-0020-4000-8000-261030120000',lateId='ee000001-0020-4000-8000-261030130000';
 const eew=(time,targetId,sourceId)=>({groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:time,training:false,action:'EEW',targetId,eew:{source:'wolfx',eventId:sourceId,issuedAt:time,receivedAt:time}});
 await push(j,a,eew(200,lateId,'20261030130000'),'quakes');
 const reportId=id(),root={groupId:a.groupId,eventId:reportId,actorDeviceId:a.deviceId,sequence:1,operatedAt:201,incidentId:lateId,replaces:[],change:{type:'REPORT',reportId,category:'fire',description:'最初の報告',latitude:35,longitude:135}};
 await push(j,a,root,'responses');await push(j,a,eew(100,firstId,'20261030120000'),'quakes');
 const corrected={...root,eventId:id(),sequence:2,operatedAt:202,incidentId:firstId,replaces:[reportId],change:{...root.change,description:'統合後の訂正'}};
 await push(j,a,corrected,'responses');const rows=(await j.pull(a.token,0,100)).records.filter(r=>r.collection==='responses');assert.equal(rows[0].record.incidentId,lateId);assert.equal(rows[1].record.incidentId,firstId);assert.deepEqual(rows[1].record.replaces,[reportId]);
});
test('ANSWER before formation is retryable; derived START sequence cannot poison replay',async t=>{
 const {j}=fixture(t),a=await create(j),b=await member(j,a),r1=report(a,10),r2=report(b,11),answer={groupId:a.groupId,eventId:id(),incidentId:r1.eventId,authorId:a.deviceId,authorName:'本人',sequence:1,createdAt:13,kind:'ANSWER',training:false,safety:'SAFE'};
 await assert.rejects(push(j,a,answer,'incidents'),{code:'missing_incident'});
 await push(j,a,r1,'quakes');await push(j,b,r2,'quakes');
 await assert.rejects(push(j,a,answer,'incidents'),{code:'missing_incident'});
 await push(j,a,{groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:12,training:false,action:'FORM',targetId:r1.eventId,reportIds:[r1.eventId,r2.eventId],rosterRevision:2},'quakes');
 await assert.rejects(push(j,a,answer,'incidents'),{code:'sequence_conflict'});
 await push(j,a,{...answer,sequence:2},'incidents');
 await push(j,b,{...answer,eventId:id(),authorId:b.deviceId},'incidents');
 const page=await j.pull(a.token,0,100);assert.equal(page.records.filter(r=>r.collection==='incidents').length,2);assert.equal(page.records.find(r=>r.record.eventId===answer.eventId).record.sequence,2);
});
test('CARE requires incident proof and cannot use the synthetic START sequence',async t=>{
 const {j}=fixture(t),a=await create(j),b=await member(j,a),r1=report(a,10),r2=report(b,11),care={groupId:a.groupId,eventId:id(),incidentId:r1.eventId,authorId:a.deviceId,authorName:'本人',sequence:1,createdAt:13,kind:'CARE',training:false,subjectId:b.deviceId,action:'CHECKING',note:'確認中'};
 await assert.rejects(push(j,a,care,'incidents'),{code:'missing_incident'});
 await push(j,a,r1,'quakes');await push(j,b,r2,'quakes');await push(j,a,{groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:12,training:false,action:'FORM',targetId:r1.eventId,reportIds:[r1.eventId,r2.eventId],rosterRevision:2},'quakes');
 await assert.rejects(push(j,a,care,'incidents'),{code:'sequence_conflict'});
 await push(j,a,{groupId:a.groupId,eventId:id(),actorDeviceId:a.deviceId,operatedAt:20,training:false,action:'END',targetId:r1.eventId},'quakes');
 await push(j,a,{...care,sequence:2},'incidents');
});

test('only administrators can grant injury and urgency permission; revocation blocks new writes without erasing history',async t=>{
 const f=fixture(t),admin=await create(f.j),peer=await member(f.j,admin),other=await member(f.j,admin);
 const incidentId=id(),person=id();let sequence=0;
 const make=(who,change,eventId=id(),replaces=[])=>({groupId:admin.groupId,incidentId,actorDeviceId:who.deviceId,eventId,sequence:++sequence,operatedAt:Date.now(),change,replaces});
 const injury=()=>make(peer,{type:'INJURY',target:{kind:'PERSON',id:person},state:'INJURED'});
 await assert.rejects(push(f.j,peer,injury(),'responses'),{status:403,code:'response_forbidden'});
 await assert.rejects(f.j.setMemberPermission(peer.token,{memberId:peer.memberId,canTriage:true}),{status:403,code:'admin_required'});
 const originalRoster=f.j.snapshot();
 const granted=await f.j.setMemberPermission(admin.token,{memberId:peer.memberId,canTriage:true});
 assert.equal(granted.roster.members.find(m=>m.memberId===peer.memberId).canTriage,true);
 assert.deepEqual(granted.rosterHistory[originalRoster.rosterRevision-1].roster,originalRoster.roster);
 const saved=injury();await push(f.j,peer,saved,'responses');
 const reportId=id();await push(f.j,other,make(other,{type:'REPORT',reportId,category:'Road',description:'Obstruction',latitude:35,longitude:135},reportId),'responses');
 const priority=make(peer,{type:'PRIORITY',target:{kind:'REPORT',id:reportId},urgency:'HIGH',reason:'People need help'});await push(f.j,peer,priority,'responses');
 await assert.rejects(push(f.j,peer,make(peer,{type:'PROGRESS',target:{kind:'REPORT',id:reportId},status:'DONE',result:'Done'}),'responses'),{status:403,code:'response_forbidden'});
 const revoked=await f.j.setMemberPermission(admin.token,{memberId:peer.memberId,canTriage:false});
 assert.equal(revoked.roster.members.find(m=>m.memberId===peer.memberId).canTriage,false);
 await assert.rejects(push(f.j,peer,injury(),'responses'),{status:403,code:'response_forbidden'});
 await assert.rejects(push(f.j,peer,make(peer,{...priority.change,urgency:'LOW'},id(),[priority.eventId]),'responses'),{status:403,code:'response_forbidden'});
 const {responseHeads}=await import('../../lab/web/managed-records.mjs');
 const heads=responseHeads(f.j.internalRoster(),f.j.history('responses'));assert.ok(heads.some(e=>e.eventId===saved.eventId));assert.ok(heads.some(e=>e.eventId===priority.eventId));
 f.restart();assert.equal(f.j.snapshot().roster.members.find(m=>m.memberId===peer.memberId).canTriage,false);
});
