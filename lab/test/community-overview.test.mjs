import test from 'node:test';
import assert from 'node:assert/strict';
import {communityOverview,issueCommunityReport,communityReportHtml,reportCommunityIds,areaCaseCommunityIds} from '../web/community-overview.mjs';

test('report location drives counts and filters without rewriting old region records',()=>{
 const communities=[{id:'west',boundary:[[0,0],[1,0],[1,1],[0,1]]},{id:'east',boundary:[[1,0],[2,0],[2,1],[1,1]]}];
 const report={type:'REPORT',reportId:'r',latitude:.5,longitude:1.5,regionIds:['west'],cancelled:false},saved=structuredClone(report);
 assert.deepEqual(reportCommunityIds(report,communities),['east']);
 assert.deepEqual(areaCaseCommunityIds({target:{kind:'REPORT',id:'r'},reports:[{change:report}]},{communities}),['east']);
 const counts=change=>communityOverview({people:[],heads:[{change}],communities});
 assert.equal(counts(report).find(r=>r.id==='east').reports,1);assert.equal(counts(report).find(r=>r.id==='west').reports,0);
 assert.equal(counts({...report,longitude:.5}).find(r=>r.id==='west').reports,1);
 assert.equal(counts({...report,longitude:3}).find(r=>r.id==='unknown').reports,1);
 assert.deepEqual(reportCommunityIds({...report,longitude:1},communities),['east']);
 const single=communityOverview({people:[],heads:[{change:{...report,longitude:3}}],communities:communities.slice(0,1)});
 assert.equal(single.find(r=>r.id==='unknown').reports,1);assert.deepEqual(report,saved);
 assert.deepEqual(reportCommunityIds(report,[{id:'west'}]),['west']);
});

test('cancelled material heads add no shortage; an active concurrent head remains visible with mixed-unit conflicts explicit',()=>{
 const target={kind:'PERSON',id:'a'},change={type:'MATERIAL',target,requestId:'m',name:'Water',unit:'bottles',needed:10,arranged:1};
 const input={people:[{id:'a',status:'SOS'}],heads:[{change:{...change,cancelled:true,cancelReason:'duplicate'}}]};
 assert.deepEqual(communityOverview(input)[0].materials,[]);
 input.heads.push({change:{...change,unit:'boxes',needed:2,arranged:0}});
 assert.deepEqual(communityOverview(input)[0].materials,[{name:'Water',unit:'boxes',shortfall:2,conflict:false}]);
 input.heads.push({change});const materials=communityOverview(input)[0].materials;
 assert.deepEqual(materials,[{name:'Water',unit:'boxes',shortfall:2,conflict:true},{name:'Water',unit:'bottles',shortfall:9,conflict:true}]);
});
test('overview separates safety, injuries, fictional people and conflicting materials',()=>{
 const people=[{id:'a',name:'Alex',communityId:'west',status:'RESCUED',fictional:true},{id:'b',name:'Bo',communityId:'west',status:'SOS'},{id:'c',name:'Cam',communityId:'west',status:'SAFE'}];
 const heads=[{eventId:'1',change:{type:'INJURY',target:{kind:'PERSON',id:'a'},state:'INJURED'}},{eventId:'2',change:{type:'INJURY',target:{kind:'PERSON',id:'c'},state:'UNINJURED'}},{eventId:'3',change:{type:'REPORT',reportId:'r',regionIds:['west'],cancelled:false}},{eventId:'4',change:{type:'MATERIAL',target:{kind:'REPORT',id:'r'},requestId:'m',name:'Water',unit:'litres',needed:10,arranged:3}},{eventId:'5',change:{type:'MATERIAL',target:{kind:'REPORT',id:'r'},requestId:'m',name:'Water',unit:'litres',needed:10,arranged:5}}];
 const rows=communityOverview({people:[...people,people[0]],heads,communities:[{id:'west',nameJa:'西',nameEn:'West'}]});
 assert.equal(rows[0].total,3);assert.equal(rows[0].fictional,1);assert.equal(rows[0].injured,1);assert.equal(rows[0].injuryUnknown,1);assert.equal(rows[0].rescued,1);assert.equal(rows[0].sos,1);assert.equal(rows[0].materials[0].shortfall,7);assert.equal(rows[0].materials[0].conflict,true);
 const report=issueCommunityReport({groupId:'g',groupName:'Team',incidentId:'i',people,heads,communities:[{id:'west',nameJa:'西',nameEn:'West'}]},'en',42,'issued');people[0].name='changed';assert.equal(report.people[0].name,'Alex');assert.equal(report.issuedAt,42);assert.equal(report.sourceIds.length,5);assert.match(communityReportHtml(report),/West/);assert.doesNotMatch(communityReportHtml(report),/changed/);
});
test('cancelled reports have no material shortfall; ambiguous injury remains unknown',()=>{
 const target={kind:'PERSON',id:'a'};const rows=communityOverview({people:[{id:'a',status:'SOS'}],heads:[{change:{type:'INJURY',target,state:'INJURED'}},{change:{type:'INJURY',target,state:'UNINJURED'}},{change:{type:'REPORT',reportId:'r',cancelled:true}},{change:{type:'MATERIAL',target:{kind:'REPORT',id:'r'},requestId:'m',name:'Rope',unit:'m',needed:10,arranged:0}}]});assert.equal(rows[0].injured,0);assert.equal(rows[0].injuryUnknown,1);assert.equal(rows[0].materials.length,0);
});
