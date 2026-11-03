import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {gunzipSync} from 'node:zlib';
import {demoMapMunicipality,mapMunicipality} from '../web/municipalities.mjs';
import {cityDownloadPlan} from '../web/city-offline.mjs';
import {localGeocodeCandidates,CITY_DATA} from '../web/city-addresses.mjs';

test('demo selects its own map without changing the ordinary group municipality',async()=>{
 const ordinary={code:'22205'},store={read:async(_,key)=>key.startsWith('venue:')?{positionMode:'KYOTO',closed:false}:ordinary};
 assert.equal((await mapMunicipality(store,'g')).code,'26100');
 assert.equal(demoMapMunicipality({positionMode:'ACTUAL',closed:false}).code,'13103');
 assert.equal(demoMapMunicipality({positionMode:'KYOTO',closed:true}),null);
 assert.equal(ordinary.code,'22205');
 assert.equal(demoMapMunicipality(null),null);
});

test('Toranomon package includes verified building tiles and searchable venue/address',async t=>{
 const old=globalThis.fetch;t.after(()=>globalThis.fetch=old);
 globalThis.fetch=async url=>{assert.ok(String(url).startsWith(CITY_DATA));return new Response(await readFile('demo/data/cities/'+String(url).slice(CITY_DATA.length)));};
 const plan=await cityDownloadPlan('13103');
 assert.ok(plan.files.some(f=>f.url.endsWith('13103/risk/index.json')));
 assert.ok(plan.files.some(f=>f.url.includes('13103/buildings/')&&f.sha256));
 for(const query of ['虎ノ門ヒルズ','港区虎ノ門1丁目23番1号']){
  const found=await localGeocodeCandidates(query,'13103');assert.ok(found.length,query);
  assert.ok(found.some(p=>Math.abs(p.latitude-35.6665)<.015&&Math.abs(p.longitude-139.7495)<.015),query);
 }
 const index=JSON.parse(await readFile('demo/data/cities/13103/buildings/index.json'));
 let total=0,unknown=0,assumed=0;const ids=new Set();
 for(const chunk of index.chunks){const {buildings}=JSON.parse(gunzipSync(await readFile('demo/data/cities/13103/buildings/'+chunk.file)));for(const b of buildings){assert.ok(!ids.has(b.id));ids.add(b.id);total++;if(b.status==='unknown'){unknown++;assert.equal(b.profile,null);}else {assumed++;assert.equal(b.status,'assumed');assert.equal(b.profile,'highrise-zero');assert.ok(b.attributes.floors>=16);}}}
 assert.equal(total,5033);assert.equal(unknown,4901);assert.equal(assumed,132);
});
