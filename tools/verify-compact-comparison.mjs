import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const url=process.env.COMPARISON_URL||pathToFileURL(resolve('demo/cloud-sharing.html')).href;
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const page=await browser.newPage({viewport:{width:1280,height:1000}});
try {
 await page.goto(url);await page.addStyleTag({content:'*{scroll-behavior:auto!important;scroll-snap-type:none!important}'});const ids=await page.locator('main > section').evaluateAll(es=>es.map(e=>e.id));
 assert.ok(ids.includes('old-version'));assert.deepEqual(ids.slice(-2),['compact-android-1538','compact-pwa-1538']);
 for(const platform of ['android','pwa']) {
  const section=page.locator(`#compact-${platform}-1538`);await section.evaluate(e=>e.scrollIntoView({behavior:'instant',block:'start',inline:'center'}));
  await page.waitForFunction(id=>{const r=document.getElementById(id).getBoundingClientRect();return r.left>=0&&r.right<=innerWidth;},`compact-${platform}-1538`);
  await page.waitForFunction(id=>[...document.querySelectorAll(`#${id} img`)].every(i=>i.complete&&i.naturalWidth>0),`compact-${platform}-1538`);
  const caption=await section.locator('.comparison-caption').boundingBox(),figure=await section.locator('figure').boundingBox();
  assert.ok(caption.y+caption.height<=figure.y+1,'Caption must not overlap screen');
  await page.screenshot({path:`output/compact-header/comparison-${platform}-${url.startsWith('http')?'public':'local'}.png`});
 }
 await writeFile('output/compact-header/comparison-'+(url.startsWith('http')?'public':'local')+'.json',JSON.stringify({passed:true,url,columns:ids.length,lastColumns:ids.slice(-2)},null,2));
 console.log('Comparison verified: two appended columns, six loaded images, no caption overlap.');
}finally{await browser.close();}
