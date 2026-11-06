import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {chromium} from 'file:///C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {startVenueServer} from './venue-server.mjs';

const out=process.env.LATE_JOIN_OUTPUT||'output/leave-group-20261107';await mkdir(out,{recursive:true});
const server=await startVenueServer({cityFixture:true,publicAssets:process.env.VENUE_PUBLIC_ASSETS||null});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const errors=[],results=[];
async function page(){
 const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
 await context.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});});
 const p=await context.newPage();p.setDefaultTimeout(30000);p.on('pageerror',e=>errors.push(e.message));await p.goto(server.base);return p;
}
try {
 for(const mode of ['EARTHQUAKE','DRILL']) {
  const admin=await page(),groupName='Already started '+mode,password='LateJoin123';
  // Start the incident before a member device or browser exists.
  const started=await admin.evaluate(async({groupName,password,mode})=>{
   const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');
   const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity());
   const group=await c.register('create',{groupName,password,activityName:'Organizer'});
   const venue=await c.startVenue(group.groupId,'',{presentationMode:mode});s.close();return venue;
  },{groupName,password,mode});
  const member=await page();
  await member.locator('#group-join-identity').fill(groupName);await member.locator('#group-join-password').fill(password);
  await member.locator('#group-next').click();await member.locator('#group-join-name').fill('Late participant');await member.locator('#group-join').click();
  await member.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);
  assert.equal(await member.locator('header h1').innerText(),mode==='EARTHQUAKE'?'Earthquake':'Drill');
  const state=await member.evaluate(async()=>{
   const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud'),id=await s.identity(),group=await s.read('meta','group'),venue=await s.read('meta','venue:'+group);
   const selection=await s.read('meta','incident:'+group);s.close();return {id,venue,selection};
  });
  assert.equal(state.venue.sessionId,started.sessionId);assert.equal(state.venue.startedAt,started.startedAt);
  assert.equal(state.venue.placements.length,2);
  const own=state.venue.placements.find(p=>p.deviceId===state.id);assert.ok(own?.home?.buildingId);assert.equal(own.communityId,'demo-central');
  for(const tab of ['buildings','residents','response'])assert.equal(await member.locator(`[data-tab="${tab}"]`).isDisabled(),true);
  assert.equal(await member.locator('#menu-open').isDisabled(),true);
  await member.screenshot({path:`${out}/${mode}-joined-safety.png`});
  await member.locator('#safe').click();await member.locator('#status-update').click();
  await member.locator('#venue-location-notice').waitFor({state:'visible'});
  await member.getByRole('button',{name:'Continue',exact:true}).click();
  assert.equal(await member.locator('body').getAttribute('data-page'),'buildings');
  await member.locator('[data-tab="safety"]').click();await member.locator('#sos').click();await member.locator('#status-update').click();
  await member.waitForFunction(()=>document.querySelector('#own-status-feedback').textContent==='Saved');
  assert.equal(await member.locator('body').getAttribute('data-page'),'safety');assert.equal(await member.locator('#venue-location-notice').isVisible(),false);
  await member.reload();await member.waitForFunction(()=>document.querySelector('header h1').textContent.includes('Earthquake')||document.querySelector('header h1').textContent.includes('Drill'));
  assert.equal(await member.locator('#venue-location-notice').isVisible(),false);assert.equal(await member.locator('#group-access').isVisible(),false);
  await member.waitForFunction(()=>document.querySelector('#network').textContent==='Shared');
  await member.locator('#menu-open').click();
  await member.locator('[data-menu=group]').click();
  await member.locator('#group-settings-open').click();
  member.once('dialog',d=>d.dismiss());await member.locator('#group-leave').click();
  assert.equal(await member.locator('#group-leave').isVisible(),true);
  await member.route('**/api/v1/groups/*/access',route=>route.abort());
  member.once('dialog',d=>d.accept());await member.locator('#group-leave').click();
  await member.waitForFunction(()=>document.querySelector('#group-leave-feedback').textContent.includes('Could not leave'));
  assert.equal(await member.locator('#group-leave').isVisible(),true);
  await member.unroute('**/api/v1/groups/*/access');
  member.once('dialog',d=>d.accept());await member.locator('#group-leave').click();
  await member.locator('#group-access').waitFor({state:'visible'});
  assert.equal(await member.locator('#group-leave').isVisible(),false);
  await member.reload();await member.locator('#group-access').waitFor({state:'visible'});
  await member.locator('#group-join-identity').fill(groupName);await member.locator('#group-join-password').fill(password);
  await member.locator('#group-next').click();await member.locator('#group-join-name').fill('Late participant');await member.locator('#group-join').click();
  await member.waitForFunction(()=>!document.body.classList.contains('registration-needed'));
  const rejoined=await member.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud'),g=await s.read('meta','group'),v=await s.read('meta','venue:'+g);s.close();return v;});
  assert.equal(rejoined.sessionId,started.sessionId);assert.equal(rejoined.placements.length,2);
  results.push({mode,cancelPreservesMembership:true,networkFailurePreservesMembership:true,leaveReturnsToJoin:true,rejoinWorks:true});
  await member.context().close();await admin.context().close();
 }
 assert.deepEqual(errors,[]);await writeFile(out+'/result.json',JSON.stringify({results,errors},null,2));console.log(JSON.stringify({results,errors}));
} finally {await browser.close();await server.close();}
