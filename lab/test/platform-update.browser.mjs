import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {extname,resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const out='output/platform-quality/upgrade-current';await mkdir(out,{recursive:true});
const old=new Map();for(const f of ['index.html','app.mjs','sw.js','style.css','web1617-ui.mjs','cloud-ui.mjs']){const r=await fetch('https://tasukeai-sync.tomodachi-takumi.workers.dev/'+f);assert.ok(r.ok);old.set('/'+f,await r.text());}
const oldVersion=old.get('/app.mjs').match(/PWA (\d+\.\d+\.\d+)/)?.[1];assert.ok(oldVersion);
const candidateVersion=(await readFile('lab/web/app.mjs','utf8')).match(/PWA (\d+\.\d+\.\d+)/)?.[1];assert.ok(candidateVersion);
const candidateCache='pland-quality-candidate-1';
const fixtureSource=await readFile('output/platform-audit/server.mjs','utf8');const seed=fixtureSource.split('const seed=`')[1].split('`;')[0];
let version=0;const root=resolve('lab/web'),mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.webmanifest':'application/manifest+json'};
const server=createServer(async(req,res)=>{try{let path=new URL(req.url,'http://local').pathname;if(path==='/')path='/index.html';if(path.startsWith('/api/'))throw Error();let body;
 if(path==='/audit-fixture.mjs')body=seed;else if(!version&&old.has(path))body=old.get(path);else body=await readFile(resolve(root,'.'+path));
 if(path==='/index.html')body=body.toString().replace('<meta name="tasukeai-cloud" content="1">','');
 if(path==='/app.mjs')body="import './audit-fixture.mjs';\n"+body;
 if(path==='/sw.js'){body=body.toString().replace("self.addEventListener('install'","FILES.push('/audit-fixture.mjs');\nself.addEventListener('install'");if(version)body=body.replace(/const CACHE\s*=\s*'[^']+'/ ,`const CACHE='${version===1?candidateCache:'pland-quality-candidate-2'}'`);}
 res.writeHead(200,{'Content-Type':mime[extname(path)]||'text/plain','Cache-Control':'no-store'});res.end(body);
}catch{res.writeHead(404);res.end('{}');}});await new Promise(r=>server.listen(4229,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});const context=await browser.newContext({viewport:{width:400,height:860}}),page=await context.newPage(),checks=[],errors=[];page.on('pageerror',e=>errors.push(e.message));
const until=async(fn)=>{for(let i=0;i<100;i++){if(await fn())return;await new Promise(r=>setTimeout(r,100));}throw Error('Update condition timed out');};
const editor=async()=>{await page.locator('[data-tab="response"]').click();await page.locator('.case-row').filter({hasText:'建物倒壊'}).click();await page.locator('[data-edit="progress"]').click();};
try{
 await page.goto('http://127.0.0.1:4229/');await page.waitForFunction(()=>document.querySelector('#offline').textContent.includes('準備ができました'));await page.reload();
 await page.waitForFunction(v=>document.querySelector('footer').textContent.includes(v),oldVersion);const identity=await page.evaluate(async()=>{const{Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B');const id=await s.identity();s.close();return id;});
 version=1;await page.evaluate(async()=>{await(await navigator.serviceWorker.getRegistration()).update();});
 await page.evaluate(()=>window.qualityDocumentToken='unchanged');
 await until(()=>page.evaluate(cache=>new Promise(resolve=>{const channel=new MessageChannel();const timer=setTimeout(()=>resolve(false),250);channel.port1.onmessage=e=>{clearTimeout(timer);resolve(e.data===cache);};navigator.serviceWorker.controller?.postMessage({type:'GET_VERSION'},[channel.port2]);}),candidateCache));
 // An old open page is not forcibly reloaded or cleared.
 assert.equal(await page.evaluate(()=>window.qualityDocumentToken),'unchanged');await page.reload();await page.waitForFunction(v=>document.querySelector('footer').textContent.includes(v),candidateVersion);checks.push(`Published ${oldVersion} cache updates without closing all tabs; open document is not forcibly reloaded`);
 await editor();await page.locator('[name="result"]').fill('更新をまたぐ下書き');
 version=2;await page.evaluate(async()=>{await(await navigator.serviceWorker.getRegistration()).update();});await page.locator('.update-notice').waitFor({state:'visible'});
 assert.equal(await page.locator('[name="result"]').inputValue(),'更新をまたぐ下書き');await page.screenshot({path:out+'/update-ready.png'});
 await page.locator('[data-tab="buildings"]').click();await page.locator('#menu-open').click();await page.locator('[data-menu="profile"]').click();await page.locator('#name').fill('未保存プロフィール（架空）');assert.ok(await page.locator('.update-notice button').isDisabled());
 await page.locator('#profile-save').click();await page.waitForFunction(()=>document.getElementById('profile-feedback').dataset.status==='success');await until(()=>page.locator('.update-notice button').isEnabled());checks.push('Unsaved profile blocks reload until successful save');
 await page.locator('.update-notice button').click();await page.waitForFunction(()=>!document.querySelector('.update-notice')||document.querySelector('.update-notice').hidden);await editor();assert.equal(await page.locator('[name="result"]').inputValue(),'更新をまたぐ下書き');checks.push('Update is explicit; response draft survives reload');
 await context.setOffline(true);await page.reload();await page.locator('.leaflet-container').waitFor();await editor();assert.equal(await page.locator('[name="result"]').inputValue(),'更新をまたぐ下書き');
 const restored=await page.evaluate(async()=>{const{Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B');const result={id:await s.identity(),roster:await s.roster('demo')};s.close();return result;});assert.equal(restored.id,identity);assert.equal(restored.roster.members.length,2);checks.push('Offline restart retains identity, membership and draft');
 const freshContext=await browser.newContext();const fresh=await freshContext.newPage();
 try {version=1;await fresh.goto('http://127.0.0.1:4229/');await fresh.waitForFunction(()=>!!navigator.serviceWorker.controller);assert.equal(await fresh.locator('.update-notice').isVisible(),false);
 version=2;await fresh.evaluate(async()=>{await(await navigator.serviceWorker.getRegistration()).update();});await fresh.locator('.update-notice').waitFor({state:'visible'});checks.push('First installation stays quiet; its next update is announced without an intermediate reload');
 }finally{await freshContext.close();}
 assert.deepEqual(errors,[]);await writeFile(out+'/verification.json',JSON.stringify({passed:true,checks,errors},null,2));console.log('PWA live-cache update passed');
}catch(e){await page.screenshot({path:out+'/failure.png'});await writeFile(out+'/verification.json',JSON.stringify({passed:false,checks,errors,error:String(e)},null,2));throw e;}finally{await browser.close();await new Promise(r=>server.close(r));}
