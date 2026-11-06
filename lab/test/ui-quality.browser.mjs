import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {chromium} from '../../tools/browser-runtime.mjs';
const root=resolve('lab/web'),out=resolve('output/ui-quality/responsive');await mkdir(out,{recursive:true});
const mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=createServer(async(req,res)=>{try{const name=new URL(req.url,'http://local').pathname;if(name.startsWith('/api/'))throw Error();const path=resolve(root,'.'+(name==='/'?'/index.html':name));if(!path.startsWith(root))throw Error();res.writeHead(200,{'content-type':mime[extname(path)]||'application/octet-stream'});res.end(await readFile(path));}catch{res.writeHead(404);res.end('not found');}});
await new Promise(r=>server.listen(4207,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const context=await browser.newContext({viewport:{width:400,height:860},serviceWorkers:'block'}),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
try{
 await page.goto('http://127.0.0.1:4207/');await page.locator('.leaflet-container').waitFor();
 await page.evaluate(async()=>{
  const {Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B'),id=await s.identity(),other=crypto.randomUUID(),third=crypto.randomUUID();
  await s.importRoster({version:1,groupId:'demo',members:[{memberId:crypto.randomUUID(),deviceIds:[id],role:'ADMIN',activityName:'確認担当（テスト）'},{memberId:crypto.randomUUID(),deviceIds:[other],role:'MEMBER',activityName:'山田 太郎（テスト）'},{memberId:crypto.randomUUID(),deviceIds:[third],role:'MEMBER',activityName:'山田 花子（テスト）'}]},id);
  for(const [originId,name]of [[id,'確認担当（テスト）'],[other,'山田 太郎（テスト）'],[third,'山田 花子（テスト）']])await s.saveProfile({groupId:'demo',originId,revision:1,updatedAt:0,name,latitude:35.097,longitude:139.077,floor:'1階',room:'南側の部屋',safety:'UNREPORTED'});
  const eventId=crypto.randomUUID();await s.commandQuake({groupId:'demo',eventId,actorDeviceId:id,operatedAt:Date.now()-240000,action:'TRAINING_START',training:true,title:'UI確認用訓練',area:'テスト地区'});await s.update('meta','incident:demo',()=>eventId);
  await s.appendIncident({groupId:'demo',eventId:crypto.randomUUID(),incidentId:eventId,authorId:other,authorName:'山田 太郎（テスト）',createdAt:Date.now(),kind:'ANSWER',safety:'SOS'});
  await s.savePosition({groupId:'demo',originId:other,revision:1,acquiredAt:Date.now()-120000,latitude:35.097,longitude:139.077,accuracyMeters:8});
  const reportId=crypto.randomUUID();await s.appendResponse({groupId:'demo',eventId:reportId,incidentId:eventId,actorDeviceId:id,operatedAt:Date.now(),change:{type:'REPORT',reportId,category:'建物倒壊',description:'テスト用の建物倒壊通報です。',latitude:35.097,longitude:139.077},replaces:[]});s.close();
 });
 await page.reload();await page.locator('.leaflet-container').waitFor();await page.waitForTimeout(600);

 const checks=[];
 for(const [width,height,scale,language] of [[400,860,1,'ja'],[320,700,1,'en'],[430,860,1,'ja'],[400,860,2,'ja'],[320,700,2,'en'],[400,460,1,'ja']]) {
  await page.setViewportSize({width,height});
  await page.locator('#menu-open').click();await page.locator('[data-menu="settings"]').click();await page.locator(`[name="ui-language"][value="${language}"]`).check();await page.locator('#design-settings .sheet-close').click();
  await page.evaluate(scale=>document.documentElement.style.fontSize=`${16*scale}px`,scale);
  for(const tab of ['residents','response','safety','buildings']) {
   await page.locator(`[data-tab="${tab}"]`).click();
   await page.waitForTimeout(80);
   if(!(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))) {console.log(await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(e=>{const r=e.getBoundingClientRect();return r.width&&(r.right>innerWidth+1||e.scrollWidth>e.clientWidth+1)}).map(e=>({tag:e.tagName,id:e.id,cls:e.className,width:e.getBoundingClientRect().width,right:e.getBoundingClientRect().right,scroll:e.scrollWidth,client:e.clientWidth})).slice(0,25)));await page.screenshot({path:out+'/overflow.png'});}
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`overflow ${width} ${scale} ${tab}`);
   const nav=await page.locator('.app-nav').boundingBox();assert.ok(nav.y>=0&&nav.y+nav.height<=height+1,'tabs remain visible');
   await page.screenshot({path:`${out}/${tab}-${width}-${height}-${scale}-${language}.png`});
  }
  await page.locator('[data-tab="response"]').click();await page.locator('#response-list [data-case]').filter({hasText:language==='ja'?'建物倒壊':'Building collapse'}).click();
  const footer=page.locator('#case-sheet .sheet-footer button');assert.equal(await footer.isVisible(),true);const box=await footer.boundingBox(),nav=await page.locator('.app-nav').boundingBox();assert.ok(box.y>=0&&box.y+box.height<=nav.y+1,'primary action visible');
  await page.screenshot({path:`${out}/detail-${width}-${height}-${scale}-${language}.png`});
  await page.locator('[data-edit="progress"]').click();assert.equal(await page.locator('#case-sheet').getAttribute('inert'),'');await page.locator('#response-editor .sheet-close').click();assert.equal(await page.locator('#case-sheet').getAttribute('inert'),null);assert.equal(await page.locator('[data-edit="progress"]').evaluate(el=>el===document.activeElement),true);
  await page.locator('#case-sheet .sheet-close').click();await page.locator('[data-tab="buildings"]').click();
  for(const selector of ['#menu-open','#tsunami-map .leaflet-control-zoom-in','#tsunami-map .leaflet-control-zoom-out','#tsunami-fit']){const box=await page.locator(selector).boundingBox();assert.ok(box.width>=44&&box.height>=44,selector);}
  checks.push({width,height,scale,language,passed:true});
 }
 assert.deepEqual(errors,[]);await writeFile(`${out}/verification.json`,JSON.stringify({checks,consoleErrors:errors},null,2));console.log('UI quality: 6 viewport/font/language cases passed');
}finally{await browser.close();await new Promise(r=>server.close(r));}
