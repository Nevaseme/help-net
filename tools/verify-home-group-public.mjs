import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const base='https://tasukeai-sync.tomodachi-takumi.workers.dev/';
const out='C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/home-1540/pwa';await mkdir(out,{recursive:true});
for(const name of ['index.html','app.mjs','cloud-ui.mjs','sw.js']){
 let local=(await readFile('lab/web/'+name,'utf8')).replace(/^\uFEFF/,'');if(name==='index.html')local=local.replace('<head>','<head><meta name="tasukeai-cloud" content="1">');
 const response=await fetch(base+name);assert.equal(response.status,200);assert.equal((await response.text()).replaceAll('\r\n','\n'),local.replaceAll('\r\n','\n'),name);
}
const{chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const context=await browser.newContext({viewport:{width:400,height:860}}),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
await context.route('**/api/**',route=>route.fulfill({status:404,contentType:'application/json',body:'{}'}));
try{
 await page.goto(base);await page.locator('#cloud-group-name').waitFor({state:'visible'});assert.match(await page.locator('footer').textContent(),/1\.6\.14/);
 await page.evaluate(async()=>{const{Store}=await import('/sync.mjs'),s=await Store.open('tasukeai-cloud'),id=await s.identity(),memberId=crypto.randomUUID();await s.update('meta','cloud-session:demo',()=>({groupId:'demo',groupName:'架空グループ',deviceId:id,memberId,role:'MEMBER',activityName:'確認用',token:'fictional-test-token'}));s.close();});await page.reload();await page.waitForFunction(()=>!document.body.classList.contains('registration-needed'));
 await page.locator('#menu-open').click();await page.locator('[data-menu="group"]').click();await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('架空グループ'));
 assert.equal(await page.locator('#cloud-group-state').textContent(),'架空グループ · 確認用');await page.screenshot({path:out+'/group.png',clip:{x:0,y:0,width:400,height:410}});
 await page.evaluate(async()=>{const{Store}=await import('/sync.mjs'),s=await Store.open('tasukeai-cloud');await s.update('meta','cloud-session:demo',x=>({...x,role:'ADMIN'}));s.close();});await page.reload();await page.locator('#menu-open').click();await page.locator('[data-menu="group"]').click();await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.endsWith(' · 管理者'));assert.deepEqual(errors,[]);
 await writeFile(out+'/verification.json',JSON.stringify({passed:true,version:'1.6.14',productionDataWrites:0,checks:['Four published assets match','Member group omits redundant role','Administrator still identifiable'],errors},null,2));console.log('Public member/admin group checks passed');
}finally{await browser.close();}
