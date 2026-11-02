import test from 'node:test';
import assert from 'node:assert/strict';
import {communityOverview,issueCommunityReport,communityReportHtml} from '../web/community-overview.mjs';
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
