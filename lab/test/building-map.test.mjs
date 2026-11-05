import test from 'node:test';
import assert from 'node:assert/strict';
import {mapBuildingFeatures,linkedBuildingResidents} from '../web/building-details.mjs';
import {communityMapOverview} from '../web/community-overview.mjs';
test('only individual rendered footprints can open building details; duplicate tile fragments merge',()=>{
 const f={source:'kyoto-risk',sourceLayer:'buildings',properties:{bid:'bldg_a',year:1986,floors:2,structure:'601'}};
 assert.deepEqual(mapBuildingFeatures([f,f,{...f,sourceLayer:'footprints'},{properties:{name:'Station'}}]).map(b=>b.id),['bldg_a']);
 assert.equal(mapBuildingFeatures([f])[0].attributes.year,1986);
});
test('building residents come from explicit current home links, never nearby phone locations',()=>{
 const rows=[{kind:'BUILDING',eventId:'home',buildingId:'home',label:'Home',sequence:1,locationReference:{plateauId:'bldg_a'}},{kind:'HOME',authorId:'linked',buildingId:'home',sequence:1},{kind:'HOME',authorId:'moved',buildingId:'home',sequence:1},{kind:'HOME',authorId:'moved',buildingId:'other',sequence:2}];
 const people=[{originId:'linked'},{originId:'moved'},{originId:'nearby',latitude:35,longitude:135}];
 assert.deepEqual(linkedBuildingResidents({id:'bldg_a'},rows,people).map(p=>p.originId),['linked']);
});
test('neighborhood summary counts only open high-priority reports',()=>{
 const c={id:'east',nameJa:'東',nameEn:'East'},people=[{id:'other',communityId:'east',latitude:35,longitude:135}];
 const heads=[...['open','done','canceled'].flatMap(id=>[{change:{type:'REPORT',reportId:id,regionIds:['east'],cancelled:id==='canceled'}},{change:{type:'PRIORITY',target:{kind:'REPORT',id},urgency:'HIGH'}}]),{change:{type:'PROGRESS',target:{kind:'REPORT',id:'done'},status:'DONE'}}];
 assert.equal(communityMapOverview({communities:[c],people,heads},'me')[0].urgentOpen,1);
});
