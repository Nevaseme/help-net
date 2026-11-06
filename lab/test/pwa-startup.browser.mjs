import assert from 'node:assert/strict';
import {chromium} from 'file:///C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {startVenueServer} from './venue-server.mjs';
const server=await startVenueServer({cityFixture:true,publicAssets:process.env.VENUE_PUBLIC_ASSETS||null});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try{
 const admin=await browser.newPage();await admin.goto(server.base);
 await admin.evaluate(async()=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity());const group=await c.register('create',{groupName:'Startup check',password:'Startup123',activityName:'Organizer'});await c.startVenue(group.groupId,'',{presentationMode:'EARTHQUAKE'});s.close();});
 const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block',colorScheme:'light'}),page=await context.newPage();
 await context.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});window.safetyBadges=[];new MutationObserver(()=>{const badge=document.querySelector('#own .badge');if(badge)window.safetyBadges.push(badge.textContent);}).observe(document,{childList:true,subtree:true});});
 let releaseRoster,rosterRequests=0;const held=new Promise(r=>releaseRoster=r);
 await page.route('**/api/v1/groups/*/roster',async route=>{rosterRequests++;await held;await route.continue();});
 await page.goto(server.base);await page.locator('#group-join-identity').fill('Startup check');await page.locator('#group-join-password').fill('Startup123');await page.locator('#group-next').click();await page.locator('#group-join-name').fill('Participant');await page.locator('#group-join').click();
 let gatedBeforeFullSync=false;
 try{await page.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled,{},{timeout:3500});gatedBeforeFullSync=true;}catch{}finally{releaseRoster();}
 await page.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);
 await page.locator('#safe').click();await page.locator('#status-update').click();await page.locator('#venue-location-notice').waitFor();await page.getByRole('button',{name:'Continue',exact:true}).click();
 await page.locator('[data-tab="safety"]').click();await page.locator('#sos').click();await page.locator('#status-update').click();await page.waitForFunction(()=>document.querySelector('#own-status-feedback').textContent==='Saved');
 const result={gatedBeforeFullSync,rosterRequests,safetyBadges:await page.evaluate(()=>window.safetyBadges),appearance:await page.locator('html').getAttribute('data-appearance')};console.log(JSON.stringify(result));
 assert.equal(gatedBeforeFullSync,true,'initial safety entry must not wait for a redundant full roster refresh');
 assert.deepEqual(result.safetyBadges,[],'legacy status badges must never flash during refresh');
}finally{await browser.close();await server.close();}
