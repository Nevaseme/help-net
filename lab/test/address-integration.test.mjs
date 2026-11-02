import test from 'node:test';
import assert from 'node:assert/strict';
import {searchAddresses,resolveAddress,normalizeAddress} from '../web/city-addresses.mjs';
const rows=[
 ['town','京都府京都市左京区讃州寺町',135.77466,35.010905,'town'],
 ['203','京都府京都市左京区讃州寺町203',135.774524,35.011046,'block',{matchLevel:'parcel',source:'ISJ',sourceVersion:'24.0a'}],
 ['hall','京都府京都市中京区上本能寺前町488',135.767742,35.011497,'block'],
 ['tower','京都タワー',135.759594,34.987615,'poi'],
 ['roppongi','東京都港区六本木六丁目10-1',139.7292,35.6602,'residential'],
 ['other','静岡県熱海市中央町1-1',139.072095,35.096327,'residential'],
 ['a','中央公園',135.76,35.01,'poi'],['b','中央公園',139.73,35.66,'poi'],
];
test('street number recognition does not upgrade a shared block coordinate to building precision',()=>{
 const r=resolveAddress(rows,'左京区讃州寺町２０３'); assert.equal(r.id,'203'); assert.equal(r.matchLevel,'parcel'); assert.equal(r.precision,'block');assert.equal(r.source,'ISJ');
});
test('Kyoto street notation resolves to town and number',()=>assert.equal(resolveAddress(rows,'京都市中京区寺町通御池上る上本能寺前町488番地')?.id,'hall'));
test('unknown number returns town label rather than invented exact address',()=>{const r=resolveAddress(rows,'左京区讃州寺町99999');assert.equal(r?.id,'town');assert.equal(r.address,'京都府京都市左京区讃州寺町');assert.equal(r.matchLevel,'town');});
test('ambiguity is preserved instead of dropping same-name places or selecting first',()=>{assert.equal(searchAddresses(rows,'中央公園').length,2);assert.equal(resolveAddress(rows,'中央公園'),null);});
test('facility long vowel is not replaced with a hyphen',()=>assert.equal(normalizeAddress('京都タワー'),'京都タワー'));
test('numeric chome and explicit other city are resolved',()=>{assert.equal(resolveAddress(rows,'東京都港区六本木6-10-1')?.id,'roppongi');assert.equal(resolveAddress(rows,'熱海市中央町１番１号')?.id,'other');});

import {readFileSync} from 'node:fs';
const shared=JSON.parse(readFileSync(new URL('../../shared/address-search-cases.json',import.meta.url),'utf8'));
test('Android and PWA share the normalization and selection contract',()=>{for(const [input,expected] of shared.normalization)assert.equal(normalizeAddress(input),expected);for(const c of shared.search)assert.deepEqual(searchAddresses(shared.rows,c.query).map(r=>r.id).sort(),c.ids.slice().sort(),c.query);});
