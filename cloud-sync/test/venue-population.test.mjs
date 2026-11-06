import {test} from 'node:test';
import assert from 'node:assert/strict';
import {compactScenario,compactSlots,occupiedVenueScenario} from '../src/venue-compact.mjs';

test('central stays at 48 with 0–40 attendees, replacing only safe roles at the same homes and locations',()=>{
 const scenario=compactScenario(crypto.randomUUID(),Date.UTC(2026,10,6)),original=structuredClone(scenario),slots=compactSlots();
 assert.equal(slots.length,40);
 const central=scenario.people.filter(p=>p.communityId==='demo-central'),fixed=central.filter(p=>p.safety!=='SAFE');
 assert.equal(central.length,48);assert.equal(fixed.length,8);
 for(let n=0;n<=40;n++){
  const placements=slots.slice(0,n).map((p,slotId)=>({...p,slotId})),view=occupiedVenueScenario(scenario,placements),residents=view.people.filter(p=>p.communityId==='demo-central');
  assert.equal(residents.length+n,48);assert.deepEqual(residents.filter(p=>p.safety!=='SAFE'),fixed);
  assert.equal(view.people.filter(p=>p.communityId==='demo-west').length,40);assert.equal(view.people.filter(p=>p.communityId==='demo-east').length,40);
  for(const p of placements){const removed=central.find(r=>r.replacementSlotId===p.slotId);assert.equal(removed.safety,'SAFE');assert.deepEqual(removed.home,p.home);assert.deepEqual(removed.position,{latitude:p.latitude,longitude:p.longitude});}
  assert.equal(new Set([...residents,...placements].map(p=>p.home.plateauId)).size,48);
 }
 assert.deepEqual(scenario,original);
 assert.deepEqual(occupiedVenueScenario(scenario,[]),scenario,'released slots restore the same resident identity');
});

test('central critical roles have exactly the six requested sensor combinations and separate aged unknowns',()=>{
 const start=Date.UTC(2026,10,6),people=compactScenario(crypto.randomUUID(),start).people.filter(p=>p.communityId==='demo-central');
 const flags=status=>people.filter(p=>p.safety===status&&!p.positionUnknown).map(p=>[p.observations.impact,p.observations.enclosed]).sort();
 assert.deepEqual(flags('SOS'),[[false,false],[true,false],[true,true]]);
 assert.deepEqual(flags('UNREPORTED'),[[false,false],[false,true],[true,true]]);
 const unknown=people.filter(p=>p.positionUnknown);assert.equal(unknown.length,2);
 assert.deepEqual(unknown.map(p=>start-p.lastKnownPosition.acquiredAt),[7*86400000,3*86400000]);
 assert.ok(unknown.every(p=>p.position===null&&!Number.isInteger(p.replacementSlotId)));
 assert.ok(people.every(p=>p.home.buildingId&&p.home.householdId&&p.name&&!p.name.includes('undefined')));
});

test('earlier sessions are not resized or relocated',()=>{
 const old={...compactScenario(crypto.randomUUID(),Date.now()),layoutVersion:4},before=structuredClone(old);
 assert.equal(occupiedVenueScenario(old,[{slotId:0}]),old);assert.deepEqual(old,before);
 assert.equal(occupiedVenueScenario(null,[]),null);
});
