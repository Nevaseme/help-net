// Real saved numerical results, never an intercepted calculation fixture.
import assert from 'node:assert/strict';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {mapPrecomputedResult} from '../web/tsunami-precomputed.mjs';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE?pathToFileURL(process.env.PLAYWRIGHT_MODULE).href:'playwright');
const localServer=process.env.PWA_TEST_URL?null:await (await import('./venue-server.mjs')).startVenueServer();
const base=process.env.PWA_TEST_URL||localServer.base;
const out=resolve(process.env.PWA_TEST_OUTPUT||'output/playwright/tsunami-precomputed');
const bundle=JSON.parse(await readFile(new URL('../web/tsunami-precomputed-atami.json',import.meta.url),'utf8'));
await mkdir(out,{recursive:true});
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,headless:true});
const context=await browser.newContext({viewport:{width:400,height:860}});
const page=await context.newPage(),errors=[],checks=[],timings=[];let workers=0;
page.on('pageerror',e=>errors.push(e.message));page.on('worker',()=>workers++);
async function show(height,phase){
 await page.locator('[data-tab="buildings"]').click();
 if(await page.locator('#map-tsunami').getAttribute('aria-pressed')!=='true')await page.locator('#map-tsunami').click();
 await page.locator('#tsunami-height').fill(String(height));
 // Time inside the page: excludes automation transport and polling overhead.
 const ms=await page.evaluate(async()=>{
  const start=performance.now();document.querySelector('#tsunami-calculate').click();
  await new Promise((resolve,reject)=>{const check=()=>{
   const status=document.querySelector('#tsunami-status').dataset.status;
   if(status==='error'){reject(Error(document.querySelector('#tsunami-status').textContent));return;}
   if(status==='success'){requestAnimationFrame(()=>requestAnimationFrame(resolve));return;}
   if(performance.now()-start>30000){reject(Error('Saved result display timeout'));return;}
   requestAnimationFrame(check);
  };check();});return performance.now()-start;
 });
 const expected=mapPrecomputedResult(bundle,height);
 assert.equal(await page.locator('#tsunami-map').getAttribute('data-case-id'),expected.metadata.id);
 assert.equal(Number(await page.locator('#tsunami-map').getAttribute('data-cells')),expected.geojson.features.length);
 timings.push({phase,height,display_ms:ms,cells:expected.geojson.features.length});
}
try{
 await page.goto(base);
 assert.equal(await page.title(),'Tasukeai');
 await page.locator('#cloud-create').waitFor();
 await page.locator('#cloud-group-name').fill('事前計算確認・架空');await page.locator('#cloud-admin-name').fill('架空管理者');
 await page.locator('#cloud-create button').click();await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('管理者'));
 await page.locator('[data-tab="buildings"]').click();
 await page.locator('.leaflet-container').waitFor();
 assert.deepEqual(await page.locator('.app-nav button').allTextContents(),['地図','一覧','対応','安否']);
 // Explicitly choose the fixture's home location before enabling tsunami.
 await page.locator('#menu-open').click();await page.locator('[data-menu="profile"]').click();
 await page.locator('#name').fill('津波地図確認（架空）');await page.locator('#lat').fill('35.097');await page.locator('#lon').fill('139.077');await page.locator('#profile-save').click();
 await page.waitForFunction(()=>document.querySelector('#profile-feedback').dataset.status==='success');
 await page.locator('[data-tab="buildings"]').click();
 await page.evaluate(()=>{const original=L.Map.prototype.setView;L.Map.prototype.setView=function(...args){window.testLeafletMap=this;L.Map.prototype.setView=original;return original.apply(this,args);};});
 await page.locator('#tsunami-fit').click();
 await page.evaluate(()=>{const m=window.testLeafletMap;window.testCamera={lat:m.getCenter().lat,lng:m.getCenter().lng,zoom:m.getZoom()};window.testMapElement=document.querySelector('.leaflet-container');});
 await show(35,'first-load');
 assert.equal(await page.locator('#map-unknown-count').isVisible(),false);
 assert.equal(await page.locator('#map-tsunami').evaluate(e=>getComputedStyle(e).backgroundColor),'rgba(21, 94, 239, 0.18)');
 await page.locator('#map-tsunami').click();
 assert.equal(await page.locator('#tsunami-map').getAttribute('data-cells'),null);
 await page.locator('#map-tsunami').click();
 assert.ok(Number(await page.locator('#tsunami-map').getAttribute('data-cells'))>0);
 assert.equal(await page.evaluate(()=>window.testMapElement===document.querySelector('.leaflet-container')),true);
 assert.deepEqual(await page.locator('.map-toolbar').evaluate(el=>Array.from(el.children).map(e=>e.id||e.className)),['map-safe-toggle','tsunami-inline-control','map-unknown']);
 checks.push('Tsunami toggle is between safe and unknown; removes/restores overlay on the same map');
 for(let h=0;h<=35;h++)await show(h,'cached');
 assert.equal(workers,0);checks.push('All 36 exact saved heights rendered without a numerical Worker');
 for(const invalid of ['12.5','36','-1']){
  await page.locator('#tsunami-height').fill(invalid);
  assert.equal(await page.locator('#tsunami-height').evaluate(el=>el.checkValidity()),false);
 }
 await show(20,'cached');
 assert.equal(await page.locator('.map-calculation').count(),0);
 assert.equal(await page.locator('#tsunami-controls').count(),0);
 assert.equal(await page.getByRole('button',{name:'表示範囲',exact:true}).count(),0);
 await page.screenshot({path:out+'/height-20.png'});
 await page.locator('.map-legend > summary').click();
 assert.equal(await page.locator('.tsunami-legend').isVisible(),true);
 assert.match(await page.locator('.map-legend').innerText(),/津波の浸水深/);
 assert.doesNotMatch(await page.locator('.map-legend').innerText(),/計算条件|出典|ALB|結果ID|基準地点/);
 assert.match(await page.locator('.map-legend').innerText(),/3m以上/);
 await page.screenshot({path:out+'/legend.png'});
 await page.locator('.map-legend > summary').click();
 checks.push('Tsunami conditions share the lower-left legend; no explanation panel or fit button');
 await page.locator('#tsunami-height').fill('10');await page.waitForFunction(()=>document.querySelector('#tsunami-status').textContent.includes('10m')&&document.querySelector('#tsunami-status').dataset.status==='success');
 checks.push('Inline height edit automatically updates the same map');
 const camera=await page.evaluate(()=>{const m=window.testLeafletMap;const c=window.testCamera;return {before:c,after:{lat:m.getCenter().lat,lng:m.getCenter().lng,zoom:m.getZoom()},pixelDrift:m.project([c.lat,c.lng],c.zoom).distanceTo(m.project(m.getCenter(),c.zoom))};});
 assert.equal(camera.after.zoom,camera.before.zoom);
 assert.ok(camera.pixelDrift<.75,'Camera drift exceeds Leaflet pixel rounding: '+JSON.stringify(camera));
 assert.equal(await page.locator('.leaflet-overlay-pane path[fill="#fff"][stroke="#155eef"]').count(),0);
 checks.push('Toggle and height changes preserve center and zoom; reference circle absent');
 await page.locator('#map-tsunami').click();await page.locator('#tsunami-height').fill('15');await page.locator('#map-tsunami').click();
 await page.waitForFunction(()=>document.querySelector('#tsunami-status').textContent.includes('15m')&&document.querySelector('#tsunami-status').dataset.status==='success');
 assert.equal(await page.locator('#tsunami-map').getAttribute('data-case-id'),mapPrecomputedResult(bundle,15).metadata.id);
 checks.push('Editing height while overlay is off cannot restore a stale result');
 await page.locator('#tsunami-height').fill('20');await page.waitForFunction(()=>document.querySelector('#tsunami-status').textContent.includes('20m')&&document.querySelector('#tsunami-status').dataset.status==='success');await page.waitForTimeout(1500);await page.screenshot({path:out+'/map-20.png'});
 await page.locator('#menu-open').click();await page.locator('[data-menu="profile"]').click();
 await page.locator('#name').fill('保存表示確認（架空）');await page.locator('#profile-save').click();
 await page.waitForFunction(()=>document.querySelector('#profile-feedback').dataset.status==='success');
 await page.waitForFunction(()=>document.querySelector('#offline').textContent.includes('準備ができました'));
 await context.setOffline(true);await page.reload();await page.locator('.leaflet-container').waitFor();
 for(const h of [0,1,10,20,35])await show(h,'offline-reload');
 for(const tab of ['residents','response','safety','buildings']){await page.locator(`[data-tab="${tab}"]`).click();assert.equal(await page.locator('#'+tab).isVisible(),true);}
 await page.locator('#menu-open').click();await page.locator('[data-menu="profile"]').click();assert.equal(await page.locator('#name').inputValue(),'保存表示確認（架空）');
 checks.push('Offline reload preserves profile, tabs and saved heights; no interpolation');assert.equal(workers,0);assert.deepEqual(errors,[]);
 await writeFile(out+'/verification.json',JSON.stringify({passed:true,base,bundleId:bundle.id,checks,timings,workers,errors,camera,realSavedNumericalData:true},null,2));
 console.log(JSON.stringify({passed:true,cases:36,timings,workers}));
}catch(error){await page.screenshot({path:out+'/failure.png'}).catch(()=>{});await writeFile(out+'/verification.json',JSON.stringify({passed:false,base,checks,timings,workers,errors,error:String(error)},null,2));throw error;}
finally{await browser.close();await localServer?.close();}
