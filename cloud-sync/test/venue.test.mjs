import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {Journal} from '../src/journal.mjs';
import {handle} from '../src/http.mjs';
import venueBuildings from '../src/venue-buildings.json' with {type:'json'};
import {blockedRoadSamplePoint} from '../src/venue-scenario.mjs';

const id=()=>crypto.randomUUID(),token=()=>id().replaceAll('-','')+id().replaceAll('-','');
const password='fictional-demo-password';

async function legacyRoadFixture(t){
 const f=fixture(t),admin=await setup(f),g=admin.groupId;
 await f.call(`/groups/${g}/venue`,{requestId:id(),password,areaId:'kyoto-gosho-south'},admin.token);
 const journal=f.get(g),config=journal.venueConfig();
 const road=journal.history('responses').find(e=>e.change.type==='REPORT'&&e.change.category==='ROAD_BLOCKED');
 assert.equal(road.change.latitude,blockedRoadSamplePoint.latitude);
 assert.equal(road.change.longitude,blockedRoadSamplePoint.longitude);
 // Reconstruct the exact old saved seed without changing production migration rules.
 road.change.latitude=config.scenario.people[0].home.latitude;road.change.longitude=config.scenario.people[0].home.longitude;
 journal.s.run('UPDATE journal SET body=? WHERE collection=? AND record_key=?',JSON.stringify(road),'responses',road.eventId);
 return {f,admin,journal,road};
}
test('unchanged saved blocked-road seed receives one historical coordinate correction',async t=>{
 const {admin,journal,road}=await legacyRoadFixture(t),before=journal.history('responses');
 await journal.pull(admin.token);
 const after=journal.history('responses');assert.deepEqual(after.slice(0,-1),before);
 const correction=after.at(-1);assert.deepEqual(correction.replaces,[road.eventId]);
 assert.deepEqual(correction.change,{...road.change,...blockedRoadSamplePoint});
 assert.equal(correction.actorDeviceId,admin.deviceId);assert.ok(correction.sequence>road.sequence);
 await journal.pull(admin.token);assert.deepEqual(journal.history('responses'),after);
});
test('member sync does not repair seeds; edited and cancelled reports remain unchanged',async t=>{
 for(const edit of ['member','report','material','cancel']){
  const {f,admin,journal,road}=await legacyRoadFixture(t);
  if(edit==='member'){
   const member=(await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'Member',deviceId:id(),registrationToken:token()})).body;
   const before=journal.history('responses');await journal.pull(member.token);assert.deepEqual(journal.history('responses'),before);continue;
  }
  const actor=await journal.identity(admin.token),events=journal.history('responses');
  const old=edit==='material'?events.find(e=>e.change.type==='MATERIAL'&&e.change.target.id===road.change.reportId):road;
  const change=edit==='material'?{...old.change,arranged:2}:edit==='cancel'?{...road.change,cancelled:true,cancelReason:'No longer blocked'}:{...road.change,description:'User edited report'};
  const record={...old,eventId:id(),sequence:1+Math.max(...events.map(e=>e.sequence)),operatedAt:Date.now(),change,replaces:[old.eventId]};
  journal.append(actor,{collection:'responses',record});
  const before=journal.history('responses');await journal.pull(admin.token);assert.deepEqual(journal.history('responses'),before);
 }
});
test('a user-created blocked-road report is preserved while only the old seed is corrected',async t=>{
 const {admin,journal,road}=await legacyRoadFixture(t),actor=await journal.identity(admin.token),reportId=id();
 const user={...road,eventId:reportId,sequence:1+Math.max(...journal.history('responses').map(e=>e.sequence)),operatedAt:Date.now(),change:{...road.change,reportId,description:'Report created by a participant'},replaces:[]};
 journal.append(actor,{collection:'responses',record:user});
 const saved=journal.history('responses').find(e=>e.eventId===reportId);
 await journal.pull(admin.token);
 assert.deepEqual(journal.history('responses').filter(e=>e.change.reportId===reportId),[saved]);
 assert.equal(journal.history('responses').filter(e=>e.change.reportId===road.change.reportId).length,2);
});
for(const chosen of ['12345678','1','11111111','あ',' Password '])test(`chosen demo password is accepted without complexity rules: ${JSON.stringify(chosen)}`,async t=>{
 const f=fixture(t),admin=await setup(f);
 const started=await f.call(`/groups/${admin.groupId}/venue`,{requestId:id(),password:chosen,areaId:'kyoto-gosho-south'},admin.token);
 assert.equal(started.status,200,JSON.stringify(started.body));
 const join=password=>f.call('/venue/join',{groupName:'会場テスト',password,activityName:'確認',deviceId:id(),registrationToken:token()});
 assert.equal((await join(chosen+'wrong')).status,403);
 assert.equal((await join(chosen)).status,200);
});
test('blank, non-string and oversized passwords remain invalid',async t=>{
 const f=fixture(t),admin=await setup(f);
 for(const value of ['', '   ',null,12345678,'x'.repeat(129)]){
  const r=await f.call(`/groups/${admin.groupId}/venue`,{requestId:id(),password:value,areaId:'kyoto-gosho-south'},admin.token);
  assert.equal(r.status,400);assert.equal(r.body.error.code,'invalid_venue_password');
 }
});
test('venue creates persistent fictional scenario without consuming audience identities',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId,requestId=id();
 const input={requestId,password,areaId:'kyoto-gosho-south'};
 const start=await f.call(`/groups/${g}/venue`,input,admin.token);
 const scenario=start.body.venue.scenario;assert.equal(scenario?.people?.length,66);
 assert.equal(scenario.people.filter(p=>p.safety==='SAFE').length,40);assert.equal(scenario.people.filter(p=>p.safety==='UNREPORTED').length,13);assert.equal(scenario.people.filter(p=>p.initialRescue).length,6);assert.equal(new Set(scenario.people.map(p=>p.home.plateauId)).size,12);assert.equal(new Set(scenario.people.map(p=>p.home.placementId)).size,64);
 assert.equal(start.body.roster.members.length,1);assert.equal(start.body.venue.placements.length,1);
 assert.deepEqual(scenario.communityLongitudeCuts,[135.760,135.7644]);
 for(const c of scenario.communities){const residents=scenario.people.filter(p=>p.communityId===c.id);assert.equal(new Set(residents.map(p=>p.home.buildingId)).size,4);assert.ok(residents.some(p=>p.safety==='SAFE'));assert.ok(residents.some(p=>p.safety==='UNREPORTED'));assert.ok(residents.some(p=>p.safety==='SOS'&&!p.initialRescue));}
 assert.ok(scenario.people.every(p=>p.home.plateauId&&p.home.buildingId&&p.home.address&&p.communityId));
 assert.deepEqual(new Set(scenario.people.map(p=>p.safety)),new Set(['SAFE','SOS','UNREPORTED']));
 assert.ok(scenario.people.every(p=>!/[\u3040-\u30ff\u3400-\u9fff]/.test(p.note+p.name+p.home.room)));
 assert.equal(f.get(g).history('responses').filter(r=>r.change.type==='REPORT').length,3);
 assert.equal(f.get(g).history('incidents').filter(r=>r.kind==='CARE'&&r.action==='RESCUED').length,6);
 const replay=await f.call(`/groups/${g}/venue`,input,admin.token);assert.deepEqual(replay.body.venue.scenario,scenario);
 assert.equal(f.get(g).history('responses').filter(r=>r.change.type==='REPORT').length,3);
});
test('Kyoto participants receive reviewed building addresses with public provenance',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId;
 const start=await f.call(`/groups/${g}/venue`,{requestId:id(),password,areaId:'kyoto-gosho-south'},admin.token);
 const p=start.body.venue.placements[0];
 assert.ok(p.home?.plateauId?.startsWith('bldg_'));
 assert.match(p.home.address,/京都/);assert.notEqual(p.latitude,p.home.latitude);
 const {demoCommunityAt}=await import('../src/venue-layout.mjs');assert.equal(demoCommunityAt(p.longitude),demoCommunityAt(p.home.longitude));
 assert.equal(p.home.addressPrecision,'verified-building-address');
 assert.equal(p.home.address.includes('付近'),false);assert.match(p.home.address,/\d/);
 assert.ok(p.home.addressEvidence.sources.every(s=>s.url.startsWith('https://')));
 assert.equal(p.home.addressEvidence.cadastralBoundaryVerified,false);
 assert.equal(f.get(g).history('residents').length,0,'real homes remain untouched');
});

