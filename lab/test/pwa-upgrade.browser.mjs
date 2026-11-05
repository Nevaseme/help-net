// Upgrade a real cached1.6.0 PWA and its saved fictional group to the new application.
import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import http from 'node:http';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {Miniflare,convertV4MiniflareOptions} from '../../cloud-sync/node_modules/miniflare/dist/src/index.js';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE||'C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const out=resolve('output/playwright/pwa-upgrade-1.6.1');await mkdir(out,{recursive:true});
const old=await readFile('.implementation/pwa-1.6.0-standalone.mjs','utf8'),current=await readFile('cloud-sync/dist/standalone.mjs','utf8');
assert.ok(old.includes('PWA 1.6.0'));assert.ok(current.includes('PWA 1.6.1'));
const options=script=>({...convertV4MiniflareOptions({name:'pwa-upgrade',modules:true,script,compatibilityDate:'2026-10-29',durableObjects:{GROUPS:{className:'GroupJournal',useSQLite:true}},ratelimits:{SIGNUP_LIMITER:{namespace_id:'10302026',simple:{limit:30,period:60}}}}),telemetry:{enabled:false}});
const mf=new Miniflare(options(old));const base='http://127.0.0.1:8797';
const diagnostics={network:[],snapshots:[],activation:[],lifecycle:{}};
const server=http.createServer(async(req,res)=>{try{const chunks=[];for await(const c of req)chunks.push(c);const response=await mf.dispatchFetch(base+req.url,{method:req.method,headers:req.headers,...(['GET','HEAD'].includes(req.method)?{}:{body:Buffer.concat(chunks)})});const body=Buffer.from(await response.arrayBuffer());if(['/','/index.html','/app.mjs','/sw.js'].includes(req.url)){const text=body.toString('utf8');diagnostics.network.push({path:req.url,status:response.status,at:Date.now(),cacheControl:response.headers.get('cache-control'),appVersions:[...new Set(text.match(/PWA 1\.6\.\d/g)||[])],cacheName:text.match(/const CACHE='([^']+)'/)?.[1]});}res.writeHead(response.status,Object.fromEntries(response.headers));res.end(body);}catch{res.writeHead(500);res.end('Preview error');}});
await new Promise(r=>server.listen(8797,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const context=await browser.newContext({viewport:{width:390,height:844}});let page=await context.newPage();const errors=[],checks=[];let stage='old version setup';page.on('pageerror',e=>errors.push(e.message));
const menu=async name=>{await page.locator('#menu-open').click();await page.locator(`[data-menu="${name}"]`).click();};
async function workerSnapshot(label){const states=await Promise.all(context.serviceWorkers().map(worker=>worker.evaluate(async()=>({cache:typeof CACHE==='string'?CACHE:null,active:self.registration.active?.state,waiting:self.registration.waiting?.state,installing:self.registration.installing?.state,observedOwnActivate:!!self.__upgradeTestActivated,ownActivateAt:self.__upgradeTestActivatedAt,clients:(await self.clients.matchAll({includeUncontrolled:true,type:'all'})).map(c=>({id:c.id,url:c.url,type:c.type})),caches:await Promise.all((await caches.keys()).map(async name=>{const cache=await caches.open(name),html=await cache.match('/'),app=await cache.match('/app.mjs');return {name,htmlVersions:[...new Set((html?await html.text():'').match(/PWA 1\.6\.\d/g)||[])],appVersions:[...new Set((app?await app.text():'').match(/PWA 1\.6\.\d/g)||[])]};}))})).catch(e=>({error:e.message}))));diagnostics.snapshots.push({label,at:Date.now(),states});return states;}
try{
 await page.goto(base);await page.locator('#cloud-create').waitFor();await page.waitForFunction(()=>document.querySelector('#offline').textContent.includes('準備ができました'));
 await page.locator('#cloud-group-name').fill('移行確認・架空');await page.locator('#cloud-admin-name').fill('確認用（架空）');await page.locator('#cloud-create button').click();await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('管理者'));
 await menu('profile');await page.locator('#name').fill('移行確認者（架空）');await page.locator('#lat').fill('35.095');await page.locator('#lon').fill('139.07');await page.locator('#profile-save').click();await page.waitForFunction(()=>document.querySelector('#profile-feedback').dataset.status==='success');
 await menu('incidents');await page.locator('#training-area').fill('架空地区');await page.locator('#training-start').click();await page.waitForFunction(()=>document.querySelector('header h1').textContent==='訓練');
 await page.locator('[data-tab="safety"]').click();await page.locator('#sos').click();await page.waitForFunction(()=>document.querySelector('#safety-feedback').dataset.status==='success');
 await menu('group');await page.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled);await page.locator('#cloud-sync').click();await page.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled&&document.querySelector('#cloud-status').textContent.includes('共有サーバーへ保存済み'));
 const originalIdentity=await page.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');try{return await s.identity();}finally{s.close();}});
 stage='install updated cache';console.log(stage);
 await mf.setOptions(options(current));
 const expectedCache=(await (await mf.dispatchFetch(base+'/sw.js')).text()).match(/const CACHE='([^']+)'/)?.[1];assert.ok(expectedCache);
 diagnostics.lifecycle.updateRequestedAt=Date.now();
 await page.evaluate(async()=>{const registration=await navigator.serviceWorker.getRegistration();await registration.update();});
 // This runtime's waitForFunction polls the returned value synchronously: an async
 // predicate is a truthy Promise even when it resolves false. Await in Node instead.
 const installDeadline=Date.now()+30_000;let installed=false;
 while(Date.now()<installDeadline){if(await page.evaluate(async()=>(await navigator.serviceWorker.getRegistration())?.waiting?.state==='installed')){installed=true;break;}await new Promise(r=>setTimeout(r,100));}
 assert.ok(installed,'The updated worker did not finish installing');diagnostics.lifecycle.installedAt=Date.now();
 const installedStates=await workerSnapshot('new worker installed; old page still open');
 const installedCache=installedStates.find(s=>s.cache===expectedCache)?.caches.find(c=>c.name===expectedCache);
 assert.deepEqual(installedCache?.htmlVersions,['PWA 1.6.1']);assert.deepEqual(installedCache?.appVersions,['PWA 1.6.1']);
 let updatedWorker;
 for(const worker of context.serviceWorkers())if(await worker.evaluate(()=>typeof CACHE==='string'?CACHE:null).catch(()=>null)===expectedCache)updatedWorker=worker;
 assert.ok(updatedWorker,'The installed worker must be identified as the new build');
 await updatedWorker.evaluate(()=>{self.__upgradeTestActivated=false;self.addEventListener('activate',()=>{self.__upgradeTestActivated=true;self.__upgradeTestActivatedAt=Date.now();});});
 // Closing and immediately reopening a controlled page can retain the old worker.
 // Observe normal activation with no pages open; do not force skipWaiting or erase caches/data.
 diagnostics.lifecycle.closeRequestedAt=Date.now();await page.close();diagnostics.lifecycle.allPagesClosedAt=Date.now();stage='activate update after all pages close';
 await workerSnapshot('all app pages closed');
 const activationDeadline=Date.now()+30_000;let activated=false;
 while(Date.now()<activationDeadline){
  const states=await Promise.all(context.serviceWorkers().map(worker=>worker.evaluate(()=>({cache:typeof CACHE==='string'?CACHE:null,active:self.registration.active?.state,waiting:!!self.registration.waiting,observedOwnActivate:!!self.__upgradeTestActivated})).catch(()=>null)));
  const legacyPredicate=states.some(s=>s?.active==='activated'&&!s.waiting),ownActivate=states.some(s=>s?.cache===expectedCache&&s.observedOwnActivate&&s.active==='activated'&&!s.waiting);
  if(!diagnostics.activation.length||legacyPredicate||ownActivate)diagnostics.activation.push({at:Date.now(),legacyPredicate,ownActivate,states});
  if(ownActivate){activated=true;break;}
  await new Promise(r=>setTimeout(r,100));
 }
 const activatedStates=await workerSnapshot('activation gate finished');diagnostics.lifecycle.activationConfirmedAt=Date.now();diagnostics.lifecycle.ownActivateAt=activatedStates.find(s=>s.cache===expectedCache)?.ownActivateAt;
 assert.ok(activated,'The completed update did not activate after every page closed');
 const activatedCaches=activatedStates.find(s=>s.cache===expectedCache)?.caches;
 assert.deepEqual(activatedCaches?.filter(c=>c.name.startsWith('pland-lab-')).map(c=>c.name),[expectedCache]);
 page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(base);
 stage='new version and offline readiness';console.log(stage);
 await page.waitForFunction(()=>document.querySelector('footer').textContent.includes('PWA 1.6.1'));
 await page.waitForFunction(()=>document.querySelector('#offline').textContent.includes('準備ができました'));
 await menu('group');assert.match(await page.locator('#cloud-group-state').textContent(),/移行確認・架空.*確認用（架空）.*管理者/);
 stage='updated server authentication';console.log(stage);
 await page.locator('#cloud-sync').click();await page.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled&&document.querySelector('#cloud-status').textContent.includes('共有サーバーへ保存済み'));
 const identity=await page.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');try{return await s.identity();}finally{s.close();}});assert.equal(identity,originalIdentity);
 await context.setOffline(true);await page.reload();await page.locator('.leaflet-container').waitFor();
 stage='offline preserved state';console.log(stage);
 await menu('profile');assert.equal(await page.locator('#name').inputValue(),'移行確認者（架空）');await page.locator('[data-tab="safety"]').click();assert.equal(await page.locator('#sos').getAttribute('aria-pressed'),'true');
 for(const tab of ['residents','response','safety','buildings']){await page.locator(`[data-tab="${tab}"]`).click();assert.equal(await page.locator('#'+tab).isVisible(),true);}
 await page.locator('#tsunami-panel > summary').click();await page.locator('#tsunami-height').fill('20');await page.locator('#tsunami-calculate').click();await page.waitForFunction(()=>document.querySelector('#tsunami-status').dataset.status==='success');
 checks.push('Cached1.6.0 updates to1.6.1 after pages close','Identity, membership, activity name, profile, training and SOS retained','Existing server membership still authenticates','Offline restart, four tabs and saved20m result work');
 assert.deepEqual(errors,[]);await page.screenshot({path:out+'/upgraded-offline-map.png'});await writeFile(out+'/verification.json',JSON.stringify({passed:true,checks,errors,fictionalData:true,diagnostics},null,2));console.log('PWA upgrade and offline preservation passed');
}catch(error){await page.screenshot({path:out+'/failure.png'}).catch(()=>{});const state=await page.evaluate(async()=>({footer:document.querySelector('footer')?.textContent,offline:document.querySelector('#offline')?.textContent,cloud:document.querySelector('#cloud-status')?.textContent,workers:(await navigator.serviceWorker.getRegistrations()).map(r=>({active:r.active?.state,waiting:r.waiting?.state,installing:r.installing?.state})),caches:await caches.keys()})).catch(()=>null);await writeFile(out+'/verification.json',JSON.stringify({passed:false,stage,checks,errors,state,error:error.stack||String(error)},null,2));throw error;}
finally{await writeFile(out+'/diagnostics.json',JSON.stringify(diagnostics,null,2));await browser.close();const closed=new Promise(r=>server.close(r));server.closeAllConnections();await mf.dispose();await closed;}
