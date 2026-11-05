import test from 'node:test';import assert from 'node:assert/strict';import {gzipSync} from 'node:zlib';
import {localGeocodeCandidates} from '../web/city-addresses.mjs';
test('activity city ranks facilities without hiding supported other cities',async t=>{
 const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);
 globalThis.fetch=async url=>{const code=url.match(/cities\/(\d+)\//)[1];if(url.endsWith('city.json'))return Response.json({addresses:{path:code+'/addresses.gz'},places:{path:code+'/places.gz'}});return new Response(gzipSync(JSON.stringify({rows:code==='22205'&&url.endsWith('places.gz')?[['station','熱海駅',139.077,35.103,'poi']]:code==='26100'?[['dummy','京都市試験町',135,35,'town']]:[]})));};
 assert.equal((await localGeocodeCandidates('熱海駅','26100'))[0]?.address,'熱海駅');
});