test('sixty-four simulated placements share twelve reviewed real buildings without invented room numbers',()=>{
 assert.equal(venueBuildings.homes.length,64);
 assert.equal(new Set(venueBuildings.homes.map(h=>h.plateauId)).size,12);
 assert.equal(new Set(venueBuildings.homes.map(h=>h.latitude+':'+h.longitude)).size,64);
 for(const home of venueBuildings.homes){
  assert.equal(home.floor,'');assert.equal(home.room,'');
  assert.ok(home.addressEvidence.osmCoverage>=0.75&&home.addressEvidence.plateauCoverage>=0.75);
 }
});

test('home edits update the building but never move the assigned phone/SOS location',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId,sessionId=id();
 const start=await f.call(`/groups/${g}/venue`,{requestId:sessionId,password,areaId:'kyoto-gosho-south'},admin.token);
 const before=start.body.venue.placements[0],home={...before.home,buildingId:id(),address:'Changed demo home',latitude:35.06,longitude:135.8,plateauId:null,floor:'',room:''};
 const record={groupId:g,eventId:id(),authorId:admin.deviceId,sequence:1,createdAt:Date.now(),kind:'DEMO_HOME',sessionId,home};
 const result=await f.call(`/groups/${g}/records`,{records:[{collection:'deviceRecords',record}]},admin.token);assert.equal(result.status,200,JSON.stringify(result.body));
 const refreshed=(await f.call(`/groups/${g}/roster`,undefined,admin.token)).body.venue;
 assert.deepEqual(refreshed.placements[0].home,home);
 assert.equal(refreshed.placements[0].latitude,before.latitude);assert.equal(refreshed.placements[0].longitude,before.longitude);
 const {venueWithHomes,venuePositions,venuePlaces}=await import('../../lab/web/venue-demo.mjs');
 const offline=venueWithHomes(start.body.venue,[record]);
 assert.equal(venuePositions(offline).find(p=>p.originId===admin.deviceId).latitude,before.latitude);
 assert.equal(venuePlaces(offline).find(p=>p.kind==='BUILDING'&&p.buildingId===home.buildingId).latitude,home.latitude);
 assert.equal(f.get(g).history('residents').length,0,'real home remains unchanged');
 const journal=f.get(g),old=journal.venueConfig();old.scenario.people.forEach(p=>delete p.observations);
 journal.s.run('UPDATE metadata SET value=? WHERE key=?',JSON.stringify(old),'venue');
 assert.ok(journal.venueSnapshot().scenario.people[6].observations.enclosed);
 assert.equal(journal.venueConfig().scenario.people[6].observations,undefined,'no rewrite of stored legacy scenario');
});

