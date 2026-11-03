import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {chromium} from 'file:///C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {startVenueServer} from './venue-server.mjs';
const server=await startVenueServer(),base=process.env.COMMUNITY_TEST_URL||server.base;
const out=process.env.COMMUNITY_TEST_OUTPUT;assert.ok(out,'Set screenshot output outside repository');await mkdir(out,{recursive:true});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const a=await browser.newPage(),ctx=await browser.newContext({viewport:{width:390,height:844}}),p=await ctx.newPage();
const errors=[];p.on('pageerror',e=>errors.push(e.message));const name='Map QA '+Date.now(),password='12345678';
try{
 await a.goto(base);await a.evaluate(async({name,password})=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity()),g=await c.register('create',{groupName:name,activityName:'Map organizer'});await c.startVenue(g.groupId,password,{positionMode:'KYOTO'});await c.sync(g.groupId);s.close();},{name,password});
 await p.goto(base);await p.locator('#group-join-identity').fill(name);await p.locator('#group-join-password').fill(password);await p.locator('#group-next').click();await p.locator('#group-join-name').fill('Map audience');await p.locator('#group-join').click();
 await p.locator('#safe').click();await p.locator('#status-update').click();await p.waitForFunction(()=>document.body.dataset.page==='buildings');
 await p.waitForFunction(()=>document.querySelectorAll('.community-map-marker').length===2);
 await p.screenshot({path:out+'/map-ja.png'});
 const label=await p.locator('.community-map-marker').first().getAttribute('title');await p.locator('.community-map-marker').first().click();
 await p.locator('#town-summary-sheet').waitFor({state:'visible'});assert.equal(await p.locator('#town-summary-sheet .town-counts > div').count(),4);assert.ok((await p.locator('#town-summary-sheet').innerText()).includes(label));
 assert.equal(await p.locator('#town-summary-sheet [data-injury]').count(),0);assert.equal(await p.locator('#town-summary-sheet [data-person]').count(),0);await p.screenshot({path:out+'/other-neighborhood.png'});
 await p.locator('#town-summary-sheet .sheet-close').click();
 assert.ok(await p.locator('.person-impact').count()>0);assert.ok(await p.locator('.person-enclosed').count()>0);
 const observed=p.locator('.person-enclosed').first();await observed.click();
 await p.locator('#person-sheet').waitFor({state:'visible'});assert.match(await p.locator('#detail-info').innerText(),/閉所を検知/);assert.match(await p.locator('#detail-info').innerText(),/デモの模擬検知/);await p.screenshot({path:out+'/observations-ja.png'});
 await p.locator('#person-sheet .sheet-close').click();
 await p.locator('[data-tab="response"]').click();await p.locator('#area-summary').click();assert.equal(await p.locator('.community-card').count(),3);await p.locator('#area-sheet .sheet-close').click();
 const moved=await p.evaluate(async()=>{
  const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs'),{appendScopedIncident}=await import('/incidents.mjs');
  const s=await Store.open('tasukeai-cloud'),g=await s.read('meta','group'),id=await s.identity(),v=await s.read('meta','venue:'+g),before=v.placements.find(p=>p.deviceId===id);
  const home={...before.home,buildingId:crypto.randomUUID(),latitude:before.home.latitude+.001,address:'Changed demo home',plateauId:null,floor:'',room:''};
  await appendScopedIncident(s,{groupId:g,incidentId:v.incidentId,authorId:id},'ANSWER',{safety:'SOS'});
  await s.appendDeviceRecord({groupId:g,authorId:id,kind:'DEMO_HOME',sessionId:v.sessionId,home});const client=new CloudClient(s,id);const sent=await client.sync(g);if(sent.error)throw Error(sent.error);await client.sync(g);
  const after=(await s.read('meta','venue:'+g)).placements.find(p=>p.deviceId===id);s.close();return {before,after,home};
 });
 assert.equal(moved.after.latitude,moved.before.latitude);assert.equal(moved.after.longitude,moved.before.longitude);assert.equal(moved.after.home.latitude,moved.home.latitude);
 await p.reload();await p.waitForFunction(()=>!!document.querySelector('#incident-select').value&&!!document.querySelector('#list [data-person]'));
 await p.waitForFunction(value=>document.querySelector('#lat').value===String(value),moved.home.latitude);assert.equal(await p.locator('#home-address').inputValue(),'Changed demo home');
 await p.evaluate(()=>localStorage.setItem('tasukeai-language','en'));await p.reload();await p.waitForFunction(()=>!!document.querySelector('#incident-select').value&&!!document.querySelector('#list [data-person]'));await p.locator('[data-tab="buildings"]').click();await p.waitForFunction(()=>document.querySelectorAll('.community-map-marker').length===2);assert.match(await p.locator('.community-map-marker').first().innerText(),/Gosho South/);
 await p.locator('.person-enclosed').first().click();assert.match(await p.locator('#detail-info').innerText(),/Enclosed space detected/);assert.match(await p.locator('#detail-info').innerText(),/Simulated demo observations/);await p.screenshot({path:out+'/observations-en.png'});
 await p.waitForFunction(()=>!!navigator.serviceWorker.controller);await ctx.setOffline(true);await p.reload();await p.waitForFunction(()=>!!document.querySelector('#incident-select').value&&!!document.querySelector('#list [data-person]'));await p.locator('[data-tab="buildings"]').click();await p.waitForFunction(()=>document.querySelectorAll('.community-map-marker').length===2);assert.ok(await p.locator('.person-enclosed').count()>0);await ctx.setOffline(false);
 await p.setViewportSize({width:320,height:800});assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);
 console.log('PASS: member map → other-neighborhood counts only; all 3 summaries; impact/enclosure pins and detail; JA/EN; offline restart; 320 px; no page errors');
}finally{
 try{await a.evaluate(async()=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),g=await s.read('meta','group');if(g)await new CloudClient(s,await s.identity()).endVenue(g);s.close();});}catch{}
 await browser.close();await server.close();
}
