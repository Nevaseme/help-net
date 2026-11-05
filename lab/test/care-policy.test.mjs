import {test} from 'node:test';
import assert from 'node:assert/strict';
import {canReplaceCare,currentCareHeads} from '../web/care-policy.mjs';
const rescue={eventId:'rescue',groupId:'g',incidentId:'q',subjectId:'person',authorId:'helper',action:'RESCUED'};
const revoke={...rescue,eventId:'revoke',authorId:'admin',action:'REVOKED',supersedes:'rescue'};
const roster={groupId:'g',members:[{role:'ADMIN',deviceIds:['admin']},{role:'MEMBER',deviceIds:['member']}]};
test('trusted administrator can reverse another author rescue without modifying it',()=>{
 const original=JSON.stringify(rescue);assert.equal(canReplaceCare(revoke,rescue,roster),true);
 assert.deepEqual(currentCareHeads([rescue,revoke],roster),[revoke]);assert.equal(JSON.stringify(rescue),original);
});
test('member, forged event role, different person/incident/group cannot replace rescue',()=>{
 for(const invalid of [{...revoke,authorId:'member',role:'ADMIN'},{...revoke,subjectId:'other'},{...revoke,incidentId:'other'},{...revoke,groupId:'other'},{...revoke,action:'CHECKING'}]){
  assert.equal(canReplaceCare(invalid,rescue,roster),false);assert.ok(currentCareHeads([rescue,invalid],roster).includes(rescue));
 }
 assert.equal(canReplaceCare(revoke,rescue),false);
 assert.equal(canReplaceCare(revoke,rescue,{...roster,groupId:'other'}),false);
});
test('historical author corrections work and delayed original gives the same projection',()=>{
 const own={...revoke,authorId:'helper'};assert.equal(canReplaceCare(own,rescue),true);
 assert.deepEqual(currentCareHeads([own,rescue]),[own]);assert.deepEqual(currentCareHeads([rescue,own]),[own]);
});
