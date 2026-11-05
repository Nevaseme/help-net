import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const results=[],out='C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/sync-1544';
try {
 for(const url of [pathToFileURL(resolve('demo/cloud-sharing.html')).href,...(process.env.LOCAL_ONLY?[]:['https://nevaseme.github.io/safety_confirm/cloud-sharing.html?sync=1544'])]) {
  const page=await browser.newPage({viewport:{width:1280,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(url);
  await page.addStyleTag({content:'*{scroll-behavior:auto!important;scroll-snap-type:none!important}'});
  const ids=await page.locator('main > section').evaluateAll(es=>es.map(e=>e.id));
  assert.equal(ids.length,45);assert.ok(ids.includes('old-version'));assert.equal(ids.at(-1),'sync-stable-android');
  const section=page.locator('#sync-stable-android');
  await section.evaluate(e=>e.scrollIntoView({block:'start',inline:'center',behavior:'instant'}));
  await page.waitForFunction(()=>[...document.querySelectorAll('#sync-stable-android img')].every(i=>i.complete&&i.naturalWidth>0));
  for(const detail of await section.locator('details').all()) {await detail.locator('summary').click();assert.notEqual(await detail.getAttribute('open'),null);await detail.locator('summary').click();}
  await page.setViewportSize({width:390,height:844});
  await section.evaluate(e=>e.scrollIntoView({inline:'center',behavior:'instant'}));
  await page.screenshot({path:out+'/comparison-'+(url.startsWith('http')?'public':'local')+'.png'});
  assert.deepEqual(errors,[]);results.push({url,columns:ids.length,passed:true});await page.close();
 }
 await writeFile(out+'/comparison-verification.json',JSON.stringify(results,null,2));console.log(JSON.stringify(results));
} finally {await browser.close();}
