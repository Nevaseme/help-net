import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createVenueScenario} from '../src/venue-scenario.mjs';
import {demoPhonePoint,demoCommunityAt} from '../src/venue-layout.mjs';
import catalogue from '../src/venue-buildings.json' with {type:'json'};
test('128 demo phone positions stay spread out within the home neighborhood; homes retain building evidence',()=>{
 const scenario=createVenueScenario(crypto.randomUUID()),counts=new Map();
 const audience=catalogue.homes.map(home=>{const region=demoCommunityAt(home.longitude),slot=counts.get(region)||0;counts.set(region,slot+1);return {home,position:demoPhonePoint(region,24+slot)};});
 const all=[...scenario.people.filter(p=>!p.positionUnknown),...audience];assert.equal(all.length,128);
 const distance=(a,b)=>Math.hypot((a.latitude-b.latitude)*111195,(a.longitude-b.longitude)*91000);
 for(const p of all){assert.equal(demoCommunityAt(p.position.longitude),demoCommunityAt(p.home.longitude));assert.ok(p.position.latitude>=35.011&&p.position.latitude<=35.018);assert.ok(p.home.plateauId);assert.equal(p.home.addressPrecision,'verified-building-address');}
 for(let i=0;i<all.length;i++)for(let j=i+1;j<all.length;j++)assert.ok(distance(all[i].position,all[j].position)>40,'no dense phone pile, including audience vs scenario');
 for(const c of scenario.communities){const pts=scenario.people.filter(p=>p.communityId===c.id&&!p.positionUnknown).map(p=>p.position);assert.ok(Math.max(...pts.map(p=>p.latitude))-Math.min(...pts.map(p=>p.latitude))>.005);assert.equal(demoCommunityAt(c.longitude),c.id);}
});
