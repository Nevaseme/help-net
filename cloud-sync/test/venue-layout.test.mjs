import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createVenueScenario} from '../src/venue-scenario.mjs';
import {demoCommunityAtPoint} from '../../lab/web/community-overview.mjs';
import layout from '../src/venue-compact-layout.json' with {type:'json'};
import {demoCommunities as former} from '../src/venue-layout.mjs';
import {readFileSync,readdirSync} from 'node:fs';
import {gunzipSync} from 'node:zlib';
test('smaller neighborhood boundaries retain reviewed homes and valid simulated phone locations',()=>{
 const scenario=createVenueScenario(crypto.randomUUID());
 const area=ring=>Math.abs(ring.reduce((s,p,i)=>{const q=ring[(i+1)%ring.length];return s+p[0]*q[1]-q[0]*p[1];},0))/2;
 const extent=ring=>[Math.max(...ring.map(p=>p[0]))-Math.min(...ring.map(p=>p[0])),Math.max(...ring.map(p=>p[1]))-Math.min(...ring.map(p=>p[1]))];
 for(const c of scenario.communities){const old=former.find(x=>x.id===c.id);assert.ok(area(c.boundary)<=area(old.boundary)/4);extent(c.boundary).forEach((v,i)=>assert.ok(v<=extent(old.boundary)[i]/2+1e-10+1e-10));assert.equal(demoCommunityAtPoint(c.latitude,c.longitude,scenario),c.id);}
 for(const p of scenario.people){assert.equal(demoCommunityAtPoint(p.home.latitude,p.home.longitude,scenario),p.communityId);assert.ok(layout.households.some(h=>h.buildingId===p.home.buildingId));if(!p.positionUnknown){assert.equal(demoCommunityAtPoint(p.position.latitude,p.position.longitude,scenario),p.communityId);}}
 assert.equal(demoCommunityAtPoint(35.018,135.763,scenario),null);
});

function inside(p,g){
 const ring=r=>r.reduce((hit,a,i)=>{const b=r[(i+1)%r.length];return (a[1]>p[1])!==(b[1]>p[1])&&p[0]<(b[0]-a[0])*(p[1]-a[1])/(b[1]-a[1])+a[0]?!hit:hit;},false);
 return (g.type==='MultiPolygon'?g.coordinates:[g.coordinates]).some(poly=>ring(poly[0])&&!poly.slice(1).some(ring));
}
const point=p=>[p.longitude,p.latitude],dir=new URL('../../demo/data/cities/26100/buildings/',import.meta.url);
const buildings=readdirSync(dir).filter(n=>/^523546.*\.gzip$/.test(n)).flatMap(n=>JSON.parse(gunzipSync(readFileSync(new URL(n,dir)))).buildings);
test('96 samples occupy all 47 included buildings and produce 80 single households at 32 attendees',()=>{
 assert.equal(layout.people.length,96);assert.equal(layout.households.length,64);assert.equal(new Set(layout.people.map(p=>p.home.buildingId)).size,47);
 const sizes=Object.values(Object.groupBy(layout.people,p=>p.home.householdId)).map(p=>p.length);
 assert.equal(sizes.filter(n=>n===1).length+32,80);assert.equal(sizes.filter(n=>n===3).length,16);
 for(const c of layout.communities){const [w,s]=c.boundary[0],[e,n]=c.boundary[2];const included=buildings.filter(b=>w<=(b.bbox[0]+b.bbox[2])/2&&(b.bbox[0]+b.bbox[2])/2<e&&s<=(b.bbox[1]+b.bbox[3])/2&&(b.bbox[1]+b.bbox[3])/2<n);for(const b of included)assert.ok(layout.people.some(p=>p.home.plateauId===b.id),b.id);}
 for(const p of [...layout.people,...layout.slots]){const b=buildings.find(b=>b.id===p.home.plateauId);assert.ok(b);assert.ok(inside(point(p.home),b.geometry),p.home.plateauId);assert.ok(p.home.householdId);}
});
test('current positions have the approved indoor/road balance and distinct attendee positions',()=>{
 const indoor=p=>buildings.some(b=>inside(point(p),b.geometry));
 const known=layout.people.filter(p=>!p.positionUnknown);assert.equal(known.length,94);assert.equal(known.filter(p=>indoor(p.position)).length,66);
 assert.equal(layout.slots.slice(0,32).filter(indoor).length,24);assert.equal(new Set(layout.slots.map(p=>`${p.latitude}:${p.longitude}`)).size,64);assert.equal(indoor(layout.blockedRoadPoint),false);
});
test('six known SOS/no-response people plus two unknown examples retain old fixes',()=>{
 const start=Date.UTC(2026,10,6),s=createVenueScenario(crypto.randomUUID(),start),unknown=s.people.filter(p=>p.positionUnknown);
 assert.equal(s.people.filter(p=>p.safety==='SAFE').length,88);assert.equal(s.people.filter(p=>p.safety!=='SAFE'&&!p.positionUnknown).length,6);assert.equal(unknown.length,2);
 assert.deepEqual(unknown.map(p=>p.lastKnownPosition.acquiredAt),[start-7*86400000,start-3*86400000]);assert.ok(unknown.every(p=>p.position===null));
});

test('32 attendee homes span every central building and indoor locations are 90% home, 10% visiting',()=>{
 const central=new Set(layout.people.filter(p=>p.communityId==='demo-central').map(p=>p.home.plateauId));
 const attendees=layout.slots.slice(0,32),homes=Object.groupBy(attendees,p=>p.home.plateauId);
 assert.deepEqual(new Set(Object.keys(homes)),central);
 assert.ok(Object.values(homes).every(rows=>rows.length<=3));
 const locationBuilding=p=>buildings.filter(b=>inside(point(p.position||p),b.geometry));
 const classify=rows=>rows.filter(p=>locationBuilding(p).length).reduce((counts,p)=>{counts[locationBuilding(p).some(b=>b.id===p.home.plateauId)?'home':'away']++;return counts;},{home:0,away:0});
 assert.deepEqual(classify(layout.people.filter(p=>!p.positionUnknown)),{home:59,away:7});
 assert.deepEqual(classify(attendees),{home:22,away:2});
 assert.deepEqual(classify([...layout.people.filter(p=>!p.positionUnknown),...attendees]),{home:81,away:9});
 const counts=layout.communities.map(c=>layout.people.filter(p=>p.communityId===c.id).length+attendees.filter(p=>p.communityId===c.id).length);
 assert.deepEqual(counts,[40,48,40]);
 const boxes=layout.communities.map(c=>c.boundary);
 assert.equal(boxes[0][1][0],boxes[1][0][0]);assert.equal(boxes[1][1][0],boxes[2][0][0]);
 for(const box of boxes){assert.equal(box[0][1],boxes[0][0][1]);assert.equal(box[2][1],boxes[0][2][1]);assert.ok(Math.abs((box[1][0]-box[0][0])-.0007)<1e-10);}
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
