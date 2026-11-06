import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {chromium} from '../../tools/browser-runtime.mjs';
import {startVenueServer} from './venue-server.mjs';

const server=await startVenueServer({cityFixture:true,publicAssets:process.env.VENUE_PUBLIC_ASSETS||null});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'}),page=await context.newPage();
const errors=[];page.on('pageerror',e=>errors.push(e.message));
await context.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});window.startupMapFrames=[];function frame(){const map=document.querySelector('#buildings');if(map?.checkVisibility({checkVisibilityCSS:true}))window.startupMapFrames.push(document.body.dataset.page);requestAnimationFrame(frame);}requestAnimationFrame(frame);});
let release;
await page.route('**/app.mjs',async route=>{await new Promise(r=>{release=r;});await route.continue();});
async function launch(reload=false){release=null;const navigation=reload?page.reload({waitUntil:'commit'}):page.goto(server.base,{waitUntil:'commit'});await navigation;await page.locator('#buildings').waitFor({state:'attached'});await page.waitForTimeout(300);assert.equal(await page.locator('#buildings').isVisible(),false,'Map must not be the initial HTML screen');assert.equal(await page.locator('#startup-screen').isVisible(),true);assert.match(await page.locator('#startup-screen').innerText(),/Loading/);assert.ok(release);release();}
try{
 await mkdir('output/playwright/startup',{recursive:true});await launch();await page.locator('#group-access').waitFor({state:'visible'});assert.deepEqual(await page.evaluate(()=>window.startupMapFrames),[]);assert.equal(await page.locator('#startup-screen').isVisible(),false);await page.screenshot({path:'output/playwright/startup/new-install.png'});
 // A saved participant still owes the first answer for an already-started earthquake.
 await page.evaluate(async()=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity());try{const g=await c.register('create',{groupName:'Startup check',password:'StartupTest123',activityName:'Participant'});await c.startVenue(g.groupId,'',{presentationMode:'EARTHQUAKE'});await c.sync(g.groupId);}finally{s.close();}});
 await launch(true);await page.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled&&document.body.dataset.startup==='ready');assert.deepEqual(await page.evaluate(()=>window.startupMapFrames),[]);await page.screenshot({path:'output/playwright/startup/unanswered.png'});
 await page.locator('#safe').click();await page.locator('#status-update').click();await page.locator('#venue-location-notice').waitFor({state:'visible'});await page.locator('#venue-location-notice button').click();await page.locator('#tsunami-map .maplibregl-canvas').waitFor();
 await launch(true);await page.waitForFunction(()=>document.body.dataset.startup==='ready'&&document.body.dataset.page==='buildings');await page.locator('#tsunami-map .maplibregl-canvas').waitFor();assert.equal(await page.locator('#venue-location-notice').isVisible(),false);assert.equal(await page.locator('#startup-screen').isVisible(),false);
 assert.deepEqual(errors,[]);console.log(JSON.stringify({newInstallNoMapFlash:true,savedUnansweredNoMapFlash:true,answeredReturnsToMap:true,loadingVisible:true,errors}));
}finally{release?.();await browser.close();await server.close();}
