import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const out='output/header-proposal-v2';await mkdir(out,{recursive:true});
const url=process.env.PROPOSAL_URL||pathToFileURL(resolve('demo/header-proposal.html')).href;
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});const page=await browser.newPage({viewport:{width:1150,height:1260}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
try {
 await page.goto(url);await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth));
 assert.equal((await page.locator('.header').boundingBox()).height,65);
 assert.equal(await page.locator('#title').textContent(),'訓練');
 await page.screenshot({path:out+'/comparison.png',fullPage:true});
 await page.locator('#menu').click();await page.getByText('あなたの役割：一般住民').waitFor();await page.locator('#proposal').screenshot({path:out+'/menu.png'});await page.keyboard.press('Escape');assert.ok(await page.locator('#menu').evaluate(e=>e===document.activeElement));
 await page.locator('#details').click();await page.getByText('11月1日 10:00',{exact:true}).waitFor();assert.ok(!(await page.locator('#dialog-body').textContent()).includes('町内会'));await page.keyboard.press('Escape');
 await page.locator('[data-state="normal"]').click();assert.equal(await page.locator('#title').textContent(),'');assert.equal(await page.locator('#details').isVisible(),false);
 await page.locator('[data-state="disaster"]').click();assert.equal(await page.locator('#title').textContent(),'地震');const mapBefore=await page.locator('.map').boundingBox();await page.locator('#offline').check();assert.equal(await page.locator('#notice').isVisible(),true);assert.deepEqual(await page.locator('.map').boundingBox(),mapBefore);await page.locator('#proposal').screenshot({path:out+'/offline.png'});
 await page.locator('[data-state="training"]').click();await page.locator('#offline').uncheck();await page.locator('#proposal').screenshot({path:out+'/proposal.png'});
 await page.setViewportSize({width:390,height:1080});await page.locator('#offline').check();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:out+'/mobile.png',fullPage:true});
 await page.setViewportSize({width:320,height:1080});const smallMap=await page.locator('.map').boundingBox();await page.locator('#offline').uncheck();assert.deepEqual(await page.locator('.map').boundingBox(),smallMap);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 assert.deepEqual(errors,[]);await writeFile(out+'/'+(url.startsWith('http')?'public':'local')+'.json',JSON.stringify({passed:true,url,checks:['64px header plus divider','Menu group and role','Details date','Escape focus return','Three states','Offline notice','390px no overflow'],errors},null,2));console.log('Header proposal verified');
}finally{await browser.close();}
