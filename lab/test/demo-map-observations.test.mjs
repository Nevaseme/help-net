import test from 'node:test';
import assert from 'node:assert/strict';
import {createVenueScenario} from '../../cloud-sync/src/venue-scenario.mjs';
import {withDemoObservations} from '../web/venue-observations.mjs';
import {venueResidents} from '../web/venue-demo.mjs';
import {personPresentation} from '../web/web1617-ui.mjs';
import {communityMapOverview} from '../web/community-overview.mjs';

test('explicit observations and legacy fallback preserve identities and answers',()=>{
 const scenario=createVenueScenario('session'),old=structuredClone(scenario);
 old.people.forEach(p=>delete p.observations);
 assert.deepEqual(withDemoObservations(scenario),scenario);
 const restored=withDemoObservations(old);assert.deepEqual(restored.people.map(({observations,...p})=>p),old.people);assert.equal(restored.people[6].observations.enclosed,true);
 const venue={scenario,sessionId:'session',startedAt:1000,groupId:'g',placements:[{deviceId:'real',name:'Actual participant',latitude:35,longitude:135}]};
 const residents=venueResidents(venue);
 assert.equal(residents[0].impactAt,undefined);assert.equal(residents[0].enclosedAt,undefined);
 for(const community of scenario.communities){
  const eligible=scenario.people.filter(p=>p.communityId===community.id&&p.safety!=='SAFE'&&!p.initialRescue);
  assert.ok(eligible.some(p=>p.observations.impact));assert.ok(eligible.some(p=>p.observations.enclosed));
 }
 const enclosed=residents.find(p=>p.enclosedAt&&!p.impactAt);
 assert.ok(personPresentation({...enclosed,safety:'UNREPORTED'},[],{start:{eventId:'session'}},'real').showObservations);
 assert.equal(personPresentation({...enclosed,safety:'SAFE'},[],{start:{eventId:'session'}},'real').showObservations,false);
 assert.equal(personPresentation({...enclosed,safety:'SOS'},[{eventId:'care',subjectId:enclosed.originId,action:'RESCUED',createdAt:2000}],{start:{eventId:'session'}},'real').showObservations,false);
 assert.equal(old.people.some(p=>Object.hasOwn(p,'observations')),false,'legacy stored metadata is not mutated');
});

test('map includes only other neighborhoods with aggregate counts and no person details',()=>{
 const communities=['west','east'].map(id=>({id,nameJa:id,nameEn:id}));
 const people=[{id:'me',communityId:'west',status:'SAFE',latitude:35,longitude:135},{id:'other',name:'Private name',communityId:'east',status:'SOS',latitude:35.1,longitude:135.1}];
 const rows=communityMapOverview({communities,people,heads:[]},'me');
 assert.equal(rows.length,1);assert.equal(rows[0].id,'east');assert.equal(rows[0].sos,1);assert.equal(rows[0].total,1);
 assert.equal(rows[0].latitude,35.1);assert.ok(!JSON.stringify(rows).includes('Private name'));
 assert.equal(communityMapOverview({communities,people:people.map(p=>({...p,latitude:null})),heads:[]},'me').length,0);
});
