import test from 'node:test';import assert from 'node:assert/strict';import * as geo from '../web/city-addresses.mjs';
const rows=[['a','京都府京都市左京区試験町',135.7,35.1,'town'],['b','京都府京都市左京区別町',135.8,35.2,'town'],['c','京都府京都市左京区試験町12-3',135.71,35.11,'parcel'],['d','京都タワー',135.7593,34.9875,'poi']];
test('geocoding distinguishes precise addresses, area centers and approximate town fallback',()=>{
 assert.equal(typeof geo.resolveAddress,'function');
 assert.equal(geo.resolveAddress(rows,'京都市左京区試験町１２番地３号').id,'c');
 const area=geo.resolveAddress(rows,'左京区');assert.equal(area.precision,'area');assert.ok(Math.abs(area.latitude-35.15)<1e-9);
 const coarse=geo.resolveAddress(rows,'京都市左京区試験町999');assert.equal(coarse.precision,'town');assert.equal(coarse.address,'京都府京都市左京区試験町');
 assert.equal(geo.resolveAddress(rows,'京都タワー').id,'d');assert.equal(geo.resolveAddress(rows,'存在しない住所'),null);
});
test('ward-prefixed addresses with parcel numbers find the correct town without inventing parcel precision',()=>{
 const data=[['s','京都府京都市左京区讃州寺町',135.77466,35.010905,'town'],['k','京都府京都市上京区讃州寺町',135.755382,35.025662,'town']];
 assert.equal(geo.resolveAddress(data,'左京区讃州寺町203')?.id,'s');
 assert.equal(geo.resolveAddress(data,'左京区讃州寺町２０３番地')?.precision,'town');
 assert.equal(geo.searchAddresses(data,'左京区讃州寺町203')[0]?.id,'s');
 assert.equal(geo.resolveAddress(data,'上京区讃州寺町203')?.id,'k');
 assert.equal(geo.resolveAddress(data,'讃州寺町203'),null);
 assert.equal(geo.resolveAddress(data,'右京区讃州寺町203'),null);
 assert.equal(geo.resolveAddress(rows,'左京区試験町12-3')?.id,'c');
});
