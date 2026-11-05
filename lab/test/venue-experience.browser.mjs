import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {chromium} from 'file:///C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {startVenueServer} from './venue-server.mjs';
const participants=Number(process.env.VENUE_PARTICIPANTS||1);
const out=process.env.VENUE_OUTPUT||process.env.TEMP+'/pland-venue-20261106';await mkdir(out,{recursive:true});
const server=await startVenueServer({cityFixture:true,publicAssets:process.env.VENUE_PUBLIC_ASSETS||null}),browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const base=process.env.VENUE_TEST_BASE||server.base;
const errors=[],japanese=[];
async function routeLocalApi(context){if(base!==server.base)await context.route('**/api/**',async route=>{const request=route.request(),response=await fetch(server.base+new URL(request.url()).pathname+new URL(request.url()).search,{method:request.method(),headers:{...request.headers(),origin:server.base},...(request.method()==='POST'?{body:request.postDataBuffer()}:{})});await route.fulfill({status:response.status,headers:Object.fromEntries(response.headers),body:Buffer.from(await response.arrayBuffer())});});}
try{for(const mode of ['DRILL','EARTHQUAKE']){
 const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'}),page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(20000);
 await routeLocalApi(context);await context.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});});
 await page.route('**/tsunami-map.mjs',async route=>{const r=await route.fetch();await route.fulfill({response:r,body:(await r.text()).replace("L.control.zoom({position:'topright'}).addTo(map);","window.qaMap=map;L.control.zoom({position:'topright'}).addTo(map);").replace('vectorMap=layer.getMaplibreMap();','vectorMap=layer.getMaplibreMap();window.qaVector=vectorMap;')});});
 const audit=async name=>{const rows=await page.evaluate(()=>{const found=[];const re=/[\u3040-\u30ff\u3400-\u9fff]/;for(const e of document.querySelectorAll('body *')){if(e.closest('script,style,[hidden]')||!e.checkVisibility({checkOpacity:true,checkVisibilityCSS:true}))continue;if(e.matches('input:not([type=password]):not([type=hidden]),textarea')&&re.test(e.value))found.push({tag:e.tagName,id:e.id,attribute:'value',text:e.value});for(const a of ['aria-label','title','placeholder']){const v=e.getAttribute(a);if(v&&re.test(v))found.push({tag:e.tagName,id:e.id,attribute:a,text:v});}for(const n of e.childNodes)if(n.nodeType===Node.TEXT_NODE&&re.test(n.textContent))found.push({tag:e.tagName,id:e.id,text:n.textContent.trim()});}return found;});japanese.push(...rows.map(x=>({mode,screen:name,...x})));};
 await page.goto(base);const fixture=await page.evaluate(async ({mode,participants})=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity()),g=await c.register('create',{groupName:'Venue check '+mode,activityName:'Organizer'});await c.startVenue(g.groupId,'VenueCheck123',{presentationMode:mode});
 for(let i=1;i<participants;i++){
  const j=await c.request('/venue/join',{body:{groupName:'Venue check '+mode,password:'VenueCheck123',activityName:'Participant '+(i+1),deviceId:crypto.randomUUID(),registrationToken:crypto.randomUUID().replaceAll('-','')+crypto.randomUUID().replaceAll('-','')}});
  await c.request('/groups/'+g.groupId+'/records',{session:j,body:{records:[{collection:'incidents',record:{groupId:g.groupId,eventId:crypto.randomUUID(),incidentId:j.venue.incidentId,authorId:j.deviceId,authorName:'Participant '+(i+1),sequence:1,createdAt:Date.now(),kind:'ANSWER',safety:'SAFE'}}]}});
 }
 await c.sync(g.groupId);const envelope=await c.request('/groups/'+g.groupId+'/roster',{session:g}),records=[];let cursor=0;
 while(true){const page=await c.request('/groups/'+g.groupId+'/records?after='+cursor+'&limit=100',{session:g});records.push(...page.records);cursor=page.nextCursor;if(!page.hasMore)break;}
 s.close();return {envelope,records};},{mode,participants});
 assert.equal(fixture.envelope.venue.placements.length,participants);
 if(participants===32&&mode==='DRILL'&&process.env.VENUE_WRITE_FIXTURE==='1')await writeFile('android/app/src/androidTest/assets/venue-experience-fixture.json',JSON.stringify(fixture,null,2));
 await page.reload();await page.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);assert.equal(await page.locator('html').getAttribute('lang'),'en');
 for(const name of ['buildings','residents','response'])assert.equal(await page.locator(`[data-tab="${name}"]`).isDisabled(),true);assert.equal(await page.locator('#menu-open').isDisabled(),true);
 assert.equal(await page.locator('header h1').innerText(),mode==='DRILL'?'Drill':'Earthquake');await audit('safety');await page.screenshot({path:out+'/'+mode+'-safety.png'});
 await page.locator('#safe').click();await page.locator('#status-update').click();const notice=page.locator('#venue-location-notice');await notice.waitFor({state:'visible'});assert.match(await notice.innerText(),/This is a demo\. Your location is set to Kyoto to make the experience easier to follow\./);await page.screenshot({path:out+'/'+mode+'-notice.png'});await notice.getByRole('button',{name:'Continue',exact:true}).click();
 await page.waitForFunction(()=>window.qaMap&&Object.values(window.qaMap._layers).some(l=>l.options?.className==='my-location-dot'));await page.waitForFunction(()=>window.qaVector?.isStyleLoaded(),null,{timeout:60000});await page.waitForTimeout(1200);await audit('map');await page.screenshot({path:out+'/'+mode+'-map.png'});
 const full=await page.locator('.person-map-marker').count();await page.locator('#map-show-safe').uncheck();await page.waitForTimeout(500);const focused=await page.locator('.person-map-marker').count();assert.ok(focused<full,`${full} > ${focused}`);console.log(JSON.stringify({mode,full,focused}));await page.screenshot({path:out+'/'+mode+'-hide-safe.png'});await page.locator('#map-show-safe').check();
 assert.equal(await page.evaluate(()=>window.qaMap.getPane('my-location').style.zIndex),'650');
 assert.equal(await page.evaluate(()=>window.qaMap.getPane('my-location').style.pointerEvents),'none');
 assert.equal(await page.evaluate(()=>window.qaMap.getMaxZoom()),20);assert.equal(await page.evaluate(()=>window.qaMap.options.bounceAtZoomLimits),false);
 if(mode==='DRILL'){
  const people=await page.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud'),v=await s.read('meta','venue:'+await s.read('meta','group'));s.close();return v.scenario.people;});
  for(const region of ['demo-central','demo-west']){
   const person=people.find(p=>p.communityId===region&&p.safety==='UNREPORTED'&&!p.positionUnknown);
   await page.evaluate(home=>window.qaMap.setView([home.latitude,home.longitude],18,{animate:false}),person.home);await page.waitForTimeout(1200);
   await page.evaluate(home=>window.qaMap.fire('click',{latlng:{lat:home.latitude,lng:home.longitude}}),person.home);
   const sheet=page.locator('#map-building-sheet');await sheet.waitFor();assert.match(await sheet.innerText(),/Residents with no response/);
   if(region==='demo-central')await sheet.getByRole('button').filter({hasText:person.name}).waitFor();else{assert.match(await sheet.innerText(),/You cannot view individual details because you do not belong to this neighborhood\./);assert.equal(await sheet.locator('[data-building-person]').count(),0);}
   await audit(region+'-building');await page.screenshot({path:out+'/'+mode+'-'+region+'-building.png'});await sheet.locator('.sheet-close').click();
  }
  await page.evaluate(()=>window.qaMap.setView([35.01395,135.76395],19,{animate:false}));await page.waitForTimeout(800);await page.screenshot({path:out+'/'+mode+'-compact-map.png'});
  await page.evaluate(()=>window.qaMap.setView([35.01395,135.76395],17.75,{animate:false}));await page.waitForTimeout(900);await page.screenshot({path:out+'/'+mode+'-neighborhoods.png'});
  await page.evaluate(()=>window.qaMap.setView([35.01395,135.76395],19,{animate:false}));await page.waitForTimeout(700);
  await page.locator('#map-show-safe').uncheck();await page.waitForTimeout(250);await page.screenshot({path:out+'/'+mode+'-compact-focused.png'});await page.locator('#map-show-safe').check();
 }
 await page.locator('#map-report-add').click();assert.equal(await page.locator('#map-pick-confirm').isDisabled(),true);
 await page.evaluate(()=>window.qaMap.fire('click',{latlng:{lat:35.01455,lng:135.7641}}));
 await page.locator('.report-preview-marker').waitFor();assert.equal(await page.locator('#map-pick-confirm').isEnabled(),true);
 await page.evaluate(()=>window.qaMap.fire('click',{latlng:{lat:35.0146,lng:135.7642}}));assert.equal(await page.locator('.report-preview-marker').count(),1);
 await page.screenshot({path:out+'/'+mode+'-pin-preview.png'});
 await page.locator('#map-pick-confirm').click();await page.locator('#response-edit-form').waitFor();
 assert.equal(await page.locator('#response-edit-form [name="latitude"]').inputValue(),'35.0146');assert.equal(await page.locator('#response-edit-form [name="regionIds"]').count(),0);
 await page.locator('#response-edit-form [name="description"]').fill('Retained report draft');await page.locator('#report-pick-location').click();
 await page.getByRole('button',{name:'Cancel',exact:true}).click();await page.locator('#response-edit-form').waitFor();assert.equal(await page.locator('#response-edit-form [name="description"]').inputValue(),'Retained report draft');
 await audit('report-preview');await page.screenshot({path:out+'/'+mode+'-report-preview.png'});
 await page.locator('#response-edit-form').evaluate(form=>form.closest('dialog').close());
 await page.locator('#map-unknown').click();await page.waitForTimeout(250);assert.match(await page.locator('#list').innerText(),/Last updated:/);await audit('unknown');
 for(const name of ['residents','response','safety']){await page.locator(`[data-tab="${name}"]`).click();await page.waitForTimeout(350);await audit(name);}
 await page.locator('#safe').click();await page.locator('#status-update').click();await page.waitForFunction(()=>document.querySelector('#own-status-feedback').textContent==='Saved');await audit('updated-safe');
 assert.equal(await page.evaluate(()=>document.body.dataset.page),'safety');assert.equal(await page.locator('#venue-location-notice').isVisible(),false);
 await page.locator('#sos').click();await page.locator('#own-memo').fill('Updated safety status');await page.locator('#status-update').click();await page.waitForFunction(()=>document.querySelector('#own-status-feedback').textContent==='Saved');await audit('updated-sos');
 assert.equal(await page.evaluate(()=>document.body.dataset.page),'safety');assert.equal(await page.locator('#venue-location-notice').isVisible(),false);
 await page.locator('#menu-open').click();assert.equal(await page.evaluate(()=>document.activeElement.id),'menu-title');await audit('menu');
 for(const name of ['profile','group','incidents','settings']){if(!await page.locator('#app-menu').isVisible())await page.locator('#menu-open').click();await page.locator(`[data-menu="${name}"]`).click();await page.waitForTimeout(500);await audit(name);await page.screenshot({path:out+'/'+mode+'-'+name+'.png'});if(name==='settings')await page.locator('#settings-sheet .sheet-close').click().catch(()=>{});}
 await context.close();
}
 const jaContext=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'}),ja=await jaContext.newPage();
 await routeLocalApi(jaContext);await ja.goto(base);await ja.evaluate(async()=>{localStorage.setItem('tasukeai-language','ja');const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity()),g=await c.register('create',{groupName:'Japanese notice check',activityName:'Organizer'});await c.startVenue(g.groupId,'LocalJapaneseCheck',{presentationMode:'EARTHQUAKE'});await c.sync(g.groupId);s.close();});
 await ja.reload();await ja.locator('#safe').click();await ja.locator('#status-update').click();await ja.locator('#venue-location-notice').waitFor({state:'visible'});assert.equal(await ja.locator('html').getAttribute('lang'),'ja');assert.match(await ja.locator('#venue-location-notice').innerText(),/これはデモです。わかりやすく体験していただくため、現在地を京都に設定しています。/);await ja.screenshot({path:out+'/JA-notice.png'});await ja.getByRole('button',{name:'続ける',exact:true}).click();await jaContext.close();
 await writeFile(out+'/audit.json',JSON.stringify({errors,japanese},null,2));assert.deepEqual(errors,[]);assert.deepEqual(japanese,[]);console.log(JSON.stringify({out,japanese}));}catch(error){console.error(JSON.stringify({errors,japanese}));throw error;}finally{await browser.close();await server.close();}
