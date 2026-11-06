import assert from 'node:assert/strict';
import {chromium} from '../../tools/browser-runtime.mjs';
import {startVenueServer} from './venue-server.mjs';
const server=await startVenueServer({publicAssets:process.env.VENUE_PUBLIC_ASSETS||null}),browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try{
 for(const active of [false,true]){
  const admin=await browser.newContext({serviceWorkers:'block'}),a=await admin.newPage();await a.goto(server.base);await a.locator('#group-access').waitFor();
  const groupName='Join GPS '+active;
  await a.evaluate(async({active,groupName})=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity()),g=await c.register('create',{groupName,password:'GpsJoin123',activityName:'Host'});if(active)await c.startVenue(g.groupId,'',{positionMode:'KYOTO',presentationMode:'EARTHQUAKE'});s.close();},{active,groupName});
  const context=await browser.newContext({serviceWorkers:'block'}),p=await context.newPage(),errors=[];p.on('pageerror',e=>errors.push(e.message));
  await context.addInitScript(()=>{window.gpsCalls={one:0,watch:0};Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition(ok,fail){window.gpsCalls.one++;setTimeout(()=>fail({code:1}),0);},watchPosition(ok,fail){window.gpsCalls.watch++;setTimeout(()=>fail({code:1}),0);return 1;},clearWatch(){}}});});
  await p.goto(server.base);await p.locator('#group-join-identity').fill(groupName);await p.locator('#group-join-password').fill('GpsJoin123');await p.locator('#group-next').click();await p.locator('#group-join-name').fill('Participant');await p.locator('#group-join').click();
  await p.waitForFunction(()=>window.gpsCalls.one+window.gpsCalls.watch>0);
  if(active){await p.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);await p.locator('#safe').click();await p.locator('#status-update').click();await p.locator('#venue-location-notice').waitFor();await p.locator('#venue-location-notice button').click();await p.waitForFunction(()=>document.body.dataset.page==='buildings');}
  else{assert.equal((await p.evaluate(()=>window.gpsCalls)).one,1);await p.waitForFunction(()=>document.body.dataset.settingsView==='profile');}
  assert.deepEqual(errors,[]);console.log(JSON.stringify({active,permissionAttempt:await p.evaluate(()=>window.gpsCalls),denialDoesNotBlock:true,errors}));await context.close();await admin.close();
 }
}finally{await browser.close();await server.close();}
