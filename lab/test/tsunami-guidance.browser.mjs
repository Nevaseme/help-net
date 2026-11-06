import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {chromium} from '../../tools/browser-runtime.mjs';
import {startVenueServer} from './venue-server.mjs';

const server=await startVenueServer({cityFixture:true,publicAssets:process.env.VENUE_PUBLIC_ASSETS||null});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'}),page=await context.newPage();
const errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(30000);
await context.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});});
await page.route('**/tsunami-map.mjs',async route=>{const r=await route.fetch();await route.fulfill({response:r,body:(await r.text()).replace("L.control.zoom({position:'topright'}).addTo(map);","window.qaMap=map;L.control.zoom({position:'topright'}).addTo(map);")});});
async function start(next=false){await page.evaluate(async next=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity());try{let group=await s.read('meta','group');if(next)await c.endVenue(group);else group=(await c.register('create',{groupName:'Tsunami guidance',password:'LocalCheck123',activityName:'Organizer'})).groupId;await c.startVenue(group,'',{presentationMode:next?'DRILL':'EARTHQUAKE'});await c.sync(group);}finally{s.close();}},next);await page.reload();await page.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);await page.locator('#safe').click();await page.locator('#status-update').click();await page.locator('#venue-location-notice').waitFor({state:'visible'});assert.equal(await page.evaluate(()=>document.activeElement.id),'venue-location-title');await page.locator('#venue-location-notice button').click();await page.waitForFunction(()=>!!window.qaMap);}
async function placement(){return page.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');try{const g=await s.read('meta','group');return JSON.stringify({group:g,venue:await s.read('meta','venue:'+g)});}finally{s.close();}});}
try{
 await mkdir('output/playwright/tsunami-guidance',{recursive:true});await page.goto(server.base);await start();
 const before=await placement();await page.locator('#map-tsunami').click();const dialog=page.locator('#tsunami-guidance');await dialog.waitFor({state:'visible'});
 assert.match(await dialog.innerText(),/Atami only\. Enter the tsunami height in meters \(0–35\)/);
 assert.equal(await page.evaluate(()=>document.activeElement.id),'tsunami-guidance-title');assert.equal(await dialog.locator('h2').evaluate(e=>getComputedStyle(e).outlineStyle),'none');assert.equal(await page.locator('#tsunami-height').evaluate(e=>e===document.activeElement),false);
 await page.screenshot({path:'output/playwright/tsunami-guidance/english.png'});
 await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>document.activeElement.hasAttribute('data-atami')),true);
 await dialog.locator('[data-atami]').click();assert.equal(await dialog.isVisible(),false);const center=await page.evaluate(()=>window.qaMap.getCenter());assert.ok(Math.abs(center.lat-35.095)<.001&&Math.abs(center.lng-139.07)<.001);assert.equal(await placement(),before);assert.equal(await page.locator('#tsunami-height').evaluate(e=>e===document.activeElement),false);
 await page.locator('#tsunami-height').fill('5');await page.waitForFunction(()=>document.querySelector('#tsunami-status').dataset.status==='success',null,{timeout:60000});
 await page.screenshot({path:'output/playwright/tsunami-guidance/atami.png'});
 await page.locator('#map-tsunami').click();await page.locator('#map-tsunami').click();assert.equal(await dialog.isVisible(),false);
 await page.reload();await page.locator('[data-tab="buildings"]').click();await page.locator('#map-tsunami').click();assert.equal(await dialog.isVisible(),false);
 await page.evaluate(()=>localStorage.setItem('tasukeai-language','ja'));await start(true);await page.locator('#map-tsunami').click();await dialog.waitFor({state:'visible'});assert.match(await dialog.innerText(),/熱海のみ対応/);assert.match(await dialog.innerText(),/0〜35m/);assert.equal(await page.evaluate(()=>document.activeElement.id),'tsunami-guidance-title');await page.screenshot({path:'output/playwright/tsunami-guidance/japanese.png'});
 await dialog.locator('[data-close]').click();assert.equal(await dialog.isVisible(),false);assert.equal(await page.locator('#tsunami-height').evaluate(e=>e===document.activeElement),false);assert.deepEqual(errors,[]);
 console.log(JSON.stringify({firstOnlyPerIncident:true,reloadKeepsSeen:true,nextIncidentShowsAgain:true,atamiCameraOnly:true,height5Works:true,englishJapanese:true,headingFocus:true,noInputAutofocus:true,keyboardAccessible:true,errors}));
}finally{await browser.close();await server.close();}
