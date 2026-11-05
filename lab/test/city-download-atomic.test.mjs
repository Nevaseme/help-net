import test from 'node:test';import assert from 'node:assert/strict';
import {loadCityPackage} from '../web/city-addresses.mjs';
import {gzipSync} from 'node:zlib';
import {CityDownload,hasCurrentCityFiles,fetchCityFile} from '../web/city-offline.mjs';
test('old Kyoto downloads remain usable but require an update for parcel searches',()=>{
 const old={urls:['https://nevaseme.github.io/safety_confirm/data/cities/26100/buildings/index.json']};
 assert.equal(hasCurrentCityFiles('26100',old),false);
 assert.equal(hasCurrentCityFiles('26100',{urls:[...old.urls,...['parcel-addresses','parcel-search'].map(p=>'https://nevaseme.github.io/safety_confirm/data/cities/26100/'+p+'/index.json')]}),true);
 assert.equal(old.urls.length,1);
});
test('failed city update leaves the previous complete package usable',async t=>{
 const buckets=new Map();const original=globalThis.caches,fetcher=globalThis.fetch;
 globalThis.caches={keys:async()=>[...buckets.keys()],delete:async k=>buckets.delete(k),open:async k=>{if(!buckets.has(k))buckets.set(k,new Map());const b=buckets.get(k);return {match:async u=>b.get(String(u))?.clone(),put:async(u,r)=>{b.set(String(u),r.clone());},delete:async u=>b.delete(String(u))};}};
 t.after(()=>{globalThis.caches=original;globalThis.fetch=fetcher;});
 const plan={code:'13103',name:'港区',bytes:3,files:[{url:'https://test.invalid/a'},{url:'https://test.invalid/b'}]};
 globalThis.fetch=async()=>new Response('old');const d=new CityDownload();await d.download(plan);assert.ok(await d.status(plan.code));
 globalThis.fetch=async url=>{if(url.endsWith('/b'))throw Error('network failed');return new Response('new');};
 await assert.rejects(()=>d.download(plan));assert.ok(await d.status(plan.code),'old complete version must remain');
});

test('completed city download refreshes the search package already in memory',async t=>{
 const buckets=new Map(),original=globalThis.caches,fetcher=globalThis.fetch;
 globalThis.caches={keys:async()=>[...buckets.keys()],delete:async k=>buckets.delete(k),open:async k=>{if(!buckets.has(k))buckets.set(k,new Map());const b=buckets.get(k);return {match:async u=>b.get(String(u))?.clone(),put:async(u,r)=>{b.set(String(u),r.clone());},delete:async u=>b.delete(String(u))};}};
 t.after(()=>{globalThis.caches=original;globalThis.fetch=fetcher;});
 let version=1;globalThis.fetch=async url=>url.endsWith('city.json')?Response.json({addresses:{path:'13103/addresses.json.gz'}}):new Response(gzipSync(JSON.stringify({rows:[['test','施設'+version,139,35,'poi']]})));
 assert.equal((await loadCityPackage('13103',true)).addresses.rows[0][1],'施設1');version=2;
 await new CityDownload().download({code:'13103',name:'港区',bytes:1,files:[{url:'https://nevaseme.github.io/safety_confirm/data/cities/13103/city.json'},{url:'https://nevaseme.github.io/safety_confirm/data/cities/13103/addresses.json.gz'}]});
 assert.equal((await loadCityPackage('13103')).addresses.rows[0][1],'施設2');
});

test('transient download failures retry, while explicit pause stops retries',async t=>{
 const original=globalThis.fetch;t.after(()=>{globalThis.fetch=original;});let calls=0;
 globalThis.fetch=async()=>{if(++calls===1)throw TypeError('Failed to fetch');return new Response('recovered');};
 assert.equal(await (await fetchCityFile('https://test.invalid/a',new AbortController().signal)).text(),'recovered');assert.equal(calls,2);
 const stop=new AbortController();calls=0;globalThis.fetch=async()=>{calls++;stop.abort();throw TypeError('Failed to fetch');};
 await assert.rejects(()=>fetchCityFile('https://test.invalid/a',stop.signal),{name:'AbortError'});assert.equal(calls,1);
});
