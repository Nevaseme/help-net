import test from 'node:test';
import assert from 'node:assert/strict';
import {validateResponse,responseHeads} from '../web/managed-records.mjs';
const person='00000000-0000-4000-8000-000000000001',actor='00000000-0000-4000-8000-000000000002',incident='00000000-0000-4000-8000-000000000003';
const roster={version:1,groupId:'test',members:[{memberId:actor,deviceIds:[actor],activityName:'Recorder',role:'ADMIN'}]};
const event=(id,state,replaces=[])=>({groupId:'test',eventId:id,actorDeviceId:actor,sequence:1,operatedAt:1,incidentId:incident,replaces,change:{type:'INJURY',target:{kind:'PERSON',id:person},state}});
test('injury is explicit, corrected independently of safety, and restricted to people',()=>{
 const first=event(crypto.randomUUID(),'INJURED');assert.doesNotThrow(()=>validateResponse(roster,first));
 const corrected=event(crypto.randomUUID(),'UNINJURED',[first.eventId]);assert.deepEqual(responseHeads(roster,[corrected,first]).map(e=>e.change.state),['UNINJURED']);
 assert.throws(()=>validateResponse(roster,{...first,change:{...first.change,state:'SOS'}}));
 assert.throws(()=>validateResponse(roster,{...first,change:{...first.change,target:{kind:'REPORT',id:person}}}));
});
