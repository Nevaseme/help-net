import test from 'node:test';
import assert from 'node:assert/strict';
import {indexedDB} from 'fake-indexeddb';
import {Store,SyncEngine} from '../web/sync.mjs';
import {validateQuake,quakeStates} from '../web/managed-records.mjs';
import {placeMapLabel} from '../web/map-label-layout.mjs';
const id=()=>crypto.randomUUID(),g='admin-start',a=id(),b=id();
const roster={version:1,groupId:g,members:[{memberId:a,deviceIds:[a],role:'ADMIN',activityName:'Admin'},{memberId:b,deviceIds:[b],role:'MEMBER',activityName:'Member'}]};
const start=()=>({groupId:g,eventId:id(),actorDeviceId:a,operatedAt:100,action:'ADMIN_START',training:false,title:'Earthquake',area:'Kyoto'});
test('only an administrator can start an earthquake alone; the ordinary-report quorum remains',async()=>{
 const s=await Store.open(id(),indexedDB);await s.importRoster(roster,a);
 try{const e=start();assert.throws(()=>validateQuake(roster,{...e,actorDeviceId:b}));assert.throws(()=>validateQuake(roster,{...e,training:true}));await s.commandQuake({...e,action:'REPORT'});assert.equal(quakeStates(roster,await s.quakeEvents(g)).length,0);await s.commandQuake({...e,eventId:id()});assert.equal(quakeStates(roster,await s.quakeEvents(g))[0].formation.training,false);await assert.rejects(s.commandQuake(start()));}finally{s.close();}
});
test('admin earthquake and subsequent answers and reports relay with v19',async()=>{
 const x=await Store.open(id(),indexedDB),y=await Store.open(id(),indexedDB),queue=[],sent=[],errors=[];
 await x.importRoster(roster,a);await y.importRoster(roster,b);
 const nodes=[x,y].map((s,i)=>new SyncEngine(g,i?b:a,s,(to,m)=>{sent.push(m);queue.push([i,Number(to),m]);},e=>errors.push(e)));
 const drain=async()=>{let n=0;while(queue.length){assert.ok(++n<500);const [from,to,m]=queue.shift();await nodes[to].receive(String(from),m);}};
 try{const e=start();await x.commandQuake(e);await x.appendIncident({groupId:g,eventId:id(),incidentId:e.eventId,authorId:a,authorName:'Admin',createdAt:110,kind:'ANSWER',safety:'SAFE'});const reportId=id();await x.appendResponse({groupId:g,incidentId:e.eventId,eventId:reportId,actorDeviceId:a,operatedAt:120,change:{type:'REPORT',reportId,category:'Blocked road',description:'A tree blocks the road.',latitude:35,longitude:135},replaces:[]});await nodes[0].connected('1');await nodes[1].connected('0');await drain();assert.deepEqual(errors,[]);assert.ok(sent.filter(m=>/^(QUAKE|RESPONSE|INCIDENT)_/.test(m.type)).every(m=>m.version===19));assert.equal((await y.responseEvents(g)).length,1);assert.ok((await y.incidents(g)).some(r=>r.kind==='ANSWER'));}finally{x.close();y.close();}
});
test('neighborhood label avoids the central safety icon and remains inside the viewport',()=>{
 const blocked=[{x:160,y:160,w:70,h:80},{x:15,y:20,w:136,h:112}];const r=placeMapLabel({x:190,y:200},{x:390,y:600},blocked);
 assert.ok(r.x>=8&&r.x+r.w<=382&&r.y>=12&&r.y+r.h<=544);for(const b of blocked)assert.ok(r.x+r.w<=b.x||r.x>=b.x+b.w||r.y+r.h<=b.y||r.y>=b.y+b.h);
});

test('joined member can answer without inventing a home record',async()=>{
 const {appendScopedIncident}=await import('../web/incidents.mjs');const s=await Store.open(id(),indexedDB);await s.importRoster(roster,a);
 try{const e=start();await s.commandQuake(e);await appendScopedIncident(s,{groupId:g,incidentId:e.eventId,authorId:b},'ANSWER',{safety:'SOS'});assert.equal((await s.incidents(g)).find(r=>r.kind==='ANSWER').authorName,'Member');assert.equal(await s.get(g,b),undefined);}finally{s.close();}
});
