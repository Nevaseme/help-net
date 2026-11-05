import {createServer} from 'node:http';
import {readFile,mkdir} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE||'C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const root=resolve('lab/web'),out=resolve('output/playwright/person-status');await mkdir(out,{recursive:true});
const mime={'.html':'text/html','.mjs':'text/javascript','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
const server=createServer(async(req,res)=>{try{const name=new URL(req.url,'http://local').pathname;if(name.startsWith('/api/'))throw Error();const path=resolve(root,'.'+(name==='/'?'/index.html':name));if(!path.startsWith(root))throw Error();let data=await readFile(path);if(name==='/tsunami-map.mjs')data=Buffer.from(data.toString().replace("selectBuilding=callback;","selectBuilding=callback;window.testBuildingSelect=callback;").replace("L.control.zoom({position:'topright'}).addTo(map);","window.testLeafletMap=map;L.control.zoom({position:'topright'}).addTo(map);"));res.writeHead(200,{'content-type':mime[extname(path)]||'application/octet-stream'});res.end(data);}catch{res.writeHead(404);res.end('not found');}});
await new Promise(r=>server.listen(4208,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const context=await browser.newContext({viewport:{width:400,height:860},serviceWorkers:'block'}),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
try{
 // Keep this fixture isolated from offline caches while supplying the registration shape expected by the app.
 await context.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});});
 await page.goto('http://127.0.0.1:4208/');await page.waitForFunction(()=>typeof document.querySelector('#menu-open')?.onclick==='function');
 const fixture=await page.evaluate(async()=>{
  const {Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B'),id=await s.identity(),safe=crypto.randomUUID(),sos=crypto.randomUUID(),rescued=crypto.randomUUID(),waiting=crypto.randomUUID(),building=crypto.randomUUID();
  const people=[[id,'Admin'],[safe,'Private safe name'],[sos,'Help requested'],[rescued,'Private rescued name'],[waiting,'Waiting resident']];
  await s.importRoster({version:1,groupId:'demo',members:people.map(([deviceId,name])=>({memberId:deviceId,deviceIds:[deviceId],role:deviceId===id?'ADMIN':'MEMBER',activityName:name}))},id);
  for(const [originId,name]of people)await s.saveProfile({groupId:'demo',originId,revision:1,updatedAt:0,name,latitude:35.014,longitude:135.759,floor:'2',room:'Private bedroom',safety:'UNREPORTED'});
  const eventId=crypto.randomUUID(),startedAt=Date.now()-240000;
  await s.commandQuake({groupId:'demo',eventId,actorDeviceId:id,operatedAt:startedAt,action:'TRAINING_START',training:true,title:'Anonymous status check',area:'Fixture area'});await s.update('meta','incident:demo',()=>eventId);
  for(const [authorId,safety]of [[id,'SAFE'],[safe,'SAFE'],[sos,'SOS'],[rescued,'SOS']])await s.appendIncident({groupId:'demo',eventId:crypto.randomUUID(),incidentId:eventId,authorId,authorName:people.find(p=>p[0]===authorId)[1],createdAt:startedAt+1,kind:'ANSWER',safety});
  const rescueAt=startedAt+60000;await s.appendIncident({groupId:'demo',eventId:crypto.randomUUID(),incidentId:eventId,authorId:sos,authorName:'Private helper name',createdAt:rescueAt,kind:'CARE',subjectId:rescued,action:'RESCUED',note:'Private rescue note'});
  await s.appendPlace({groupId:'demo',eventId:building,buildingId:building,authorId:id,authorName:'Admin',createdAt:startedAt,kind:'BUILDING',label:'京都府京都市中京区少将井御旅町付近',latitude:35.014,longitude:135.759});
  for(const [authorId,name]of people.slice(1))await s.appendPlace({groupId:'demo',eventId:crypto.randomUUID(),buildingId:building,authorId,authorName:name,createdAt:startedAt,kind:'HOME'});
  const phone={latitude:35.019,longitude:135.756};for(const [originId]of people)await s.savePosition({groupId:'demo',originId,revision:1,acquiredAt:startedAt+1,...phone,accuracyMeters:8});
  s.close();return {id,safe,sos,rescued,waiting,building,phone,rescueAt};
 });
 await page.reload();await page.waitForFunction(()=>!!document.querySelector('#incident-select')?.value);await page.locator('[data-tab="buildings"]').click();await page.locator('.leaflet-container').waitFor();
 await page.evaluate(b=>window.testBuildingSelect([{id:b,registeredId:b,attributes:{year:2000,floors:2,structureCode:'601'}}]),fixture.building);
 for(const [key,value]of [['total',4],['SAFE',1],['SOS',1],['UNREPORTED',1],['RESCUED',1]])assert.equal((await page.locator(`[data-building-count="${key}"] dd`).textContent()).trim(),value+'人');
 assert.doesNotMatch(await page.locator('#map-building-sheet').textContent(),/Private safe name|Private rescued name/);
 await page.locator(`[data-building-person="${fixture.waiting}"]`).click();assert.equal(await page.locator('#person-sheet').isVisible(),true);
 const center=await page.evaluate(()=>({latitude:window.testLeafletMap.getCenter().lat,longitude:window.testLeafletMap.getCenter().lng}));assert.ok(Math.abs(center.latitude-fixture.phone.latitude)<1e-5,JSON.stringify({center,phone:fixture.phone}));assert.ok(Math.abs(center.longitude-fixture.phone.longitude)<1e-5,JSON.stringify({center,phone:fixture.phone}));
 await page.locator('[data-tab="buildings"]').click();await page.locator('.person-map-marker[title="無事の人"]').first().click({force:true});await page.locator('.map-location-list button').filter({hasText:'無事の人'}).first().click();assert.match(await page.locator('#person-sheet').textContent(),/無事/);assert.doesNotMatch(await page.locator('#person-sheet').textContent(),/Private/);
 for(const [id,status]of [[fixture.safe,'無事'],[fixture.rescued,'救助済み']]){
  await page.locator('[data-tab="residents"]').click();await page.locator(`[data-person="${id}"]`).click();assert.equal(await page.locator('#person-sheet').isVisible(),true);
  const text=await page.locator('#person-sheet').textContent();assert.match(text,new RegExp(status));assert.doesNotMatch(text,/Private|寝室|スマホ|精度|衝撃|閉所/);
  assert.equal(await page.locator('#person-revoke-rescue').count(),id===fixture.rescued?1:0);
 }
 assert.match(await page.locator('.person-rescued-at').textContent(),/救助日時/);await page.screenshot({path:out+'/rescued-admin.png'});
 await page.locator('#person-revoke-rescue').click();await page.waitForFunction(()=>!document.querySelector('#person-revoke-rescue'));assert.match(await page.locator('#person-sheet').textContent(),/SOS/);
 await page.locator('[data-tab="buildings"]').click();await page.evaluate(b=>window.testBuildingSelect([{id:b,registeredId:b,attributes:{}}]),fixture.building);assert.equal((await page.locator('[data-building-count="RESCUED"] dd').textContent()).trim(),'0人');assert.equal((await page.locator('[data-building-count="SOS"] dd').textContent()).trim(),'2人');
 await page.locator('[data-tab="residents"]').click();await page.locator('#menu-open').click();await page.locator('[data-menu="settings"]').click();await page.locator('[name="ui-language"][value="en"]').check();await page.locator('[data-tab="residents"]').click();await page.locator(`[data-person="${fixture.safe}"]`).click();assert.match(await page.locator('#person-sheet').textContent(),/Safe person/);await page.setViewportSize({width:320,height:700});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.locator('[data-tab="buildings"]').click();await page.evaluate(b=>window.testBuildingSelect([{id:b,registeredId:b,attributes:{floors:2}}]),fixture.building);assert.match(await page.locator('#map-building-sheet .building-attributes').innerText(),/2 floors/);await page.evaluate(b=>window.testBuildingSelect([{id:b,registeredId:b,attributes:{floors:1}}]),fixture.building);assert.match(await page.locator('#map-building-sheet .building-attributes').innerText(),/1 floor/);
 await page.evaluate(async({id,rescued,sos})=>{const {Store}=await import('/sync.mjs');const s=await Store.open('pland-lab-B'),incidentId=await s.read('meta','incident:demo');await s.appendIncident({groupId:'demo',eventId:crypto.randomUUID(),incidentId,authorId:sos,authorName:'Private helper name',createdAt:Date.now(),kind:'CARE',subjectId:rescued,action:'RESCUED',note:'Private rescue note'});await s.update('meta','identity',()=>sos);s.close();},fixture);
 await page.reload();await page.waitForFunction(()=>!!document.querySelector('#incident-select')?.value);await page.locator('[data-tab="residents"]').click();await page.locator(`[data-person="${fixture.rescued}"]`).click();assert.match(await page.locator('#person-sheet').textContent(),/Rescued at/);assert.equal(await page.locator('#person-revoke-rescue').count(),0);assert.doesNotMatch(await page.locator('#person-sheet').textContent(),/Private|Sleeping|Impact|Enclosed/);
 assert.deepEqual(errors,[]);console.log('Person status/building browser checks passed: counts, phone focus, anonymous status, admin revoke, member restrictions, English, 320px.');
}finally{await browser.close();await new Promise(r=>server.close(r));}
