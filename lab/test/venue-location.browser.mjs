import assert from 'node:assert/strict';
import {chromium} from 'file:///C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {startVenueServer} from './venue-server.mjs';

const server=await startVenueServer(),browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const context=await browser.newContext({serviceWorkers:'block'}),page=await context.newPage();
await context.addInitScript(()=>{
 const watchers=new Map();let sequence=0;
 window.gpsTest={watchers,fire:position=>{for(const w of watchers.values())w.success(position);},fail:()=>{for(const w of watchers.values())w.failure({code:1});}};
 Object.defineProperty(navigator,'geolocation',{value:{watchPosition(success,failure){const id=++sequence;watchers.set(id,{success,failure});return id;},clearWatch(id){watchers.delete(id);}}});
});
async function own(){return page.evaluate(async()=>{const {Store}=await import('/sync.mjs'),s=await Store.open('tasukeai-cloud'),id=await s.identity(),group=await s.read('meta','group'),venue=await s.read('meta','venue:'+group),position=(await s.positions(group)).find(p=>p.originId===id);s.close();return {id,group,venue,position};});}
try{
 await page.goto(server.base);
 await page.evaluate(async()=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs'),s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity()),g=await c.register('create',{groupName:'GPS acquisition browser test',activityName:'Participant'});await c.startVenue(g.groupId,'LocalTest123',{positionMode:'KYOTO'});await c.sync(g.groupId);s.close();});
 await page.reload();await page.waitForFunction(()=>window.gpsTest.watchers.size>0);
 const initial=await own();assert.equal(initial.position,undefined);
 const timestamp=Date.now();await page.evaluate(timestamp=>window.gpsTest.fire({timestamp,coords:{latitude:35.6665,longitude:139.7495,accuracy:12}}),timestamp);
 await page.waitForFunction(async expected=>{const {Store}=await import('/sync.mjs'),s=await Store.open('tasukeai-cloud'),g=await s.read('meta','group'),id=await s.identity(),p=(await s.positions(g)).find(p=>p.originId===id);s.close();return p?.acquiredAt===expected;},timestamp);
 const received=await own(),assigned=received.venue.placements.find(p=>p.deviceId===received.id);assert.equal(received.position.latitude,assigned.latitude);assert.equal(received.position.longitude,assigned.longitude);assert.equal(received.position.acquiredAt,timestamp);
 await page.evaluate(()=>window.gpsTest.fail());assert.equal((await own()).position.acquiredAt,timestamp);
 await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});document.dispatchEvent(new Event('visibilitychange'));});
 assert.equal(await page.evaluate(()=>window.gpsTest.watchers.size),0);
 await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>false});document.dispatchEvent(new Event('visibilitychange'));});
 await page.waitForFunction(()=>window.gpsTest.watchers.size>0);
 const resumed=timestamp+1000;await page.evaluate(timestamp=>window.gpsTest.fire({timestamp,coords:{latitude:35.67,longitude:139.75,accuracy:7}}),resumed);
 await page.waitForFunction(async expected=>{const {Store}=await import('/sync.mjs'),s=await Store.open('tasukeai-cloud'),g=await s.read('meta','group'),id=await s.identity(),p=(await s.positions(g)).find(p=>p.originId===id);s.close();return p?.acquiredAt===expected;},resumed);
 assert.equal((await own()).position.longitude,assigned.longitude);
 console.log('PASS Kyoto browser GPS success uses real acquisition time at assigned coordinates; failure leaves time unchanged; visibility recovery restarts watch and saves next fix. GPS and lifecycle are simulated.');
}finally{await browser.close();await server.close();}
