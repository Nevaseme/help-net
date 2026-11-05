import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createVenueScenario} from '../src/venue-scenario.mjs';
import {demoCommunityAtPoint} from '../../lab/web/community-overview.mjs';
import catalogue from '../src/venue-buildings.json' with {type:'json'};
test('smaller neighborhood boundaries retain reviewed homes and valid simulated phone locations',()=>{
 const scenario=createVenueScenario(crypto.randomUUID());
 const area=ring=>Math.abs(ring.reduce((s,p,i)=>{const q=ring[(i+1)%ring.length];return s+p[0]*q[1]-q[0]*p[1];},0))/2;
 const former={'demo-west':.002*.007,'demo-central':.0044*.007,'demo-east':.0026*.007};
 for(const c of scenario.communities){const ratio=area(c.boundary)/former[c.id];assert.ok(ratio>=.45&&ratio<=.6,`${c.id}: ${ratio}`);assert.equal(demoCommunityAtPoint(c.latitude,c.longitude,scenario),c.id);}
 for(const p of scenario.people){assert.equal(demoCommunityAtPoint(p.home.latitude,p.home.longitude,scenario),p.communityId);assert.ok(catalogue.homes.some(h=>h.buildingId===p.home.buildingId));if(!p.positionUnknown){assert.equal(demoCommunityAtPoint(p.position.latitude,p.position.longitude,scenario),p.communityId);}}
 assert.equal(demoCommunityAtPoint(35.018,135.763,scenario),null);
});
