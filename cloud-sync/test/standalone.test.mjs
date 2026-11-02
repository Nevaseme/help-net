import {test} from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';

test('single-upload bundle serves same-origin PWA and uncached API without ASSETS binding',async t=>{
 execFileSync(process.execPath,[fileURLToPath(new URL('../scripts/build.mjs',import.meta.url)),'--standalone'],{stdio:'pipe'});
 const script=await readFile(new URL('../dist/standalone.mjs',import.meta.url),'utf8');
 const mf=new Miniflare({...convertV4MiniflareOptions({name:'standalone-test',modules:true,script,compatibilityDate:'2026-10-23',durableObjects:{GROUPS:{className:'GroupJournal',useSQLite:true}},ratelimits:{SIGNUP_LIMITER:{namespace_id:'10302026',simple:{limit:30,period:60}}}}),telemetry:{enabled:false}});
 t.after(()=>mf.dispose());
 const html=await mf.dispatchFetch('https://sync.test/');assert.equal(html.status,200);assert.match(html.headers.get('content-type'),/text\/html/);assert.match(html.headers.get('content-security-policy'),/script-src 'self'/);const source=await html.text();assert.match(source,/app.mjs/);assert.match(source,/<meta name="tasukeai-cloud" content="1">/);
 const js=await mf.dispatchFetch('https://sync.test/cloud-client.mjs');assert.equal(js.status,200);assert.match(js.headers.get('content-type'),/javascript/);
 // Rejected historical inundation is not served as a current calculation.
 assert.equal((await mf.dispatchFetch('https://sync.test/tsunami-atami.json')).status,404);
 assert.equal((await mf.dispatchFetch('https://sync.test/tsunami-solver-worker.mjs')).status,404);
 const saved=await mf.dispatchFetch('https://sync.test/tsunami-precomputed-atami.json');assert.equal(saved.status,200);assert.equal((await saved.json()).cases.length,36);
 const reader=await mf.dispatchFetch('https://sync.test/tsunami-precomputed.mjs');assert.equal(reader.status,200);assert.match(reader.headers.get('content-type'),/javascript/);
 assert.match(html.headers.get('content-security-policy'),/img-src[^;]*https:\/\/cyberjapandata.gsi.go.jp/);
 const api=await mf.dispatchFetch('https://sync.test/api/v1/info');assert.equal(api.status,200);assert.equal(api.headers.get('cache-control'),'no-store');
 assert.equal((await mf.dispatchFetch('https://sync.test/.local/config.json')).status,404);
 assert.equal((await mf.dispatchFetch('https://sync.test/manifest.webmanifest',{method:'HEAD'})).status,200);
});