test('administrator selects actual positions; join creates no invented coordinates or home records',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId;
 const start=await f.call(`/groups/${g}/venue`,{requestId:id(),password,positionMode:'ACTUAL',areaId:'kyoto-gosho-south'},admin.token);assert.equal(start.status,200);
 assert.equal(start.body.venue.positionMode,'ACTUAL');assert.equal(start.body.venue.placements[0].latitude,null);
 const member=(await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'六本木テスト',deviceId:id(),registrationToken:token()})).body;
 assert.equal(member.venue.placements.every(p=>p.source==='DEVICE_LOCATION'&&p.latitude===null&&p.longitude===null),true);
 const {saveVenue,venuePositions}=await import('../../lab/web/venue-demo.mjs');await saveVenue({read:async()=>null,update:async(_table,_key,fn)=>fn()},member);
 const point={groupId:g,originId:member.deviceId,revision:1,latitude:35.66,longitude:139.73,acquiredAt:Date.now(),accuracyMeters:20};
 const actual=rows=>rows.filter(p=>p.originId===member.deviceId);assert.equal(actual(venuePositions(member.venue,[])).length,0);assert.equal(actual(venuePositions(member.venue,[{...point,acquiredAt:1}])).length,0);assert.deepEqual(actual(venuePositions(member.venue,[point])),[point]);assert.equal(venuePositions(member.venue,[]).length,64);
 assert.equal((await f.call(`/groups/${g}/records`,{records:[{collection:'positions',record:point}]},member.token)).status,200);
 assert.equal(f.get(g).history('residents').length,0);assert.equal(f.get(g).history('positions').length,1);
});

