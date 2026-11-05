import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {gunzipSync} from 'node:zlib';
import {buildingAtRegisteredPlace,registeredBuildingForPerson} from '../web/building-details.mjs';
import {venuePlaces} from '../web/venue-demo.mjs';
import {createVenueScenario} from '../../cloud-sync/src/venue-scenario.mjs';
import {CITY_DATA} from '../web/city-addresses.mjs';
import {buildingsAt} from '../web/building-picker.mjs';

const assets=new URL('../../android/app/src/main/assets/kyoto-building-assessment/',import.meta.url);
const catalogue=JSON.parse(await readFile(new URL('../../cloud-sync/src/venue-buildings.json',import.meta.url)));
const sources=await Promise.all([...new Set(['52354610.json.gzip',...catalogue.homes.map(h=>h.attributeSource.asset)])].map(async asset=>JSON.parse(gunzipSync(await readFile(new URL(asset,assets))))));
const source={buildings:sources.flatMap(s=>s.buildings)};
const placeFor=h=>({buildingId:h.buildingId,label:h.address,latitude:h.latitude,longitude:h.longitude,locationReference:{plateauId:h.plateauId,dataset:'plateau-26100-kyoto-shi-2025'}});

test('registered building details preserve read errors, exact identity, and source missingness',async t=>{
 const originalFetch=globalThis.fetch;
 t.after(()=>{globalThis.fetch=originalFetch;});
 // Preserve the old registered-home regression independently of new-demo allocation.
 const home={buildingId:'f383d3d7-a2dd-4a17-bf4d-02fd0ce0c27f',plateauId:'bldg_f383d3d7-a2dd-4a17-bf4d-02fd0ce0c27f',address:'京都府京都市中京区少将井御旅町付近',latitude:35.015627382769296,longitude:135.76033315936462};
 globalThis.fetch=async()=>new Response('',{status:503});
 await assert.rejects(buildingAtRegisteredPlace(placeFor(home)),/取得/);
 const packagedFetch=async url=>{
  assert.ok(String(url).startsWith(CITY_DATA+'26100/buildings/'));
  return new Response(await readFile(new URL(String(url).slice((CITY_DATA+'26100/buildings/').length),assets)));
 };
 globalThis.fetch=async url=>String(url).endsWith('index.json')?packagedFetch(url):new Response('',{status:503});
 await assert.rejects(buildingAtRegisteredPlace(placeFor(home)),/取得/,'chunk download failure must not become unknown source attributes');
 globalThis.fetch=async url=>String(url).endsWith('index.json')?packagedFetch(url):new Response('corrupted download');
 await assert.rejects(buildingAtRegisteredPlace(placeFor(home)),/更新/,'checksum failure must not become an unmatched building');
 globalThis.fetch=packagedFetch;
 const found=await buildingAtRegisteredPlace(placeFor(home));
 assert.equal(found.attributes.year,2012);
 assert.equal(found.attributes.floors,3);
 assert.equal(found.attributes.structureCode,'603');
 const movedPin={...placeFor(home),longitude:found.bbox[0]-0.00001};
 assert.ok((await buildingsAt(home.latitude,home.longitude,'26100')).some(b=>b.id===home.plateauId));
 assert.ok(!(await buildingsAt(movedPin.latitude,movedPin.longitude,'26100')).some(b=>b.id===home.plateauId),'map selection must still require the pin inside the footprint');
 const exact=await buildingAtRegisteredPlace(movedPin);
 assert.equal(exact.id,home.plateauId);
 assert.equal(exact.attributes.year,2012,'an explicit building ID survives a pin just outside its footprint');
 assert.equal(found.attributesStatus,'available');
 const absent=await buildingAtRegisteredPlace({...placeFor(home),locationReference:{plateauId:'bldg_not-in-this-dataset'}});
 assert.equal(absent.attributesStatus,'unmatched');
 assert.deepEqual(absent.attributes,{});
 const unlinked=await buildingAtRegisteredPlace({...placeFor(home),locationReference:{plateauId:null}});
 assert.equal(unlinked.attributesStatus,'unlinked');
 const missing=await buildingAtRegisteredPlace(placeFor(catalogue.homes.find(h=>h.attributes.year===null)));
 assert.equal(missing.attributesStatus,'source-missing');
 assert.equal(missing.attributes.year,null);

 const scenario=createVenueScenario('building-details-regression');
 const rows=venuePlaces({groupId:'demo',sessionId:scenario.sessionId,startedAt:1,scenario,placements:[],positionMode:'ASSIGNED'});
 let years=0,floors=0,structures=0;
 for(const person of scenario.people){
  const place=registeredBuildingForPerson(rows,person.deviceId);
  assert.equal(place.locationReference.plateauId,person.home.plateauId);
  const details=await buildingAtRegisteredPlace(place);
  const original=source.buildings.find(b=>b.id===person.home.plateauId);
  assert.ok(original,'scenario building is in the packaged fixture');
  assert.deepEqual(details.attributes,original.attributes);
  years+=Number(!!details.attributes.year);floors+=Number(!!details.attributes.floors);structures+=Number(!!details.attributes.structureCode);
 }
 const expected=scenario.people.map(p=>source.buildings.find(b=>b.id===p.home.plateauId).attributes);
 assert.deepEqual({years,floors,structures},{years:expected.filter(a=>a.year).length,floors:expected.filter(a=>a.floors).length,structures:expected.filter(a=>a.structureCode).length});
});
