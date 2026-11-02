// Real solver, real Worker, real offline cache. Dedicated test browser only.
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE?pathToFileURL(process.env.PLAYWRIGHT_MODULE).href:'playwright');
const base=process.env.PWA_TEST_URL||'http://127.0.0.1:8799';
const out=resolve(process.env.PWA_TEST_OUTPUT||'output/playwright/tsunami-runtime');await mkdir(out,{recursive:true});
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,headless:true});
const context=await browser.newContext({viewport:{width:400,height:860}});
const page=await context.newPage(),errors=[],checks=[],timings={};page.on('pageerror',e=>errors.push(e.message));
const note=s=>{checks.push(s);console.log(s);};
async function menu(name){await page.locator('#menu-open').click();await page.locator(`[data-menu="${name}"]`).click();}
async function calculate(height){
 await page.locator('[data-tab="buildings"]').click();
 if(!await page.locator('#tsunami-height').isVisible())await page.locator('#tsunami-panel > summary').click();
 await page.locator('#tsunami-height').fill(String(height));await page.locator('#tsunami-calculate').click();
 const start=Date.now();
 await page.waitForFunction(()=>['success','error'].includes(document.querySelector('#tsunami-status').dataset.status),null,{timeout:1200000,polling:1000});
 timings[height]=(Date.now()-start)/1000;
 const message=await page.locator('#tsunami-status').textContent();
 assert.equal(await page.locator('#tsunami-status').getAttribute('data-status'),'success',message);
 return message;
}
try{
 await page.goto(base);await page.locator('#cloud-create').waitFor();
 await page.locator('#cloud-group-name').fill('津波画面検証・架空');await page.locator('#cloud-admin-name').fill('架空管理者');
 await page.locator('#cloud-create button').click();await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('管理者'));
 await menu('profile');await page.locator('#name').fill('架空管理者');await page.locator('#lat').fill('35.095');await page.locator('#lon').fill('139.07');await page.locator('#profile-save').click();
 await page.waitForFunction(()=>document.querySelector('#profile-feedback').dataset.status==='success');
 await menu('incidents');await page.locator('#training-area').fill('架空試験地区');await page.locator('#training-start').click();
 await page.waitForFunction(()=>document.querySelector('header h1').textContent==='訓練');
 await page.locator('[data-tab="safety"]').click();assert.equal(await page.locator('#safe').isEnabled(),true);
 await page.locator('#safe').click();await page.waitForFunction(()=>document.querySelector('#safety-feedback').dataset.status==='success');note('Admin alone starts explicit training and saves own safety');
 await page.locator('[data-tab="buildings"]').click();await page.locator('#tsunami-panel > summary').click();
 await page.locator('#tsunami-height').fill('12.5');await page.locator('#tsunami-calculate').click();
 const start=Date.now();await page.waitForTimeout(1500);
 await page.locator('[data-tab="residents"]').click();assert.equal(await page.locator('#residents').isVisible(),true);
 await page.locator('[data-tab="safety"]').click();assert.equal(await page.locator('#safety').isVisible(),true);
 await page.locator('[data-tab="buildings"]').click();note('Tabs respond while the real worker computes 12.5 m');
 await page.waitForFunction(()=>['success','error'].includes(document.querySelector('#tsunami-status').dataset.status),null,{timeout:1200000,polling:1000});
 timings['12.5']=(Date.now()-start)/1000;
 assert.equal(await page.locator('#tsunami-status').getAttribute('data-status'),'success',await page.locator('#tsunami-status').textContent());
 assert.ok(Number(await page.locator('#tsunami-map').getAttribute('data-cells'))>0);
 await page.screenshot({path:out+'/height-12.5.png'});
 const resultText=await page.locator('#tsunami-conditions').textContent();assert.match(resultText,/12.5m/);note('12.5 m computed and accepted by both domain-size checks');
 await page.locator('#tsunami-panel > summary').click();await page.screenshot({path:out+'/map-12.5.png'});
 await calculate(0);assert.equal(await page.locator('#tsunami-map').getAttribute('data-cells'),'0');note('Zero-height baseline has no land inundation');
 await page.locator('#tsunami-height').fill('20');await page.locator('#tsunami-calculate').click();await page.waitForTimeout(1000);await page.locator('#tsunami-cancel').click();
 await page.waitForFunction(()=>document.querySelector('#tsunami-status').dataset.status==='error');assert.match(await page.locator('#tsunami-status').textContent(),/中止/);assert.equal(await page.locator('#tsunami-map').getAttribute('data-case-id'),null);note('Cancel clears previous result and releases worker');
 await page.waitForFunction(()=>document.querySelector('#offline').textContent.includes('準備ができました'));
 await context.setOffline(true);await page.reload();await page.locator('.leaflet-container').waitFor();
 await calculate(0);await menu('profile');assert.equal(await page.locator('#name').inputValue(),'架空管理者');note('Offline reload retains profile and computes using cached terrain and worker');
 assert.deepEqual(errors,[]);
 await writeFile(out+'/verification.json',JSON.stringify({passed:true,base,checks,timings,errors,resultText,realSolver:true},null,2));
}catch(error){await page.screenshot({path:out+'/failure.png'}).catch(()=>{});await writeFile(out+'/verification.json',JSON.stringify({passed:false,base,checks,timings,errors,error:String(error)},null,2));throw error;}
finally{await browser.close();}
