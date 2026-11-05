import test from 'node:test';
import assert from 'node:assert/strict';
import {withUnknownDemoResidents} from '../web/venue-observations.mjs';
import {venuePlacements,venuePositions,venuePlaces} from '../web/venue-demo.mjs';
import {incidentCaption,incidentStatus,earthquakeRequirement} from '../web/incident-presentation.mjs';
import {menuEnglish} from '../web/menu-english.mjs';
const sessionId='11111111-1111-4111-8111-111111111111';
const home={buildingId:'22222222-2222-4222-8222-222222222222',latitude:35.0145,longitude:135.7622,address:'Fictional home',floor:'2',room:'Bedroom'};
test('unknown demo residents keep home separate from missing phone location',()=>{
 const source={version:1,sessionId,communities:[{id:'demo-central'}],people:[{deviceId:'33333333-3333-4333-8333-333333333333',name:'Original',safety:'SAFE',communityId:'demo-central',home}]};
 const scenario=withUnknownDemoResidents(source);
 assert.equal(source.people.length,1);assert.equal(scenario.people.length,3);
 assert.deepEqual(withUnknownDemoResidents(scenario),scenario);
 const unknown=scenario.people.filter(p=>p.positionUnknown);assert.equal(unknown.length,2);
 assert.deepEqual(new Set(unknown.map(p=>p.safety)),new Set(['UNREPORTED','SOS']));
 const venue={version:1,mode:'VENUE_DEMO',groupId:'44444444-4444-4444-8444-444444444444',sessionId,incidentId:sessionId,startedAt:1,positionMode:'KYOTO',placements:[],scenario};
 for(const p of unknown){assert.equal(venuePlacements(venue).find(x=>x.deviceId===p.deviceId).latitude,null);assert.ok(!venuePositions(venue).some(x=>x.originId===p.deviceId));assert.ok(venuePlaces(venue).some(x=>x.kind==='HOME'&&x.authorId===p.deviceId));}
});
test('incident English labels preserve user titles but translate system titles',()=>{
 const view={start:{training:true,title:'会場デモ',area:'御所南〜烏丸御池',createdAt:1800000000000,authorName:'Alex'},closed:false};
 assert.doesNotMatch(incidentCaption(view,'en'),/[ぁ-んァ-ヶ一-龯]/);
 assert.doesNotMatch(incidentStatus(view,{language:'en',canEnd:false,managed:true}),/[ぁ-んァ-ヶ一-龯]/);
 assert.match(incidentCaption({...view,start:{...view.start,title:'利用者の入力'}},'en'),/利用者の入力/);
 assert.match(earthquakeRequirement(2,'en'),/2/);
 for(const key of ['訓練を始める','実際の地震を報告する','名称','地震発生を報告する','訓練を開始する','訓練を開始','訓練名'])assert.ok(menuEnglish[key],key);
});

import {buildingAge} from '../web/building-details.mjs';
test('building age advances at local New Year and preserves unknown years',()=>{assert.equal(buildingAge(2002,new Date(2026,11,31,23,59)),24);assert.equal(buildingAge(2002,new Date(2027,0,1)),25);assert.equal(buildingAge(null),null);});
