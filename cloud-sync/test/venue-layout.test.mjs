import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createVenueScenario} from '../src/venue-scenario.mjs';
import {demoCommunityAtPoint} from '../../lab/web/community-overview.mjs';
import layout from '../src/venue-compact-layout.json' with {type:'json'};
import {readFileSync,readdirSync} from 'node:fs';
import {gunzipSync} from 'node:zlib';
test('four-block neighborhoods contain all assigned homes and current phone positions',()=>{
 const scenario=createVenueScenario(crypto.randomUUID());
 for(const c of scenario.communities){assert.equal(layout.blocks[c.id].length,4);assert.equal(demoCommunityAtPoint(c.latitude,c.longitude,scenario),c.id);}
 for(const p of scenario.people){assert.equal(demoCommunityAtPoint(p.home.latitude,p.home.longitude,scenario),p.communityId);assert.ok([...layout.households,...layout.slots.map(s=>s.home)].some(h=>h.buildingId===p.home.buildingId));if(!p.positionUnknown){assert.equal(demoCommunityAtPoint(p.position.latitude,p.position.longitude,scenario),p.communityId);}}
 assert.equal(demoCommunityAtPoint(35.018,135.763,scenario),null);
});

function inside(p,g){
 const ring=r=>r.reduce((hit,a,i)=>{const b=r[(i+1)%r.length];return (a[1]>p[1])!==(b[1]>p[1])&&p[0]<(b[0]-a[0])*(p[1]-a[1])/(b[1]-a[1])+a[0]?!hit:hit;},false);
 return (g.type==='MultiPolygon'?g.coordinates:[g.coordinates]).some(poly=>ring(poly[0])&&!poly.slice(1).some(ring));
}
const point=p=>[p.longitude,p.latitude],dir=new URL('../../demo/data/cities/26100/buildings/',import.meta.url);
const buildings=readdirSync(dir).filter(n=>/^523546.*\.gzip$/.test(n)).flatMap(n=>JSON.parse(gunzipSync(readFileSync(new URL(n,dir)))).buildings);
test('128 people use 96 distinct home buildings with small buildings limited to one person',()=>{
 assert.equal(layout.people.length,96);assert.equal(layout.households.length,64);assert.equal(new Set(layout.people.map(p=>p.home.buildingId)).size,64);
 const sizes=Object.values(Object.groupBy(layout.people,p=>p.home.householdId)).map(p=>p.length);
 assert.equal(sizes.filter(n=>n===1).length+32,80);assert.equal(sizes.filter(n=>n===3).length,16);
 const footprint=g=>{const ring=r=>{const [ox,oy]=r[0],q=r.map(([x,y])=>[x-ox,y-oy]);return Math.abs(q.reduce((s,p,i)=>{const n=q[(i+1)%q.length];return s+p[0]*n[1]-n[0]*p[1];},0))*.5*91000*111195;};return (g.type==='MultiPolygon'?g.coordinates:[g.coordinates]).reduce((s,p)=>s+ring(p[0])-p.slice(1).reduce((t,h)=>t+ring(h),0),0);};
 const groups=Object.groupBy([...layout.people,...layout.slots.slice(0,32)],p=>p.home.plateauId);assert.equal(Object.keys(groups).length,96);
 for(const [id,rows] of Object.entries(groups)){const area=footprint(buildings.find(b=>b.id===id).geometry);assert.ok(area>=25,id);assert.ok(rows.length<=(area<100?1:3),id);}
 for(const p of [...layout.people,...layout.slots]){const b=buildings.find(b=>b.id===p.home.plateauId);assert.ok(b);assert.ok(inside(point(p.home),b.geometry),p.home.plateauId);assert.ok(p.home.householdId);}
});
test('current positions have the approved indoor/road balance and distinct attendee positions',()=>{
 const indoor=p=>buildings.some(b=>inside(point(p),b.geometry));
 const known=layout.people.filter(p=>!p.positionUnknown);assert.equal(known.length,94);assert.equal(known.filter(p=>indoor(p.position)).length,66);
 assert.equal(layout.slots.slice(0,32).filter(indoor).length,24);assert.equal(new Set(layout.slots.map(p=>`${p.latitude}:${p.longitude}`)).size,64);assert.equal(indoor(layout.blockedRoadPoint),false);
});
test('central six known SOS/no-response people plus two separate unknown examples',()=>{
 const start=Date.UTC(2026,10,6),s=createVenueScenario(crypto.randomUUID(),start),unknown=s.people.filter(p=>p.positionUnknown);
 assert.equal(s.people.filter(p=>p.safety==='SAFE').length,116);assert.equal(s.people.filter(p=>p.safety!=='SAFE'&&!p.positionUnknown&&!p.initialRescue).length,10);assert.equal(unknown.length,2);
 assert.deepEqual(unknown.map(p=>p.lastKnownPosition.acquiredAt),[start-7*86400000,start-3*86400000]);assert.ok(unknown.every(p=>p.position===null));
});

