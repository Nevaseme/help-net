import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {chromium} from '../../tools/browser-runtime.mjs';
const server=createServer(async(req,res)=>{
 if(req.url==='/sw.js'){res.setHeader('Content-Type','text/javascript');res.end(`self.addEventListener('install',e=>e.waitUntil(self.skipWaiting()));self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));self.addEventListener('message',e=>{if(e.data?.type==='GET_VERSION')e.ports[0]?.postMessage('pland-lab-v1.6.64-r110-helpnet');});`);return;}
 if(req.url==='/app-update.mjs'){res.setHeader('Content-Type','text/javascript');res.end(await readFile(new URL('../web/app-update.mjs',import.meta.url)));return;}
 res.setHeader('Content-Type','text/html');res.end('<!doctype html><html lang="en"><body><form id="test-form"><input id="name" name="name"></form></body></html>');
});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try{
 for(const [version,expected] of [['1.6.63',true],['1.6.64',false]]){
  const context=await browser.newContext(),page=await context.newPage();await page.goto(`http://127.0.0.1:${server.address().port}`);
  // The active worker has already changed before the document attaches listeners.
  await page.evaluate(async()=>{await navigator.serviceWorker.register('/sw.js');await navigator.serviceWorker.ready;});await page.reload();
  await page.evaluate(async version=>{const {setupAppUpdates}=await import('/app-update.mjs');await setupAppUpdates({version});},version);
  if(expected){await page.locator('.update-notice').waitFor({state:'visible'});assert.equal(await page.locator('.update-notice button').innerText(),'Update');await page.locator('#name').fill('unsaved');assert.equal(await page.locator('.update-notice button').isDisabled(),true);await page.evaluate(()=>document.dispatchEvent(new CustomEvent('form-saved',{detail:{form:document.querySelector('form')}})));assert.equal(await page.locator('.update-notice button').isDisabled(),false);}
  else {await page.evaluate(()=>new Promise(r=>setTimeout(r,150)));assert.equal(await page.locator('.update-notice').isVisible(),false);}
  await context.close();
 }
 console.log(JSON.stringify({missedControllerChange:true,unsavedChangesProtected:true,noFalseUpdateForSameVersion:true}));
}finally{await browser.close();server.close();}