test('recovery retains one member and placement, revokes previous credential, and replays safely',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId;
 await f.call(`/groups/${g}/venue`,{requestId:id(),password,areaId:'kyoto-gosho-south'},admin.token);
 const original=(await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'本人',deviceId:id(),registrationToken:token()})).body;
 const issued=await f.call(`/groups/${g}/access`,{action:'issue'},original.token);assert.equal(issued.status,200);
 assert.equal((await f.call(`/groups/${g}/access`,{action:'issue',memberId:admin.memberId},original.token)).status,403);
 const body={groupName:'会場テスト',code:issued.body.code.toLowerCase().replaceAll('-',' '),deviceId:id(),registrationToken:token()};
 const restored=await f.call('/venue/recover',body);assert.equal(restored.status,200,JSON.stringify(restored.body));
 assert.equal(restored.body.memberId,original.memberId);assert.equal(restored.body.roster.members.length,2);
 assert.equal(restored.body.venue.placements.length,2);assert.equal(restored.body.venue.placements.find(p=>p.memberId===original.memberId).slotId,original.venue.placements.find(p=>p.memberId===original.memberId).slotId);
 assert.equal((await f.call(`/groups/${g}/roster`,undefined,original.token)).status,401);
 assert.equal((await f.call('/venue/recover',body)).status,200);
 assert.equal((await f.call('/venue/recover',{...body,deviceId:id(),registrationToken:token()})).status,403);
 const {cloudRoster}=await import('../../lab/web/managed-records.mjs');
 assert.doesNotThrow(()=>cloudRoster(cloudRoster(null,original,original.deviceId),restored.body,body.deviceId));
 const secondCode=await f.call(`/groups/${g}/access`,{action:'issue',memberId:original.memberId},admin.token);assert.equal(secondCode.status,200);
 const revoked=await f.call(`/groups/${g}/access`,{action:'revoke',memberId:original.memberId},admin.token);assert.equal(revoked.status,200);assert.equal(revoked.body.venue.placements.length,1);
 assert.equal((await f.call(`/groups/${g}/roster`,undefined,body.registrationToken)).status,401);
 assert.equal((await f.call('/venue/recover',{...body,code:secondCode.body.code,deviceId:id()})).status,403);
 assert.equal(revoked.body.roster.members.length,2,'history retained');
});

