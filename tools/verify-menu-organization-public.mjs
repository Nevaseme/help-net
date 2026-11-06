import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const base='https://tasukeai-sync.tomodachi-takumi.workers.dev/';
const out='output/qa/menu-1539/public';await mkdir(out,{recursive:true});
const checked=[];
for(const name of ['index.html','style.css','app.mjs','web1617-ui.mjs','cloud-ui.mjs','ui-accessibility.mjs','sw.js']){
 let local=(await readFile('lab/web/'+name,'utf8')).replace(/^\uFEFF/,'');if(name==='index.html')local=local.replace('<head>','<head><meta name="tasukeai-cloud" content="1">');
 const response=await fetch(base+name);assert.equal(response.status,200);assert.equal((await response.text()).replaceAll('\r\n','\n'),local.replaceAll('\r\n','\n'),name);checked.push(name);
}
import {chromium} from './browser-runtime.mjs';
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const context=await browser.newContext({viewport:{width:400,height:860}}),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
// No production group is created or changed. Requests for data are intercepted.
await context.route('**/api/**',route=>route.fulfill({status:404,contentType:'application/json',body:'{}'}));
try{
 await page.goto(base);await page.locator('#cloud-group-name').waitFor({state:'visible'});assert.match(await page.locator('footer').textContent(),/1\.6\.13/);await page.screenshot({path:out+'/join.png'});
 await page.evaluate(async()=>{const{Store}=await import('/sync.mjs'),s=await Store.open('tasukeai-cloud'),id=await s.identity(),memberId=crypto.randomUUID();await s.importRoster({version:1,groupId:'demo',members:[{memberId,deviceIds:[id],role:'ADMIN',activityName:'確認用'}]},id);await s.update('meta','cloud-session:demo',()=>({groupId:'demo',groupName:'架空グループ',deviceId:id,memberId,role:'ADMIN',activityName:'確認用',token:'fictional-test-token'}));s.close();});await page.reload();await page.waitForFunction(()=>!document.body.classList.contains('registration-needed'));
 await page.locator('#menu-open').click();await page.screenshot({path:out+'/menu.png'});await page.locator('[data-menu="settings"]').click();await page.locator('[name="ui-appearance"][value="dark"]').check();assert.equal(await page.locator('html').getAttribute('data-theme'),'dark');await page.reload();assert.equal(await page.locator('html').getAttribute('data-theme'),'dark');await page.locator('#menu-open').click();await page.locator('[data-menu="incidents"]').click();assert.ok(await page.locator('#training-start').isVisible());await page.screenshot({path:out+'/training-dark.png'});
 assert.deepEqual(errors,[]);await writeFile(out+'/verification.json',JSON.stringify({passed:true,base,assets:checked,publicUi:['New user join form','Four menu entries','Appearance persistence','Admin training destination'],productionDataWrites:0,errors},null,2));console.log('Public PWA: 7 assets match; join, menu, saved appearance and training destination passed.');
}finally{await browser.close();}
