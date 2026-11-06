// Real PWA + IndexedDB + HTTP API + SQLite Journal; never presses the sync button.
import assert from 'node:assert/strict';
import http from 'node:http';
import {DatabaseSync} from 'node:sqlite';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {pathToFileURL} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {Journal} from '../../cloud-sync/src/journal.mjs';
import {handle} from '../../cloud-sync/src/http.mjs';
import {chromium} from '../../tools/browser-runtime.mjs';
const out=resolve(process.env.PWA_TEST_OUTPUT||process.env.TEMP+'/pland-reconnect-proof');await mkdir(out,{recursive:true});
const groups=new Map(),errors=[],checks=[];let stage='setup';
const env={SIGNUP_LIMITER:{limit:async()=>({success:true})},GROUPS:{getByName(group){
 if(!groups.has(group)){
  const db=new DatabaseSync(':memory:');
  const journal=new Journal({all:(q,...p)=>db.prepare(q).all(...p),run:(q,...p)=>db.prepare(q).run(...p),transaction:fn=>{db.exec('BEGIN IMMEDIATE');try{const r=fn();db.exec('COMMIT');return r;}catch(e){db.exec('ROLLBACK');throw e;}},flush:async()=>{}});
  groups.set(group,{db,journal,async dispatch(op,token,args){try{const body=await (op==='create'||op==='join'?journal[op](args):op==='pull'?journal.pull(token,args.after,args.limit):journal[op](token,args));return {status:200,body};}catch(e){return {status:e.status||503,body:{error:{code:e.code||'storage_unavailable',message:e.message}}};}}});
 }return groups.get(group);
}}};
const mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.webmanifest':'application/manifest+json'};
const server=http.createServer(async(req,res)=>{try{
 const url=new URL(req.url,'http://'+req.headers.host);
 if(url.pathname.startsWith('/api/')){const chunks=[];for await(const c of req)chunks.push(c);const response=await handle(new Request(url,{method:req.method,headers:req.headers,...(req.method==='POST'?{body:Buffer.concat(chunks)}:{})}),env);res.writeHead(response.status,Object.fromEntries(response.headers));res.end(Buffer.from(await response.arrayBuffer()));return;}
 const file=url.pathname==='/'?'index.html':url.pathname.slice(1);if(file.includes('/')||file.includes('..')){res.writeHead(404);res.end();return;}
 const body=await readFile(new URL('../web/'+file,import.meta.url));res.writeHead(200,{'content-type':mime[extname(file)]||'application/octet-stream','cache-control':'no-store'});res.end(body);
}catch{res.writeHead(404);res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=process.env.PWA_TEST_URL||'http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const ca=await browser.newContext({viewport:{width:400,height:860}}),cb=await browser.newContext({viewport:{width:1280,height:900}});
const a=await ca.newPage(),b=await cb.newPage();
for(const page of [a,b]){page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(45000);}
const note=s=>{stage=s;checks.push(s);console.log(s);};
async function menu(page,name){while(await page.locator('dialog.design-sheet[open]').count())await page.locator('dialog.design-sheet[open]').last().locator('.sheet-close').click();await page.locator('#menu-open').click();await page.locator(`[data-menu="${name}"]`).click();}
async function idle(page){await page.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled);}
async function profile(page,name){await menu(page,'profile');await page.locator('#name').fill(name);await page.locator('#lat').fill('35.095');await page.locator('#lon').fill('139.07');await page.locator('#profile-save').click();await page.waitForFunction(()=>document.querySelector('#profile-feedback').dataset.status==='success');}
async function stored(page,table,predicate){
 const deadline=Date.now()+45000;
 while(Date.now()<deadline){
  const rows=await page.evaluate(async table=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');try{return await s.read(table);}finally{s.close();}},table);
  if(rows.some(Function('r','return '+predicate)))return;
  await delay(100);
 }
 assert.fail(`Timed out waiting for durable ${table}: ${predicate}`);
}
async function safety(page,value,memo){await page.locator('[data-tab="safety"]').click();await page.locator('#'+value).click();if(value==='sos')await page.locator('#own-memo').fill(memo);await page.locator('#status-update').click();await page.waitForFunction(()=>document.querySelector('#own-status-feedback').textContent==='保存しました');}
async function saveEditor(page){await page.locator('#response-edit-form button[type="submit"]').click();await page.waitForFunction(()=>!document.querySelector('#response-editor').open);}
try{
 for(const page of [a,b]){await page.goto(base);await page.locator('#cloud-create').waitFor();assert.equal(await page.title(),'Tasukeai — 安否と対応の共有');await page.waitForFunction(()=>document.querySelector('#offline').textContent.includes('準備ができました'));}
 await a.locator('#cloud-group-name').fill('自動再接続・架空グループ');await a.locator('#cloud-admin-name').fill('架空管理者');await a.locator('#cloud-create button').click();await a.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('管理者'));await profile(a,'架空管理者');await idle(a);
 await menu(a,'group');await a.locator('#cloud-invite').click();await a.locator('#cloud-invite-output').waitFor();const invitation=await a.locator('#cloud-invite-output').inputValue();
 await b.locator('#cloud-mode').click();await b.locator('#cloud-invite-input').fill(invitation);await b.locator('#cloud-member-name').fill('架空メンバー');await b.locator('#cloud-join button').click();await b.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('メンバー'));await profile(b,'架空メンバー');await stored(a,'residents',"r.name==='架空メンバー'");note('Real HTTP registration and two independent browser stores synchronize automatically');

 stage='pageshow restores retry loop';await idle(a);
 // Isolate the pageshow contract: a restored document need not get online/visibilitychange.
 // This exercises production listeners; it is not a claim that Chromium used its BFCache.
 await a.evaluate(()=>window.dispatchEvent(new PageTransitionEvent('pagehide',{persisted:true})));
 const restoredPull=a.waitForRequest(r=>r.method()==='GET'&&r.url().includes('/records?'),{timeout:3000});
 await a.evaluate(()=>window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true})));
 await restoredPull;await idle(a);note('Persisted pageshow resumes automatic synchronization without online or visibilitychange');

 await menu(a,'incidents');await a.locator('#training-area').fill('架空試験地区');await a.locator('#training-start').click();await a.waitForFunction(()=>document.querySelector('header h1').textContent==='訓練');await b.waitForFunction(()=>document.querySelector('header h1').textContent==='訓練');
 await cb.setOffline(true);await safety(b,'sos','オフラインの架空 SOS');
 await b.locator('[data-tab="response"]').click();await b.locator('#report-add').click();await b.locator('#tsunami-map').click({position:{x:160,y:180}});await b.locator('[name="category"]').selectOption('道路閉塞');await b.locator('[name="description"]').fill('オフライン架空倒木');await saveEditor(b);
 await stored(b,'incidents',"r.kind==='ANSWER'&&r.safety==='SOS'");await stored(b,'responses',"r.change?.description==='オフライン架空倒木'");await b.screenshot({path:out+'/offline-report.png'});note('Open offline page durably saves both SOS and map report to IndexedDB');
 await cb.setOffline(false);await stored(a,'responses',"r.change?.description==='オフライン架空倒木'");await stored(a,'incidents',"r.kind==='ANSWER'&&r.safety==='SOS'");await a.locator('[data-tab="residents"]').click();await a.waitForFunction(()=>document.querySelector('#list').textContent.includes('SOS'));await a.screenshot({path:out+'/received-sos.png'});note('Online event uploads offline records and another open browser receives both without sync clicks');

 stage='network recovery without online event';await idle(b);let blocked=true;
 await cb.route('**/api/v1/**',route=>blocked?route.abort('internetdisconnected'):route.continue());
 await safety(b,'safe','通信復帰イベントなし');await idle(b);blocked=false;
 await stored(a,'incidents',"r.kind==='ANSWER'&&r.safety==='SAFE'");await a.locator('[data-tab="residents"]').click();await a.waitForFunction(()=>document.querySelector('#list').textContent.includes('無事'));await cb.unroute('**/api/v1/**');note('Periodic retry recovers network without an online event and recipient polls automatically');

 stage='lost upload response';await idle(b);let lost=false;
 await cb.route('**/api/v1/**/records',async route=>{if(!lost&&route.request().method()==='POST'){lost=true;await route.fetch();await route.abort('connectionreset');}else await route.continue();});
 await safety(b,'sos','保存応答消失の架空 SOS');await stored(a,'memos',"r.text==='保存応答消失の架空 SOS'");await b.waitForFunction(()=>document.querySelector('#cloud-status').textContent==='共有サーバーへ保存済み');assert.ok(lost);await cb.unroute('**/api/v1/**/records');
 const count=await a.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');try{return (await s.read('memos')).filter(r=>r.text==='保存応答消失の架空 SOS').length;}finally{s.close();}});assert.equal(count,1);note('Lost server ACK is retried automatically with exactly one durable recipient memo');

 stage='online event during in-flight request';await idle(b);let heldRoute,resolveHeld;const held=new Promise(r=>resolveHeld=r);
 await cb.route('**/api/v1/**/records?*',route=>{if(!heldRoute){heldRoute=route;resolveHeld();}else return route.continue();});
 await b.evaluate(()=>window.dispatchEvent(new Event('online')));await held;
 await cb.setOffline(true);await safety(b,'sos','処理中復帰の架空 SOS');await cb.setOffline(false);
 // The online event arrives while sync is busy. Finish the old failed connection afterwards.
 await heldRoute.abort('connectionreset');await cb.unroute('**/api/v1/**/records?*');
 await stored(a,'memos',"r.text==='処理中復帰の架空 SOS'");note('Reconnect during a busy request retains the local edit and retries automatically after the old request fails');

 // Reload only after the held-open automatic-delivery checks, to verify offline durability separately.
 await cb.setOffline(true);await b.reload();await b.locator('.leaflet-container').waitFor();await b.locator('[data-tab="safety"]').click();assert.equal(await b.locator('#sos').getAttribute('aria-pressed'),'true');assert.equal(await b.locator('#own-memo').inputValue(),'処理中復帰の架空 SOS');await stored(b,'responses',"r.change?.description==='オフライン架空倒木'");note('Saved safety, memo and report survive offline reload');
 assert.deepEqual(errors,[]);await writeFile(out+'/verification.json',JSON.stringify({passed:true,base,checks,errors,syncButtonClicks:0,lifecycleCoverage:'Synthetic persisted pagehide/pageshow on real page; no claim of actual BFCache activation'},null,2));
}catch(error){await Promise.all([a.screenshot({path:out+'/failure-a.png'}).catch(()=>{}),b.screenshot({path:out+'/failure-b.png'}).catch(()=>{})]);await writeFile(out+'/verification.json',JSON.stringify({passed:false,stage,checks,errors,error:String(error)},null,2));throw error;}
finally{await browser.close();await new Promise(r=>server.close(r));for(const {db}of groups.values())db.close();}