test('empty passwords, invitation after closure, expired code and idle member session are rejected',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId,sessionId=id();
 assert.equal((await f.call(`/groups/${g}/venue`,{requestId:sessionId,password:'',areaId:'kyoto-gosho-south'},admin.token)).status,400);
 await f.call(`/groups/${g}/venue`,{requestId:sessionId,password,areaId:'kyoto-gosho-south'},admin.token);
 const original=(await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'本人',deviceId:id(),registrationToken:token()})).body;
 const code=(await f.call(`/groups/${g}/access`,{action:'issue'},original.token)).body.code;
 const invitation=(await f.call(`/groups/${g}/invitations`,{},admin.token)).body;
 const journal=f.get(g),now=journal.now();journal.now=()=>now+86400001;
 assert.equal((await f.call('/venue/recover',{groupName:'会場テスト',code,deviceId:id(),registrationToken:token()})).status,403);
 journal.now=()=>now+8*86400000;
 assert.equal((await f.call(`/groups/${g}/roster`,undefined,original.token)).status,401);
 assert.equal((await f.call(`/groups/${g}/roster`,undefined,admin.token)).status,200,'admin cannot be locked out of assisting recovery');
 const newCode=(await f.call(`/groups/${g}/access`,{action:'issue',memberId:original.memberId},admin.token)).body.code;
 assert.equal((await f.call('/venue/recover',{groupName:'会場テスト',code:newCode,deviceId:original.deviceId,registrationToken:token()})).status,200,'same installation can recover expired token');
 journal.now=()=>now;
 await f.call(`/groups/${g}/records`,{records:[{collection:'quakes',record:{groupId:g,eventId:id(),actorDeviceId:admin.deviceId,operatedAt:now,action:'END',training:true,targetId:sessionId}}]},admin.token);
 const rejected=await f.call(`/groups/${g}/join`,{code:invitation.code,deviceId:id(),activityName:'終了後',registrationToken:token()});assert.equal(rejected.status,409);assert.equal(rejected.body.error.code,'venue_closed');
});
function fixture(t){
 const dbs=[],groups=new Map();
 const get=name=>{
  if(!groups.has(name)){
   const db=new DatabaseSync(':memory:');dbs.push(db);
   const storage={all:(q,...p)=>db.prepare(q).all(...p),run:(q,...p)=>db.prepare(q).run(...p),transaction:fn=>{db.exec('BEGIN');try{const r=fn();db.exec('COMMIT');return r;}catch(e){db.exec('ROLLBACK');throw e;}},flush:async()=>{}};
   groups.set(name,new Journal(storage));
  }
  return groups.get(name);
 };
 // Use production HTTP and journal operations; the real Worker/RPC case is tested separately.
 const env={SIGNUP_LIMITER:{limit:async()=>({success:true})},GROUPS:{getByName:name=>({dispatch:async(op,auth,args)=>{
  try {const j=get(name);let body;
   if(op.startsWith('directory'))body=await j[op](args,(group,method,body,auth)=>get(group)[method](auth,body));
   else body=await (op==='create'||op==='join'?j[op](args):j[op](auth,args));
   return {status:200,body};
  }catch(e){return {status:e.status??503,body:{error:{code:e.code??'storage_unavailable',message:e.message}}};}
 }})}};
 t.after(()=>dbs.forEach(db=>db.close()));
 const call=async(path,body,auth,ip='fixture-ip')=>{const r=await handle(new Request('https://sync.test/api/v1'+path,{method:body===undefined?'GET':'POST',headers:{'content-type':'application/json','X-PlanD-Records-Version':'6','CF-Connecting-IP':ip,...(auth?{Authorization:'Bearer '+auth}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})}),env);return {status:r.status,body:await r.json()};};
 return {get,call};
}
async function setup(f){
 const result=await f.call('/groups',{requestId:id(),deviceId:id(),groupName:'会場テスト',activityName:'管理者',registrationToken:token()});assert.equal(result.status,200);
 return result.body;
}

