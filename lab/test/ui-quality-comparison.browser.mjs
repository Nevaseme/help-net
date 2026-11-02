import assert from 'node:assert/strict';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try {
 const page=await browser.newPage({viewport:{width:1280,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(process.env.COMPARISON_URL||pathToFileURL(resolve('demo/cloud-sharing.html')).href);
 assert.ok(await page.locator('.sharing-view').count()>8);
 for(const platform of ['pwa','android','vivo']) {assert.equal(await page.locator(`[data-quality-${platform}]`).count(),7);}
 for(const platform of ['pwa','android','vivo'])for(const button of await page.locator(`[data-quality-${platform}]`).all()) {
  await button.click();await page.waitForFunction(id=>{const image=document.getElementById(id);return image.complete&&image.naturalWidth>0;},`quality-${platform}-image`);
  assert.equal(await button.getAttribute('aria-pressed'),'true');
 }
 for(const platform of ['pwa','android']) {
  const section=page.locator('#simple-'+platform),image=section.locator(':scope > figure img');
  await image.scrollIntoViewIfNeeded();await image.evaluate(el=>el.decode());assert.ok(await image.evaluate(el=>el.naturalWidth>0));
  await section.locator('details summary').click();
  const legend=section.locator('details img');await legend.scrollIntoViewIfNeeded();await legend.evaluate(el=>el.decode());assert.ok(await legend.evaluate(el=>el.naturalWidth>0));
 }
 const sync=page.locator('#group-production-android');
 await sync.locator('figure img').scrollIntoViewIfNeeded();await sync.locator('figure img').evaluate(el=>el.decode());
 await sync.locator('summary').click();await sync.locator('details img').scrollIntoViewIfNeeded();await sync.locator('details img').evaluate(el=>el.decode());
 for(const platform of ['android','pwa']){
  const menu=page.locator(`#menu-${platform}-1536`);
  await menu.locator('figure img').scrollIntoViewIfNeeded();await menu.locator('figure img').evaluate(el=>el.decode());
  await menu.locator('summary').click();await menu.locator('details img').scrollIntoViewIfNeeded();await menu.locator('details img').evaluate(el=>el.decode());
 }
 assert.deepEqual(errors,[]);console.log('Previous comparisons preserved; screen switches, legends and stable-sync images pass.');
}finally{await browser.close();}
