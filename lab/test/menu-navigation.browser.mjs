import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const root=resolve('lab/web'),out=resolve('output/menu-1536/pwa');await mkdir(out,{recursive:true});
const mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=createServer(async(req,res)=>{try{const name=new URL(req.url,'http://local').pathname;const path=resolve(root,'.'+(name==='/'?'/index.html':name));if(!path.startsWith(root))throw Error();const bytes=await readFile(path);res.writeHead(200,{'content-type':mime[extname(path)]||'application/octet-stream'});res.end(bytes);}catch{res.writeHead(404);res.end('not found');}});
await new Promise(r=>server.listen(4217,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const page=await browser.newPage({viewport:{width:400,height:860},serviceWorkers:'block'}),errors=[];page.on('pageerror',e=>errors.push(e.message));
const base=process.env.MENU_URL||'http://127.0.0.1:4217/';
try{
 // Fictional browser-local membership; no production group or server writes.
 await page.route('**/api/**',route=>route.fulfill({status:404,contentType:'application/json',body:'{}'}));
 if(process.env.MENU_URL)await page.route(base,async route=>{const response=await route.fetch();await route.fulfill({response,body:(await response.text()).replace('<meta name="tasukeai-cloud" content="1">','')});});
 await page.goto(base);await page.locator('.leaflet-container').waitFor();
 await page.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B'),id=await s.identity();await s.importRoster({version:1,groupId:'demo',members:[{memberId:crypto.randomUUID(),deviceIds:[id],role:'ADMIN',activityName:'確認用'}]},id);await s.saveProfile({groupId:'demo',originId:id,revision:1,updatedAt:0,name:'確認用',latitude:35.01,longitude:135.76,floor:'1階',room:'',safety:'UNREPORTED'});s.close();});
 await page.reload();await page.locator('#menu-open').waitFor({state:'visible'});
 const checks=[];
 for(const [width,height,scale,language] of [[400,860,1,'ja'],[320,640,1,'ja'],[1280,900,1,'ja'],[320,640,2,'en']]){
  await page.setViewportSize({width,height});await page.evaluate(scale=>document.documentElement.style.fontSize=`${16*scale}px`,scale);
  await page.locator('#menu-open').click();await page.locator('[data-menu="settings"]').click();await page.locator(`[name="ui-language"][value="${language}"]`).check();await page.locator('#design-settings .sheet-close').click();
  await page.locator('#menu-open').click();
  assert.equal(await page.locator('.menu-group').count(),3);assert.equal(await page.locator('[data-menu]').count(),5);assert.equal(await page.locator('[data-menu="response"]').count(),0);
  assert.equal(await page.locator('#menu-close').evaluate(el=>el===document.activeElement),true);
  const labels=await page.locator('.menu-group h3').allTextContents();assert.deepEqual(labels,language==='ja'?['登録情報','災害への備え','アプリ設定']:['Your information','Preparedness','App settings']);
  for(const row of await page.locator('[data-menu]').all()){await row.scrollIntoViewIfNeeded();const b=await row.boundingBox();assert.ok(b.height>=48&&b.x>=0&&b.x+b.width<=width+1);}
  await page.locator('.menu-items').evaluate(el=>el.scrollTop=0);await page.screenshot({path:`${out}/menu-${width}-${scale}-${language}.png`});
  const close=await page.locator('#menu-close').boundingBox();assert.ok(close.y>=0&&close.y+close.height<=height);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.keyboard.press('Escape');assert.equal(await page.locator('#app-menu').isVisible(),false);await page.waitForFunction(()=>document.getElementById('menu-open').getAttribute('aria-expanded')==='false');assert.equal(await page.locator('#menu-open').evaluate(el=>el===document.activeElement),true);
  checks.push({width,height,scale,language,passed:true});
 }
 await page.setViewportSize({width:400,height:860});await page.evaluate(()=>document.documentElement.style.fontSize='16px');
 for(const [key,target] of [['profile','#profile-section'],['group','#settings'],['buildings','#building-registry'],['incidents','.incident-tools'],['settings','#design-settings']]){
  await page.locator('[data-tab="buildings"]').click();await page.locator('#menu-open').click();await page.locator(`[data-menu="${key}"]`).click();assert.equal(await page.locator('#app-menu').isVisible(),false);assert.equal(await page.locator(target).isVisible(),true,key);
 }
 assert.deepEqual(errors,[]);await writeFile(`${out}/verification.json`,JSON.stringify({base,checks,destinations:5,errors},null,2));console.log('Menu: four viewports/font/language cases, five destinations, focus and Escape passed.');
}finally{await browser.close();await new Promise(r=>server.close(r));}
