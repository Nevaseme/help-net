import {test} from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store,currentEvents} from '../web/sync.mjs';
import {revokePersonRescue} from '../web/incidents.mjs';
test('administrator reverses all concurrent rescue heads; restart retains original history',async()=>{
 const ids=Array.from({length:3},()=>crypto.randomUUID()),groupId='rescue-test',dbName=crypto.randomUUID();
 const roster={version:1,groupId,members:ids.map((id,i)=>({memberId:id,deviceIds:[id],activityName:'Tester '+i,role:i===0?'ADMIN':'MEMBER'}))};
 const s=await Store.open(dbName,indexedDB);await s.importRoster(roster,ids[0]);
 await s.saveProfile({groupId,originId:ids[0],name:'Organizer',latitude:35,longitude:135,floor:'',room:'',safety:'UNREPORTED'});
 const incidentId=crypto.randomUUID();await s.commandQuake({groupId,eventId:incidentId,actorDeviceId:ids[0],operatedAt:100,action:'TRAINING_START',training:true,title:'Test',area:'Test'});
 const original=[];for(const authorId of ids.slice(1))original.push(await s.appendIncident({groupId,eventId:crypto.randomUUID(),incidentId,authorId,authorName:'Helper',createdAt:200,kind:'CARE',subjectId:ids[2],action:'RESCUED',note:''}));
 await assert.rejects(revokePersonRescue(s,{groupId,incidentId,authorId:ids[1]},ids[2]));
 assert.equal(await revokePersonRescue(s,{groupId,incidentId,authorId:ids[0]},ids[2]),2);s.close();
 const reopened=await Store.open(dbName,indexedDB),events=(await reopened.incidents(groupId)).filter(e=>e.kind==='CARE');
 assert.equal(currentEvents(events,roster).filter(e=>e.action==='RESCUED').length,0);
 for(const e of original)assert.deepEqual(events.find(x=>x.eventId===e.eventId),e);
 assert.equal(events.filter(e=>e.action==='REVOKED').length,2);reopened.close();
});
