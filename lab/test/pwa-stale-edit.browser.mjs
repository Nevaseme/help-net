// Test-only IndexedDB fixtures: a second device updates a record while its editor is open.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE||'C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const root=resolve('lab/web'),out=resolve('output/playwright/pwa-stale-edit');await mkdir(out,{recursive:true});
const mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=createServer(async(req,res)=>{try{const name=new URL(req.url,'http://local').pathname;if(name.startsWith('/api/'))throw Error();const path=resolve(root,'.'+(name==='/'?'/index.html':name));if(!path.startsWith(root))throw Error();const body=await readFile(path);res.writeHead(200,{'content-type':mime[extname(path)]||'application/octet-stream'});res.end(body);}catch{res.writeHead(404);res.end('not found');}});
await new Promise(r=>server.listen(4208,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const context=await browser.newContext({viewport:{width:400,height:860},serviceWorkers:'block'}),page=await context.newPage(),errors=[],checks=[];
page.on('pageerror',e=>errors.push(e.message));let stage='seed';
try{
 await page.goto('http://127.0.0.1:4208/');await page.locator('.leaflet-container').waitFor();
 const fixture=await page.evaluate(async()=>{
  const {Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B'),id=await s.identity(),remote=crypto.randomUUID(),memberId=crypto.randomUUID();
  await s.importRoster({version:1,groupId:'demo',members:[{memberId,deviceIds:[id,remote],role:'ADMIN',activityName:'同期確認者（テスト）'}]},id);
  await s.saveProfile({groupId:'demo',originId:id,revision:1,updatedAt:0,name:'同期確認者（テスト）',latitude:35.097,longitude:139.077,floor:'',room:'',safety:'UNREPORTED'});
  const incidentId=crypto.randomUUID();await s.commandQuake({groupId:'demo',eventId:incidentId,actorDeviceId:id,operatedAt:Date.now(),action:'TRAINING_START',training:true,title:'同時更新の確認（テスト）',area:'テスト地区'});await s.update('meta','incident:demo',()=>incidentId);
  const reportId=crypto.randomUUID(),target={kind:'REPORT',id:reportId};
  for(const change of [{type:'REPORT',reportId,category:'建物倒壊',description:'編集前の通報（テスト）',latitude:35.097,longitude:139.077},{type:'PROGRESS',target,status:'UNASSIGNED',result:''},{type:'PRIORITY',target,urgency:'LOW',reason:'編集前（テスト）'},{type:'AVAILABILITY',state:'AVAILABLE'},{type:'ASSIGNMENT',target,memberId,active:false}])await s.appendResponse({groupId:'demo',incidentId:change.type==='AVAILABILITY'?null:incidentId,eventId:change.type==='REPORT'?reportId:crypto.randomUUID(),actorDeviceId:id,operatedAt:Date.now(),change,replaces:[]});
  s.close();return {id,remote,memberId,incidentId,reportId,target};
 });
 await page.reload();await page.locator('.leaflet-container').waitFor();await page.locator('[data-tab="response"]').click();
 const openCase=async()=>{if(!await page.locator('#case-sheet').isVisible())await page.locator('#response-list [data-case]').filter({hasText:'建物倒壊'}).click();};
 async function remoteUpdate(type,values){return page.evaluate(async({fixture,type,values})=>{
  const {Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B'),heads=(await s.responseHeads('demo')).filter(e=>e.change.type===type),original=heads[0];
  const event=await s.appendResponse({groupId:'demo',incidentId:type==='AVAILABILITY'?null:fixture.incidentId,eventId:crypto.randomUUID(),actorDeviceId:fixture.remote,operatedAt:Date.now(),change:{...original.change,...values},replaces:heads.map(e=>e.eventId)});
  const count=(await s.responseEvents('demo')).length;s.close();return {eventId:event.eventId,count};
 },{fixture,type,values});}
 async function refreshVisibleRecord(){await page.evaluate(()=>document.getElementById('filter').dispatchEvent(new Event('change')));}
 async function expectStale(type,remote){
  assert.equal(await page.locator('#response-editor').isVisible(),true);
  await page.locator('#response-edit-form button[type="submit"]').click();
  await page.waitForFunction(()=>!document.getElementById('response-editor').open||document.querySelector('#response-edit-form .feedback')?.classList.contains('error'));
  assert.equal(await page.locator('#response-editor').isVisible(),true,`${type}: stale input must not overwrite the unseen remote head`);
  assert.match(await page.locator('#response-edit-form .feedback').textContent(),/記録が更新されています/);
  const persisted=await page.evaluate(async type=>{const {Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B');const result={heads:(await s.responseHeads('demo')).filter(e=>e.change.type===type).map(e=>e.eventId),count:(await s.responseEvents('demo')).length};s.close();return result;},type);
  assert.deepEqual(persisted.heads,[remote.eventId]);assert.equal(persisted.count,remote.count);checks.push(`${type}: unseen remote head retained; stale save rejected with form left open`);
  await page.locator('#response-editor .sheet-close').click();
 }
 stage='PROGRESS';await openCase();await page.locator('[data-edit="progress"]').click();await page.locator('[name="status"][value="IN_PROGRESS"]').check();await page.locator('[name="result"]').fill('古い画面の入力（テスト）');
 let remote=await remoteUpdate('PROGRESS',{status:'DONE',result:'別端末で対応完了（テスト）'});await refreshVisibleRecord();await page.waitForFunction(()=>document.querySelector('#case-sheet .case-facts div:nth-child(2) dd')?.textContent==='対応済み');await expectStale('PROGRESS',remote);
 stage='PRIORITY';await page.locator('[data-edit="priority"]').click();await page.locator('[name="urgency"][value="HIGH"]').check();await page.locator('[name="reason"]').fill('古い画面の理由（テスト）');
 remote=await remoteUpdate('PRIORITY',{urgency:'MEDIUM',reason:'別端末で見直し（テスト）'});await refreshVisibleRecord();await page.waitForFunction(()=>document.querySelector('#case-sheet .case-facts div:nth-child(3) dd')?.textContent==='中');await expectStale('PRIORITY',remote);
 stage='ASSIGNMENT';await page.locator('[data-edit="assignment"]').click();await page.locator('[name="assigned"]').check();remote=await remoteUpdate('ASSIGNMENT',{active:true});await refreshVisibleRecord();await page.waitForFunction(()=>document.querySelector('#case-sheet .case-facts div:first-child dd')?.textContent==='同期確認者（テスト）');await expectStale('ASSIGNMENT',remote);
 stage='REPORT cancellation';await page.locator('[data-edit="cancel-report"]').click();await page.locator('[name="cancelReason"]').fill('古い通報を取消（テスト）');
 remote=await remoteUpdate('REPORT',{description:'別端末で訂正した通報（テスト）'});await refreshVisibleRecord();await page.waitForFunction(()=>document.querySelector('#case-sheet .case-description')?.textContent==='別端末で訂正した通報（テスト）');await expectStale('REPORT',remote);
 stage='AVAILABILITY';await page.locator('#case-sheet .sheet-close').click();await page.locator('#own-activity').click();await page.locator('[name="state"][value="UNAVAILABLE"]').check();
 remote=await remoteUpdate('AVAILABILITY',{state:'WORKING'});await refreshVisibleRecord();await page.waitForFunction(()=>document.querySelector('#own-activity .activity-state')?.textContent==='対応中');await expectStale('AVAILABILITY',remote);
 assert.deepEqual(errors,[]);await writeFile(out+'/verification.json',JSON.stringify({passed:true,checks,errors,fictionalData:true},null,2));console.log('Stale editor browser regression passed');
}catch(error){await writeFile(out+'/verification.json',JSON.stringify({passed:false,stage,checks,errors,error:error.stack||String(error)},null,2));throw error;}
finally{await browser.close();const closed=new Promise(r=>server.close(r));server.closeAllConnections();await closed;}
