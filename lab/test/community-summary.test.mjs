import test from 'node:test';
import assert from 'node:assert/strict';
import * as overview from '../web/community-overview.mjs';

const report=(id,category,regionIds=['west'],cancelled=false)=>({change:{type:'REPORT',reportId:id,category,regionIds,cancelled}});
const reportCase=(id,...reports)=>({target:{kind:'REPORT',id},reports});
const personCase=(id,statusIcon='SOS')=>({target:{kind:'PERSON',id},statusIcon,person:{originId:id,safety:statusIcon}});
const communities=[{id:'west',nameJa:'西',nameEn:'West'},{id:'east',nameJa:'東',nameEn:'East'}];

test('issued reports preserve town totals without exporting undisclosed personal locations',()=>{
 const input={heads:[],communities,people:[{id:'own',name:'Own name',communityId:'west',status:'SAFE',buildingId:'a'},{id:'other',name:'Hidden name',communityId:'east',status:'SOS',buildingId:'b',latitude:35,longitude:135}],visiblePersonIds:['own']};
 const report=overview.issueCommunityReport(input,'en',100,'report');
 assert.equal(report.rows.reduce((n,r)=>n+r.total,0),2);assert.equal(report.people[0].name,'Own name');
 assert.equal(report.people[1].name,undefined);assert.equal(report.people[1].buildingId,undefined);assert.equal(report.people[1].latitude,undefined);
 assert.equal(input.people[1].name,'Hidden name');
});

test('incident summary counts only allowed cases, deduplicates IDs, and excludes rescued people',()=>{
 assert.equal(typeof overview.areaIncidentSummary,'function');
 const road=reportCase('r',report('r','道路閉塞'));
 const input={communities,people:[{id:'a',communityId:'west',status:'SOS'},{id:'hidden',communityId:'east',status:'SOS'}],heads:[report('hidden-report','建物倒壊')],cases:[personCase('a'),personCase('a'),personCase('rescued','RESCUED'),road,road,reportCase('c',report('c','建物倒壊',['east'])),reportCase('o',report('o','断水'))]};
 const summary=overview.areaIncidentSummary(input);
 assert.deepEqual(summary.counts,{total:4,sos:1,blockedRoad:1,buildingCollapse:1,other:1});
 assert.equal(summary.rows.find(r=>r.id==='west').total,3);
 assert.equal(summary.rows.find(r=>r.id==='east').total,1);
 assert.deepEqual(overview.areaIncidentSummary({...input,cases:[]}).counts,{total:0,sos:0,blockedRoad:0,buildingCollapse:0,other:0});
});

test('canceled revisions do not affect category or region; conflicting active categories count once as other',()=>{
 const active=reportCase('r',report('r','建物倒壊',['east'],true),report('r','ROAD_BLOCKED'));
 const conflict=reportCase('conflict',report('conflict','道路閉塞'),report('conflict','建物倒壊'));
 const canceled=reportCase('cancel',report('cancel','建物倒壊',[],true));
 const input={communities,cases:[active,conflict,canceled]};
 assert.equal(overview.areaCaseCategory(active),'道路閉塞');
 assert.equal(overview.areaCaseCategory(conflict),'その他');
 assert.equal(overview.areaCaseCategory(canceled),null);
 assert.deepEqual(overview.areaCaseCommunityIds(active,input),['west']);
 assert.deepEqual(overview.areaIncidentSummary(input).counts,{total:2,sos:0,blockedRoad:1,buildingCollapse:0,other:1});
});

test('legacy inputs require explicit SOS disclosure and do not equate damage with collapse',()=>{
 const summary=overview.areaIncidentSummary({people:[{id:'visible',status:'SOS',disclosed:true},{id:'hidden',status:'SOS'}],heads:[report('a','BUILDING_COLLAPSE',[]),report('b','BUILDING_DAMAGE',[]),report('c','ROAD_BLOCKED',[])]});
 assert.deepEqual(summary.counts,{total:4,sos:1,blockedRoad:1,buildingCollapse:1,other:1});
 assert.equal(summary.rows[0].id,'unknown');
 assert.equal(summary.rows[0].total,4);
});

test('other-town SOS contributes to aggregate counts but only permitted cases provide detail access',()=>{
 const own=personCase('own'),outside=personCase('outside'),road=reportCase('road',report('road','道路閉塞',['east']));
 const input={communities,people:[{id:'own',communityId:'west'},{id:'outside',communityId:'east'}],cases:[own,road],aggregateCases:[own,outside,road]};
 const summary=overview.areaIncidentSummary(input);
 assert.equal(summary.counts.total,3);
 assert.equal(summary.counts.sos,2);
 assert.equal(summary.accessibleCounts.total,2);
 assert.equal(summary.accessibleCounts.sos,1);
 assert.equal(summary.rows.find(r=>r.id==='east').sos,1);
 assert.equal(summary.accessibleRows.find(r=>r.id==='east').sos,0);
 assert.equal(summary.accessibleRows.find(r=>r.id==='east').blockedRoad,1);
 const readOnly=overview.areaIncidentSummary({...input,cases:[]});
 assert.equal(readOnly.counts.total,3);
 assert.equal(readOnly.accessibleCounts.total,0);
});

test('injury controls include only explicitly permitted people while legacy callers remain supported',()=>{
 const people=[{id:'own'},{id:'outside'}];
 assert.deepEqual(overview.communityInjuryPeople({people,visiblePersonIds:['own']}),[{id:'own'}]);
 assert.deepEqual(overview.communityInjuryPeople({people,visiblePersonIds:new Set(['own'])}),[{id:'own'}]);
 assert.deepEqual(overview.communityInjuryPeople({people,visiblePersonIds:[]}),[]);
 assert.deepEqual(overview.communityInjuryPeople({people}),people);
});