test('only administrator can reverse another author rescue; server preserves signed original',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId,sessionId=id();
 await f.call(`/groups/${g}/venue`,{requestId:sessionId,password,areaId:'kyoto-gosho-south'},admin.token);
 const member=(await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'Helper',deviceId:id(),registrationToken:token()})).body;
 const original=f.get(g).history('incidents').find(e=>e.kind==='CARE'&&e.action==='RESCUED');
 const push=(record,auth)=>f.call(`/groups/${g}/records`,{records:[{collection:'incidents',record}]},auth);
 const base={...original,eventId:id(),authorId:member.deviceId,authorName:'Helper',sequence:2,createdAt:Date.now(),action:'REVOKED',supersedes:original.eventId};
 const denied=await push(base,member.token);assert.equal(denied.status,403,JSON.stringify(denied.body));assert.equal(denied.body.error.code,'care_forbidden');
 const helperRescue={...original,eventId:id(),authorId:member.deviceId,authorName:'Helper',sequence:2,createdAt:Date.now(),supersedes:null};
 assert.equal((await push(helperRescue,member.token)).status,200);
 const next=Math.max(...f.get(g).history('incidents').filter(e=>e.authorId===admin.deviceId&&e.incidentId===sessionId).map(e=>e.sequence))+1;
 const reversal={...base,eventId:id(),authorId:admin.deviceId,authorName:'管理者',sequence:next,supersedes:helperRescue.eventId};
 assert.equal((await push(reversal,admin.token)).status,200);assert.equal((await push(reversal,admin.token)).status,200);
 assert.deepEqual(f.get(g).history('incidents').find(e=>e.eventId===helperRescue.eventId),helperRescue);
 const pending={...helperRescue,eventId:id(),sequence:3},early={...reversal,eventId:id(),sequence:next+1,supersedes:pending.eventId};
 assert.equal((await push(early,admin.token)).status,409);
 assert.equal((await push(pending,member.token)).status,200);assert.equal((await push(early,admin.token)).status,200);
});
test('administrator starts venue and forty named attendees join its existing incident without home records',async t=>{
 const f=fixture(t),admin=await setup(f),sessionId=id();
 const started=await f.call(`/groups/${admin.groupId}/venue`,{requestId:sessionId,password,areaId:'kyoto-gosho-south'},admin.token);
 assert.equal(started.status,200,JSON.stringify(started.body));
 assert.equal(started.body.venue.incidentId,sessionId);
 assert.equal((await f.call('/venue/check',{groupName:'会場テスト',password})).status,200);
 const inputs=Array.from({length:40},()=>({groupName:'会場テスト',password,activityName:'同じ名前',deviceId:id(),registrationToken:token()}));
 const joined=[];for(const input of inputs){const r=await f.call('/venue/join',input);assert.equal(r.status,200,JSON.stringify(r.body));joined.push(r.body);}
 assert.ok(joined.every(r=>r.role==='MEMBER'&&r.venue.incidentId===sessionId));
 assert.equal(new Set(joined.map(r=>r.memberId)).size,40);
 const replay=await f.call('/venue/join',inputs[0]);assert.equal(replay.body.memberId,joined[0].memberId);
 const snapshot=f.get(admin.groupId).snapshot();assert.equal(snapshot.roster.members.length,41);
 assert.equal(snapshot.venue.placements.length,41);assert.equal(new Set(snapshot.venue.placements.map(p=>p.latitude+':'+p.longitude)).size,41);
 assert.equal(f.get(admin.groupId).history('residents').length,0);
 const text=JSON.stringify(snapshot);assert.equal(text.includes(password),false);assert.equal(text.includes('passwordHash'),false);
});
test('wrong password, member start and conflicting group names are refused without changing membership',async t=>{
 const f=fixture(t),admin=await setup(f),start={requestId:id(),password,areaId:'kyoto-gosho-south'};
 assert.equal((await f.call(`/groups/${admin.groupId}/venue`,start,admin.token)).status,200);
 const bad=await f.call('/venue/join',{groupName:'会場テスト',password:'incorrect',activityName:'悪い入力',deviceId:id(),registrationToken:token()});assert.equal(bad.status,403);
 assert.equal(f.get(admin.groupId).snapshot().roster.members.length,1);
 const member=await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'参加者',deviceId:id(),registrationToken:token()});assert.equal(member.status,200);
 assert.equal((await f.call(`/groups/${admin.groupId}/venue`,start,member.body.token)).status,403);
 const other=await setup(f);assert.equal((await f.call(`/groups/${other.groupId}/venue`,{...start,requestId:id()},other.token)).status,409);
 const retry=await f.call(`/groups/${admin.groupId}/venue`,start,admin.token);assert.equal(retry.status,200);assert.equal(f.get(admin.groupId).history('quakes').length,1);
});
test('invalid attempts remain bounded even though forty successful joins are allowed',async t=>{
 const f=fixture(t);let limited=false;
 for(let n=0;n<125;n++){const r=await f.call('/venue/check',{groupName:'存在しない会場',password},undefined,'wrong-input-ip');if(r.status===429){limited=true;break;}}
 assert.equal(limited,true);
});

