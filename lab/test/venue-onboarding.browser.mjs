import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {startVenueServer} from './venue-server.mjs';
import {chromium} from '../../tools/browser-runtime.mjs';
const local=await startVenueServer(),base=process.env.PWA_TEST_URL||local.base;
const out=resolve(process.env.PWA_TEST_OUTPUT||'.superpowers/sdd/2026-10-31-venue-demo/pwa-proof');await mkdir(out,{recursive:true});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const aContext=await browser.newContext({viewport:{width:400,height:860}}),bContext=await browser.newContext({viewport:{width:400,height:860}});
const a=await aContext.newPage(),b=await bContext.newPage(),errors=[],checks=[];
for(const p of [a,b]){p.setDefaultTimeout(Number(process.env.VENUE_TIMEOUT||30000));p.on('pageerror',e=>errors.push(e.message));}
const name='架空会場'+Date.now(),password='fictional-demo-password';
async function rows(page){return page.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');try {const group=await s.read('meta','group');return {group,people:await s.all(group),positions:await s.positions(group),venue:await s.read('meta','venue:'+group),answers:await s.incidents(group)};}finally{s.close();}});}
try {
 await a.goto(base);await a.locator('#cloud-group-name').fill(name);await a.locator('#cloud-admin-name').fill('主催者');await a.locator('#cloud-create button').click();
 await a.locator('#venue-admin-password').fill(password);await a.locator('#venue-start').click();
 await a.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);
 await a.locator('#safe').click();await a.locator('#status-update').click();await a.waitForFunction(()=>document.body.dataset.page==='buildings');checks.push('Administrator starts venue before attendance and answers without a home record');
 await b.goto(base);await b.locator('#venue-open').click();await b.locator('#venue-group-name').fill(name);await b.locator('#venue-password').fill('incorrect');await b.locator('#venue-check').click();await b.waitForFunction(()=>document.querySelector('#cloud-status').textContent.includes('パスワードを確認'));
 await b.locator('#venue-password').fill(password);await b.locator('#venue-check').click();await b.locator('#venue-name').fill('参加者ひとり');await b.screenshot({path:out+'/01-name.png'});await b.locator('#venue-join').click();
 await b.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);await b.screenshot({path:out+'/02-safety.png'});await b.reload();await b.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);
 await b.locator('#safe').click();await b.locator('#status-update').click();await b.waitForFunction(()=>document.body.dataset.page==='buildings');
 await b.waitForFunction(()=>{const bounds=document.querySelector('#tsunami-map').getBoundingClientRect();return [...document.querySelectorAll('.person-map-marker')].filter(el=>{const r=el.getBoundingClientRect();return r.left>=bounds.left&&r.right<=bounds.right&&r.top>=bounds.top&&r.bottom<=bounds.bottom;}).length>=2;});await b.waitForFunction(()=>[...document.querySelectorAll('.leaflet-tile')].length>0&&[...document.querySelectorAll('.leaflet-tile')].every(el=>el.complete&&el.naturalWidth>0),{},{timeout:20000}).catch(()=>{});await b.screenshot({path:out+'/03-map.png'});checks.push('Group/password → name → safety → map with earlier attendee markers');
 const before=await rows(b);assert.equal(before.people.length,0);assert.equal(before.positions.length,0);assert.equal(before.venue.placements.length,2);assert.ok(before.answers.some(r=>r.kind==='ANSWER'&&r.safety==='SAFE'));checks.push('Demo participants are separate from home and GPS storage');
 await bContext.setOffline(true);await b.reload();await b.waitForFunction(()=>document.querySelector('#header-group').textContent.includes('参加者ひとり'));const restored=await rows(b);assert.deepEqual(restored.venue,before.venue);assert.equal(restored.answers.filter(r=>r.kind==='ANSWER').length,before.answers.filter(r=>r.kind==='ANSWER').length);checks.push('Offline reopen retains membership, placement and answer');
 await b.locator('[data-tab="safety"]').click();await b.locator('#sos').click();await b.locator('#status-update').click();await b.waitForFunction(()=>document.body.dataset.page==='buildings');
 const offline=await rows(b);assert.ok(offline.answers.some(r=>r.kind==='ANSWER'&&r.safety==='SOS'));
 await bContext.setOffline(false);
 await a.waitForFunction(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');try {return (await s.incidents(await s.read('meta','group'))).some(r=>r.kind==='ANSWER'&&r.safety==='SOS');}finally{s.close();}},{},{timeout:45000});checks.push('Offline SOS is shared with earlier participant automatically after reconnect');
 assert.deepEqual(errors,[]);await writeFile(out+'/verification.json',JSON.stringify({passed:true,base,checks,errors},null,2));console.log(checks.join('\n'));
}catch(error){await b.screenshot({path:out+'/failure-b.png'}).catch(()=>{});await a.screenshot({path:out+'/failure-a.png'}).catch(()=>{});await writeFile(out+'/verification.json',JSON.stringify({passed:false,checks,errors,error:String(error)},null,2));throw error;}
finally{await browser.close();await local.close();}
