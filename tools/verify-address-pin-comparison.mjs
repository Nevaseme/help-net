import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
import {chromium} from './browser-runtime.mjs';
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});const results=[];
try{for(const url of [pathToFileURL(resolve('demo/cloud-sharing.html')).href,...(process.env.LOCAL_ONLY?[]:['https://nevaseme.github.io/safety_confirm/cloud-sharing.html?address-pin=1543'])]){
 const page=await browser.newPage({viewport:{width:1280,height:1000}});await page.goto(url);await page.addStyleTag({content:'*{scroll-behavior:auto!important;scroll-snap-type:none!important}'});const ids=await page.locator('main > section').evaluateAll(es=>es.map(e=>e.id));assert.ok(ids.includes('old-version'));assert.deepEqual(ids.slice(-2),['address-pin-android','address-pin-pwa']);
 for(const id of ids.slice(-2)){const section=page.locator('#'+id);await section.evaluate(e=>e.scrollIntoView({block:'start',inline:'center',behavior:'instant'}));await page.waitForFunction(id=>[...document.querySelectorAll('#'+id+' img')].every(i=>i.complete&&i.naturalWidth>0),id);const a=await section.locator('.comparison-caption').boundingBox(),b=await section.locator('figure').boundingBox();assert.ok(a.y+a.height<=b.y+1);for(const detail of await section.locator('details').all()){await detail.locator('summary').click();assert.ok(await detail.getAttribute('open')!==null);await detail.locator('summary').click();}}
 await page.setViewportSize({width:390,height:844});await page.locator('#address-pin-pwa').evaluate(e=>e.scrollIntoView({inline:'center',behavior:'instant'}));await page.screenshot({path:'output/qa/address-pin-1543/comparison-'+(url.startsWith('http')?'public':'local')+'.png'});results.push({url,columns:ids.length,passed:true});await page.close();}
 await writeFile('.implementation/address-pin/comparison-verification.json',JSON.stringify(results,null,2));console.log(JSON.stringify(results));}finally{await browser.close();}
