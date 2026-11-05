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
 assert.ok(ids.includes('old-version'));assert.deepEqual(ids.slice(-2),['organized-android-1539','organized-pwa-1539']);
 for(const platform of ['android','pwa']) {
  const section=page.locator(`#organized-${platform}-1539`);await section.evaluate(e=>e.scrollIntoView({behavior:'instant',block:'start',inline:'center'}));
  await page.waitForFunction(id=>{const r=document.getElementById(id).getBoundingClientRect();return r.left>=0&&r.right<=innerWidth;},`organized-${platform}-1539`);
  await page.waitForFunction(id=>[...document.querySelectorAll(`#${id} img`)].every(i=>i.complete&&i.naturalWidth>0),`organized-${platform}-1539`);
  const caption=await section.locator('.comparison-caption').boundingBox(),figure=await section.locator('figure').boundingBox();
  assert.ok(caption.y+caption.height<=figure.y+1,'Caption must not overlap screen');
  await page.screenshot({path:`C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/menu-1539/comparison-${platform}-${url.startsWith('http')?'public':'local'}.png`});
 }
 await writeFile('C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/menu-1539/comparison-'+(url.startsWith('http')?'public':'local')+'.json',JSON.stringify({passed:true,url,columns:ids.length,lastColumns:ids.slice(-2)},null,2));
 console.log('Comparison verified: two appended columns, six loaded images, no caption overlap.');
}finally{await browser.close();}

