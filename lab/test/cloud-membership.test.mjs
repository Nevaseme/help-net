import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {quakeStates} from '../web/managed-records.mjs';
const id=()=>crypto.randomUUID();
const member=(role='MEMBER')=>{const key=id();return {memberId:key,activityName:role,role,deviceIds:[key]};};
const members=[member('ADMIN'),member()],group='cloud-history';
const roster=n=>({version:1,groupId:group,members:[...members,...Array.from({length:n-2},()=>member())]});
const snapshot=(revision,roster)=>({revision,roster});
test('joining members preserves a formed disaster at the original quorum',async()=>{
 const s=await Store.open(id(),indexedDB),r1=roster(2),r2={...r1,members:[...r1.members,...Array.from({length:8},()=>member())]},me=members[0].memberId;
 try {
  await s.importCloudRoster({roster:r1,rosterRevision:1,rosterHistory:[snapshot(1,r1)]},me);
  for(const [i,m] of members.entries())await s.commandQuake({groupId:group,eventId:id(),actorDeviceId:m.memberId,operatedAt:i+1,action:'REPORT',training:false,title:'test',area:'test'});
  const before=quakeStates(await s.roster(group),await s.quakeEvents(group));assert.equal(before.length,1);
  assert.equal((await s.quakeEvents(group)).find(e=>e.action==='FORM').rosterRevision,1);
  await s.importCloudRoster({roster:r2,rosterRevision:2,rosterHistory:[snapshot(1,r1),snapshot(2,r2)]},me);
  assert.deepEqual(quakeStates(await s.roster(group),await s.quakeEvents(group)),before);
  await assert.rejects(s.importCloudRoster({roster:r2,rosterRevision:2,rosterHistory:[snapshot(1,r2),snapshot(2,r2)]},me));
  assert.equal((await s.roster(group)).revision,2);
 } finally{s.close();}
});
test('membership updates reject role changes and retain an explicit legacy baseline',async()=>{
 const s=await Store.open(id(),indexedDB),r=roster(2),me=members[0].memberId;
 try {
  await s.importRoster(r,me);
  await s.importCloudRoster({roster:r,rosterRevision:1,rosterHistory:[snapshot(1,r)]},me);
  assert.deepEqual((await s.roster(group)).history.find(h=>h.revision===0).members,r.members);
  const changed={...r,members:r.members.map((m,i)=>i?{...m,role:'ADMIN'}:m)};
  await assert.rejects(s.importCloudRoster({roster:changed,rosterRevision:2,rosterHistory:[snapshot(1,r),snapshot(2,changed)]},me));
 } finally{s.close();}
});
