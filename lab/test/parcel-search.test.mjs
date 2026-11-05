import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {gunzipSync} from 'node:zlib';
import {ParcelSearch,mergeParcelCandidates} from '../web/parcel-search.mjs';
import {addressRows,normalizeAddress,localGeocodeCandidates,CITY_DATA} from '../web/city-addresses.mjs';

const row=(id,ward,n=203)=>[id,`京都府京都市${ward}讃州寺町${n}`,135.77,35.01,'parcel-area',{matchLevel:'parcel',source:'fixture',sourceVersion:'unknown'}];
test('exact parcel search keeps ward ambiguity, normalizes notation, and never guesses a number',async()=>{
 let reads=0;
 const search=new ParcelSearch(async()=>({schemaVersion:1,towns:[['京都府京都市左京区讃州寺町','a'],['京都府京都市上京区讃州寺町','b']],chunks:[{id:'a'},{id:'b'}]}),async c=>{reads++;return {schemaVersion:1,rows:[row(c.id,c.id==='a'?'左京区':'上京区'),row('road','左京区','道路-1')]};});
 for(const q of ['左京区讃州寺町203','京都市左京区讃州寺町２０３番地','京都府京都市左京区讃州寺町203','京都市左京区仁王門通新麩屋町西入ル讃州寺町203'])assert.deepEqual((await search.search(q)).map(r=>r.id),['a'],q);
 assert.equal(reads,1);
 assert.equal((await search.search('讃州寺町203')).length,2);
 for(const q of ['左京区讃州寺町999999','下京区讃州寺町203','大阪府大阪市左京区讃州寺町203','京都府宇治市讃州寺町203'])assert.equal((await search.search(q)).length,0,q);
 assert.equal(reads,2);
 assert.equal((await search.search('左京区讃州寺町道路-1'))[0].id,'road');
 const parcel=(await search.search('左京区讃州寺町203'))[0];
 const coarse=addressRows([['old',parcel.address,135,35,'block',{source:'ISJ'}]]);
 assert.equal(mergeParcelCandidates(coarse,[parcel])[0].id,'a');
 const precise=addressRows([['trusted',parcel.address,135,35,'residential']]);
 assert.deepEqual(mergeParcelCandidates(precise,[parcel]).map(r=>r.id),['trusted']);
 const noPrefecture=addressRows([['trusted','京都市左京区讃州寺町203',135,35,'residential']]);
 assert.deepEqual(mergeParcelCandidates(noPrefecture,[parcel]).map(r=>r.id),['trusted']);
});

test('full Kyoto package is bounded and warm exact searches finish within 500 ms',async t=>{
 const root=new URL('../../demo/data/cities/26100/parcel-search/',import.meta.url);
 const index=JSON.parse(await readFile(new URL('index.json',root),'utf8'));let reads=0;
 const search=new ParcelSearch(async()=>index,async c=>{reads++;return JSON.parse(gunzipSync(await readFile(new URL(c.file,root))));});
 assert.equal(index.rowCount,718575);assert.equal(index.chunks.length,256);assert.ok(Math.max(...index.chunks.map(c=>c.count))<10000);
 const hits=await search.search('左京区讃州寺町203');
 assert.ok(hits.length);assert.equal(hits[0].precision,'parcel-area');assert.equal(hits[0].source,'mlit-kyoto-parcel-prototype-2025');
 assert.equal(hits[0].longitude,135.77448339);assert.equal(hits[0].latitude,35.01152586);
 assert.equal(reads,1);
 const start=performance.now();for(let i=0;i<100;i++)await search.search('京都市左京区讃州寺町２０３番地');
 const elapsed=(performance.now()-start)/100;t.diagnostic(`warm search ${elapsed.toFixed(3)} ms; ${reads} of 256 chunks loaded`);assert.ok(elapsed<500);
 assert.equal((await search.search('左京区讃州寺町999999')).length,0);
 assert.ok((await search.search('右京区京北下町島﨑20-1')).some(r=>r.id==='kyoto-mlit2025:701154'));
 // Every indexed town's normalization is identical to the runtime contract.
 for(const [town] of index.towns)assert.equal(normalizeAddress(town),town);
});

test('integrated geocoder upgrades coarse ISJ points and keeps the town fallback honest',async t=>{
 const root=new URL('../../demo/data/cities/',import.meta.url);
 if(!existsSync(new URL('26100/parcel-search/index.json',root))){t.skip('Published parcel search assets not present');return;}
 const original=globalThis.fetch;const requested=[];let offlineChunk=true;
 globalThis.fetch=async url=>{
  assert.ok(String(url).startsWith(CITY_DATA));const relative=String(url).slice(CITY_DATA.length);requested.push(relative);
  if(offlineChunk&&relative.includes('parcel-search/chunks/'))throw Error('offline partial download');
  return new Response(await readFile(new URL(relative,root)));
 };
 t.after(()=>{globalThis.fetch=original;});
 const fallback=await localGeocodeCandidates('左京区讃州寺町２０３番地','26100');
 assert.ok(fallback.length);assert.equal(fallback[0].precision,'block');assert.equal(fallback[0].source,'ISJ');
 offlineChunk=false;
 const hits=await localGeocodeCandidates('左京区讃州寺町２０３番地','26100');
 assert.equal(hits.length,1);assert.equal(hits[0].precision,'parcel-area');assert.equal(hits[0].matchLevel,'parcel');
 assert.equal(hits[0].source,'mlit-kyoto-parcel-prototype-2025');
 const start=performance.now();await localGeocodeCandidates('左京区讃州寺町203','26100');
 const elapsed=performance.now()-start;t.diagnostic(`integrated warm geocoder ${elapsed.toFixed(2)} ms`);assert.ok(elapsed<500);
 const absent=await localGeocodeCandidates('左京区讃州寺町999999','26100');
 assert.ok(absent.length);assert.ok(absent.every(r=>r.precision==='town'&&!r.address.includes('999999')));
 assert.equal((await localGeocodeCandidates('京都府宇治市讃州寺町203','26100')).length,0);
 assert.equal(requested.filter(path=>path.includes('parcel-search/chunks/')).length,2);
});

test('offline upgrade retains the previous address cache without inventing parcel precision',async t=>{
 const previousFetch=globalThis.fetch,previousCaches=globalThis.caches;
 const legacy={city:{code:'26100'},addresses:{rows:[['legacy','京都府京都市左京区讃州寺町203',135,35,'block']]},places:{rows:[]}};
 globalThis.fetch=async()=>{throw Error('offline');};
 globalThis.caches={keys:async()=>[],open:async()=>({match:async key=>String(key).endsWith('/search-package-v2')?Response.json(legacy):undefined})};
 t.after(()=>{globalThis.fetch=previousFetch;if(previousCaches===undefined)delete globalThis.caches;else globalThis.caches=previousCaches;});
 const {loadCityPackage}=await import('../web/city-addresses.mjs?legacy-offline-test');
 assert.deepEqual(await loadCityPackage('26100'),legacy);
});