test('venue credentials do not authorize roster reads or caller-supplied administrator roles',async t=>{
 const f=fixture(t),admin=await setup(f),start={requestId:id(),password,areaId:'kyoto-gosho-south'};
 await f.call(`/groups/${admin.groupId}/venue`,start,admin.token);
 const unauthenticated=await f.call(`/groups/${admin.groupId}/roster`);
 assert.equal(unauthenticated.status,401);assert.equal(JSON.stringify(unauthenticated.body).includes(admin.deviceId),false);
 const wrongName=await f.call('/venue/check',{groupName:'unknown group',password});
 const wrongPassword=await f.call('/venue/check',{groupName:'会場テスト',password:'not-the-secret'});
 assert.equal(wrongName.status,403);assert.deepEqual(wrongName.body,wrongPassword.body);
 const joined=await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'管理者',deviceId:id(),registrationToken:token(),role:'ADMIN'});
 assert.equal(joined.status,200);assert.equal(joined.body.role,'MEMBER');
 assert.equal((await f.call(`/groups/${admin.groupId}/invitations`,{},joined.body.token)).status,403);
 assert.equal((await f.call(`/groups/${admin.groupId}/municipality`,{code:'22205'},joined.body.token)).status,403);
 assert.equal((await f.call(`/groups/${admin.groupId}/venue`,{...start,requestId:id()},joined.body.token)).status,403);
 assert.equal((await f.call(`/groups/${admin.groupId}/roster`,undefined,token())).status,401);
 const other=await setup(f);assert.equal((await f.call(`/groups/${other.groupId}/roster`,undefined,joined.body.token)).status,401);
});

test('knowing the shared venue password cannot replace the administrator device credential',async t=>{
 const f=fixture(t),admin=await setup(f);await f.call(`/groups/${admin.groupId}/venue`,{requestId:id(),password,areaId:'kyoto-gosho-south'},admin.token);
 const forged=await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'管理者',deviceId:admin.deviceId,registrationToken:token()});
 assert.equal(forged.status,409);assert.equal(f.get(admin.groupId).snapshot().roster.members.length,1);
 assert.equal((await f.call(`/groups/${admin.groupId}/roster`,undefined,admin.token)).status,200);
 const config=f.get(admin.groupId).venueConfig();assert.notEqual(config.passwordHash,password);assert.equal(config.salt.length,32);assert.equal(config.passwordHash.length,64);
 const publicBody=JSON.stringify(f.get(admin.groupId).snapshot());assert(!publicBody.includes(password));assert(!publicBody.includes(config.passwordHash));assert(!publicBody.includes(admin.token));
});

test('ending a public venue refuses new password joins without deleting previous records',async t=>{
 const f=fixture(t),admin=await setup(f),requestId=id();await f.call(`/groups/${admin.groupId}/venue`,{requestId,password,areaId:'kyoto-gosho-south'},admin.token);
 const j=f.get(admin.groupId);j.append(await j.identity(admin.token),{collection:'quakes',record:{groupId:admin.groupId,eventId:id(),actorDeviceId:admin.deviceId,operatedAt:Date.now(),action:'END',training:true,targetId:requestId}});
 assert.equal((await f.call('/venue/check',{groupName:'会場テスト',password})).status,409);
 assert.equal((await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'終了後',deviceId:id(),registrationToken:token()})).status,409);
 assert.equal(j.snapshot().roster.members.length,1);assert.equal(j.history('quakes').length,2);
});