test('32 attendee homes are distinct, distributed across four blocks, and indoor locations are 90% home',()=>{
 const central=new Set(layout.people.filter(p=>p.communityId==='demo-central').map(p=>p.home.plateauId));
 const attendees=layout.slots.slice(0,32),homes=Object.groupBy(attendees,p=>p.home.plateauId);
 assert.equal(Object.keys(homes).length,32);assert.ok(Object.keys(homes).every(id=>!central.has(id)));
 assert.ok(Object.values(homes).every(rows=>rows.length===1));
 const locationBuilding=p=>buildings.filter(b=>inside(point(p.position||p),b.geometry));
 const classify=rows=>rows.filter(p=>locationBuilding(p).length).reduce((counts,p)=>{counts[locationBuilding(p).some(b=>b.id===p.home.plateauId)?'home':'away']++;return counts;},{home:0,away:0});
 assert.deepEqual(classify(layout.people.filter(p=>!p.positionUnknown)),{home:59,away:7});
 assert.deepEqual(classify(attendees),{home:22,away:2});
 const attendeeRoads=attendees.filter(p=>!locationBuilding(p).length);
 assert.deepEqual(layout.blocks['demo-central'].map(r=>attendeeRoads.filter(p=>inside(point(p.home),{type:'Polygon',coordinates:[r]})).length),[2,2,2,2]);
 assert.deepEqual(classify([...layout.people.filter(p=>!p.positionUnknown),...attendees]),{home:81,away:9});
 const counts=layout.communities.map(c=>layout.people.filter(p=>p.communityId===c.id).length+attendees.filter(p=>p.communityId===c.id).length);
 assert.deepEqual(counts,[40,48,40]);
 const boxes=layout.communities.map(c=>c.boundary);
 assert.deepEqual(boxes[0][1],boxes[1][0]);assert.deepEqual(boxes[0][2],boxes[1][3]);assert.deepEqual(boxes[1][1],boxes[2][0]);assert.deepEqual(boxes[1][2],boxes[2][3]);
 for(const c of layout.communities){const people=[...layout.people,...attendees].filter(p=>p.communityId===c.id);const byBlock=layout.blocks[c.id].map(r=>people.filter(p=>inside(point(p.home),{type:'Polygon',coordinates:[r]})).length);assert.deepEqual(byBlock,c.id==='demo-central'?[12,12,12,12]:[10,10,10,10]);}
});

test('initial report locations match their road/building category and each neighborhood',()=>{
 const scenario=createVenueScenario(crypto.randomUUID());
 for(const [category,p] of Object.entries(layout.reportPoints)){
  assert.equal(demoCommunityAtPoint(p.latitude,p.longitude,scenario),p.communityId);
  const inBuilding=buildings.some(b=>inside(point(p),b.geometry));
  assert.equal(inBuilding,category!=='ROAD_BLOCKED');
  assert.ok(!layout.people.some(person=>person.position?.latitude===p.latitude&&person.position?.longitude===p.longitude));
 }
 assert.equal(new Set(Object.values(layout.reportPoints).map(p=>p.communityId)).size,3);
});

test('central impact roles use separate interior home buildings away from the neighborhood perimeter',()=>{
 const ring=layout.communities.find(c=>c.id==='demo-central').boundary;
 const edgeDistance=p=>Math.min(...ring.map((a,i)=>{const b=ring[(i+1)%ring.length],v=[(b[0]-a[0])*91000,(b[1]-a[1])*111195],q=[(p.longitude-a[0])*91000,(p.latitude-a[1])*111195],t=Math.max(0,Math.min(1,(q[0]*v[0]+q[1]*v[1])/(v[0]**2+v[1]**2)));return Math.hypot(q[0]-t*v[0],q[1]-t*v[1]);}));
 const people=layout.people.filter(p=>p.communityId==='demo-central'&&p.observations.impact);
 assert.equal(people.length,3);assert.equal(new Set(people.map(p=>p.home.plateauId)).size,3);
 for(const p of people){assert.ok(edgeDistance(p.position)>=20);assert.ok(inside(point(p.position),buildings.find(b=>b.id===p.home.plateauId).geometry));}
 for(let i=0;i<people.length;i++)for(let j=i+1;j<people.length;j++)assert.ok(Math.hypot((people[i].position.longitude-people[j].position.longitude)*91000,(people[i].position.latitude-people[j].position.latitude)*111195)>40);
});

test('four rescued former-SOS residents add one distinct home to each central block',()=>{
 const central=createVenueScenario(crypto.randomUUID()).people.filter(p=>p.communityId==='demo-central');
 assert.equal(central.length,52);
 for(const ring of layout.blocks['demo-central']){
  const people=central.filter(p=>inside(point(p.home),{type:'Polygon',coordinates:[ring]}));
  assert.equal(people.length,13);assert.equal(people.filter(p=>p.initialRescue&&p.safety==='SOS').length,1);
 }
 assert.equal(new Set(central.map(p=>p.home.plateauId)).size,52);
});
