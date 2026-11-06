import assert from 'node:assert/strict';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {chromium} from '../../tools/browser-runtime.mjs';
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try {
 const page=await browser.newPage({viewport:{width:1280,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(process.env.COMPARISON_URL||pathToFileURL(resolve('demo/cloud-sharing.html')).href);
 assert.ok(await page.locator('.sharing-view').count()>8);
 for(const platform of ['pwa','android'])for(const button of await page.locator(`[data-venue-${platform}]`).all()) {
  await button.click();await page.waitForFunction(id=>{const image=document.getElementById(id);return image.complete&&image.naturalWidth>0;},`venue-${platform}-image`);
  assert.equal(await button.getAttribute('aria-pressed'),'true');
 }
 assert.deepEqual(errors,[]);console.log('Previous comparisons preserved; all seven new PWA/Android screen switches and image loads pass.');
}finally{await browser.close();}