test('private training assigns administrator and invited members without a public password or home writes',async t=>{
 const f=fixture(t),admin=await setup(f),sessionId=id();
 const invitation=await f.call(`/groups/${admin.groupId}/invitations`,{},admin.token);
 const join=async name=>f.call(`/groups/${admin.groupId}/join`,{deviceId:id(),activityName:name,registrationToken:token(),code:invitation.body.code});
 const member=await join('既存参加者');assert.equal(member.status,200);
 const input={requestId:sessionId,areaId:'kyoto-gosho-south',privateTraining:true,title:'位置の訓練',area:'京都'};
 const started=await f.call(`/groups/${admin.groupId}/venue`,input,admin.token);
 assert.equal(started.status,200,JSON.stringify(started.body));assert.equal(started.body.venue.placements.length,2);
 assert.ok(started.body.venue.placements.some(p=>p.deviceId===admin.deviceId));
 assert.equal(f.get(admin.groupId).history('quakes')[0].title,'位置の訓練');
 assert.equal((await f.call(`/groups/${admin.groupId}/venue`,input,member.body.token)).status,403);
 const later=await join('途中参加');assert.equal(later.status,200);assert.equal(later.body.venue.placements.length,3);
 assert.equal(f.get(admin.groupId).history('residents').length,0);
 assert.equal((await f.call('/venue/check',{groupName:'会場テスト',password})).status,403);
 const replay=await f.call(`/groups/${admin.groupId}/venue`,input,admin.token);assert.equal(replay.body.venue.placements.length,3);
 const j=f.get(admin.groupId),actor=await j.identity(admin.token);
 j.append(actor,{collection:'quakes',record:{groupId:admin.groupId,eventId:id(),actorDeviceId:admin.deviceId,operatedAt:Date.now(),action:'END',training:true,targetId:sessionId}});
 assert.equal(j.venueSnapshot().closed,true);const afterEnd=await join('終了後参加');assert.equal(afterEnd.status,200);assert.equal(afterEnd.body.venue.placements.length,3);

});

test('shared demo clock defaults to three minutes and only admin may change active-session projection',async t=>{
 const f=fixture(t),admin=await setup(f),g=admin.groupId,sessionId=id();
 const started=(await f.call(`/groups/${g}/venue`,{requestId:sessionId,password,areaId:'kyoto-gosho-south'},admin.token)).body;
 assert.ok(started.venue.startedAt-started.venue.simulationStartedAt>=179999);
 const member=(await f.call('/venue/join',{groupName:'会場テスト',password,activityName:'Member',deviceId:id(),registrationToken:token()})).body;
 const j=f.get(g),originals=JSON.stringify(['quakes','incidents','responses'].map(k=>j.history(k))),homes=JSON.stringify(j.venueSnapshot().placements);
 const change=(minutes,auth=admin.token,s=sessionId)=>f.call(`/groups/${g}/venue-clock`,{minutes,sessionId:s},auth);
 assert.equal((await change(10,member.token)).status,403);
 for(const invalid of [-1,1.5,1441,'3',null])assert.equal((await change(invalid)).status,400);
 assert.equal((await change(10,admin.token,id())).status,409);
 for(const minutes of [0,3,15]){const response=await change(minutes);assert.equal(response.status,200,JSON.stringify(response.body));assert.ok(Math.abs(Date.now()-response.body.venue.simulationStartedAt-minutes*60000)<3000);const other=(await f.call(`/groups/${g}/roster`,undefined,member.token)).body;assert.equal(other.venue.simulationStartedAt,response.body.venue.simulationStartedAt);}
 assert.equal(JSON.stringify(['quakes','incidents','responses'].map(k=>j.history(k))),originals);assert.equal(JSON.stringify(j.venueSnapshot().placements),homes);
 j.append(await j.identity(admin.token),{collection:'quakes',record:{groupId:g,eventId:id(),actorDeviceId:admin.deviceId,operatedAt:Date.now(),action:'END',training:true,targetId:sessionId}});
 assert.equal((await change(5)).status,409);
});

test('administrator can choose initial elapsed minutes without backdating original quake or response records',async t=>{
 for(const minutes of [0,5]){const f=fixture(t),admin=await setup(f),g=admin.groupId;
  const result=await f.call(`/groups/${g}/venue`,{requestId:id(),password,areaId:'kyoto-gosho-south',elapsedMinutes:minutes},admin.token);assert.equal(result.status,200);const v=result.body.venue;
  assert.ok(Math.abs(v.startedAt-v.simulationStartedAt-minutes*60000)<=2);assert.ok(f.get(g).history('quakes')[0].operatedAt>=v.startedAt);
 }
 const f=fixture(t),admin=await setup(f);
 for(const minutes of [-1,1.5,1441,'5'])assert.equal((await f.call(`/groups/${admin.groupId}/venue`,{requestId:id(),password,areaId:'kyoto-gosho-south',elapsedMinutes:minutes},admin.token)).status,400);
});
