import test from 'node:test';
import assert from 'node:assert/strict';
import {gzipSync} from 'node:zlib';
import {createHash} from 'node:crypto';
import {ParcelAddressRepository,parcelAddressChoices} from '../web/parcel-addresses.mjs';

test('parcel candidates retain ambiguity and do not become verified home addresses',()=>{
 const source={id:'land',version:'2024'},link={status:'multiple-parcels',candidates:[{parcelId:'1',address:'京都市A町1'},{parcelId:'2',address:'京都市A町2'},{parcelId:'3',address:'京都市A町1'}]};
 const rows=parcelAddressChoices(link,source);assert.equal(rows.length,2);assert.equal(rows[0].status,'multiple-parcels');assert.equal(rows[0].source,source);assert.equal(rows[0].precision,undefined);assert.equal(link.candidates.length,3);
 assert.deepEqual(parcelAddressChoices({status:'partial',candidates:[{parcelId:'road',address:'京都市A町道',numbered:false}]},source),[]);
});
test('loads only containing meshes, verifies hashes, and caches successful reads',async()=>{
 const data=gzipSync(JSON.stringify({schemaVersion:1,mesh:'1',buildings:{b:{status:'single-parcel',candidates:[{parcelId:'p',address:'京都市A町1'}]}}}));
 const index={schemaVersion:1,source:{id:'land',version:'2024'},chunks:[{mesh:'1',bbox:[135,35,136,36],file:'1.gz',sha256:createHash('sha256').update(data).digest('hex')}]};let calls=0;
 const repo=new ParcelAddressRepository(async url=>{calls++;return url.endsWith('index.json')?Response.json(index):new Response(data);},'https://example.test/');
 assert.deepEqual(await repo.choices('b',35.2,139,'13103'),[]);assert.equal(calls,0);
 assert.deepEqual(await repo.choices('b',34,135),[]);assert.equal(calls,1);
 assert.equal((await repo.choices('b',35.5,135.5))[0].address,'京都市A町1');assert.equal(calls,2);
 assert.equal((await repo.choices('b',35.5,135.5)).length,1);assert.equal(calls,2);
 assert.deepEqual(await repo.choices('unknown',35.5,135.5),[]);
 const invalid=new ParcelAddressRepository(async url=>url.endsWith('index.json')?Response.json(index):new Response('broken'),'https://example.test/');
 await assert.rejects(invalid.choices('b',35.5,135.5),/更新/);
});
