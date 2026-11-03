import test from 'node:test';
import assert from 'node:assert/strict';
import {personPresentation,responseCases,isResolvedCase,reportEditSources,visiblePeople} from '../web/web1617-ui.mjs';
import {indexedDB} from 'fake-indexeddb';
import {Store} from '../web/sync.mjs';
import {managedIncidentOptions} from '../web/managed-records.mjs';
const person={originId:'other',name:'Private name',safety:'UNREPORTED',updatedAt:0};
test('unanswered person remains anonymous before three minutes; known position does not expose a home',()=>{
 const view={start:{createdAt:1000}};
 assert.equal(personPresentation(person,[],view,'me',180999).disclosed,false);
 assert.equal(personPresentation(person,[],view,'me',181000).disclosed,true);
 const sos=personPresentation({...person,safety:'SOS'},[],view,'me',2000,{latitude:35,longitude:139});
 assert.equal(sos.disclosed,true);assert.equal(sos.showHome,false);assert.equal(sos.unknownLocation,false);
 assert.equal(personPresentation({...person,safety:'SOS'},[],view,'me',2000).showHome,true);
});
test('safe names and observations are not presented as distress cases',()=>{
 const p=personPresentation({...person,safety:'SAFE'},[],null,'me');
 assert.equal(p.name,'無事の人');assert.equal(p.showObservations,false);
 assert.equal(responseCases({records:[{...person,safety:'SAFE'}],events:[],id:'me'},[]).length,0);
 assert.equal(personPresentation({...person,safety:'SAFE'},[],null,'me',2000,{latitude:35,longitude:139}).showHome,false);
 assert.equal(personPresentation({...person,safety:'SAFE'},[],null,'me',2000).showHome,true);
 assert.equal(personPresentation(person,[],{start:{createdAt:0}},'me',180000,{latitude:35,longitude:139}).showHome,true);
});
test('case projection preserves conflicting progress and material heads',()=>{
 const target={kind:'PERSON',id:'other'};
 const rows=responseCases({records:[{...person,safety:'SOS'}],events:[],id:'me'},[
  {change:{type:'PROGRESS',target,status:'IN_PROGRESS'},operatedAt:1},
  {change:{type:'PROGRESS',target,status:'DONE'},operatedAt:2},
  {change:{type:'MATERIAL',target,requestId:'m',name:'Water',unit:'L',needed:4,arranged:2},operatedAt:3}
 ]);
 assert.equal(rows[0].progress.length,2);assert.equal(rows[0].materials.length,1);assert.equal(rows[0].updatedAt,3);
 assert.equal(isResolvedCase(rows[0]),false);
 assert.equal(isResolvedCase({progress:[]}),false);
 assert.equal(isResolvedCase({progress:[{change:{status:'DONE'}}]}),true);
});
test('three-minute disclosure uses earliest EEW receipt or human report and later updates never reset it',async()=>{
 const a=crypto.randomUUID(),b=crypto.randomUUID(),group='privacy-clock';
 const roster={version:1,groupId:group,members:[{memberId:a,deviceIds:[a],activityName:'A',role:'ADMIN'},{memberId:b,deviceIds:[b],activityName:'B',role:'MEMBER'}]};
 const s=await Store.open(crypto.randomUUID(),indexedDB);await s.importRoster(roster,a);
 const incident='ee000001-0020-4000-8000-261030120000';
 const receipt=at=>({groupId:group,eventId:crypto.randomUUID(),actorDeviceId:a,operatedAt:at,action:'EEW',training:false,targetId:incident,eew:{source:'wolfx',eventId:'20261030120000',issuedAt:at,receivedAt:at}});
 await s.mergeQuake(receipt(1000));await s.mergeQuake(receipt(99000));
 let view=managedIncidentOptions(await s.incidents(group),roster,await s.quakeEvents(group))[0];
 assert.equal(view.start.createdAt,1000);assert.equal(personPresentation(person,[],view,'me',181000).disclosed,true);
 for(const [actor,at]of [[a,100],[b,200]])await s.commandQuake({groupId:group,eventId:crypto.randomUUID(),actorDeviceId:actor,operatedAt:at,action:'REPORT',training:false,title:'Event',area:'Area'});
 view=managedIncidentOptions(await s.incidents(group),roster,await s.quakeEvents(group))[0];
 assert.equal(view.start.createdAt,100);assert.equal(personPresentation({...person,updatedAt:180099},[],view,'me',180100).disclosed,true);s.close();
});
test('editing a visible report never chooses a canceled concurrent revision',()=>{
 const canceled={eventId:'a',change:{cancelled:true,description:'old canceled content'}},active={eventId:'b',change:{description:'active content'}},other={eventId:'c',change:{description:'other active content'}};
 assert.deepEqual(reportEditSources({reports:[canceled,active]}),[active]);
 assert.deepEqual(reportEditSources({reports:[canceled,active,other]}),[active,other]);
 assert.deepEqual(reportEditSources({reports:[canceled]}),[canceled]);
});

test('anonymous pre-disclosure phone observations remain visible without home disclosure',()=>{
 const result=personPresentation({...person,impactAt:100,enclosedAt:110},[],{start:{createdAt:100}},'me',200,{latitude:35,longitude:135});
 assert.equal(result.disclosed,false);assert.equal(result.showHome,false);assert.equal(result.showObservations,true);assert.equal(result.name,'応答なし');
});

test('venue mutual recognition does not expose homes for SOS with a known phone position',()=>{
 const p={...person,venueSessionId:'demo',homeAddress:'Private home',safety:'SOS'};
 const v={start:{eventId:'demo',createdAt:1}};
 const result=personPresentation(p,[],v,'me',200,{latitude:35,longitude:135});
 assert.equal(result.name,p.name);assert.equal(result.disclosed,true);assert.equal(result.showHome,false);
 assert.equal(personPresentation(p,[],v,'me',200).showHome,true);
});

test('map, people and task scopes use the same home community; outside reports remain available',()=>{
 const records=[{originId:'me',homeLongitude:135.759,safety:'SAFE'},{originId:'west',homeLongitude:135.7595,safety:'SOS'},{originId:'east',homeLongitude:135.763,safety:'SOS'}];
 const snapshot={id:'me',records,events:[],venue:{scenario:{people:[]},positionMode:'KYOTO'}};
 assert.deepEqual(visiblePeople(snapshot).map(p=>p.originId),['me','west']);
 const rows=responseCases(snapshot,[{change:{type:'REPORT',reportId:'outside',category:'道路閉塞',description:'Road in another town'},operatedAt:1}]);
 assert.deepEqual(rows.map(r=>r.target.id),['west','outside']);
 assert.deepEqual(visiblePeople({...snapshot,id:'absent'}),[]);
 assert.equal(visiblePeople({...snapshot,venue:null}).length,3);
});
