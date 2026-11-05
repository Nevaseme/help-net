import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {chromium} from 'file:///C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs';
import {startVenueServer} from './venue-server.mjs';

const out=process.env.PLACEMENT_OUTPUT||`${process.env.TEMP}/pland-neighborhood-placement`;
await mkdir(out,{recursive:true});
const server=await startVenueServer({cityFixture:true});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const context=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});
const page=await context.newPage(),errors=[],checks=[];
page.setDefaultTimeout(30000);
page.on('pageerror',e=>errors.push(e.message));
if(process.env.PLACEMENT_URL)await page.route('**/api/**',async route=>{const url=new URL(route.request().url());const res=await route.fetch({url:server.base+url.pathname+url.search,headers:{...route.request().headers(),origin:server.base,referer:server.base+'/'}});await route.fulfill({response:res});});
await context.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});localStorage.setItem('tasukeai-language','en');});
await page.route('**/tsunami-map.mjs',async route=>{const res=await route.fetch();await route.fulfill({response:res,body:(await res.text()).replace("L.control.zoom({position:'topright'}).addTo(map);","window.qaMap=map;L.control.zoom({position:'topright'}).addTo(map);").replace("vectorMap=layer.getMaplibreMap();","vectorMap=layer.getMaplibreMap();window.qaVector=vectorMap;")});});
const anchors=()=>page.evaluate(()=>Object.values(window.qaMap._layers).filter(l=>l.options?.icon?.options?.className==='community-map-marker').map(l=>{
 const rect=l.getElement().getBoundingClientRect(),host=window.qaMap.getContainer().getBoundingClientRect(),point=window.qaMap.latLngToContainerPoint(l.getLatLng());
 return {id:l._leaflet_id,lat:l.getLatLng().lat,lon:l.getLatLng().lng,dx:rect.left+rect.width/2-host.left-point.x,dy:rect.bottom-host.top-point.y,transition:getComputedStyle(l.getElement()).transitionDuration};
}));
const verify=(rows,initial)=>{assert.ok(rows.length>=2);for(const row of rows){assert.ok(Math.abs(row.dx)<=1&&Math.abs(row.dy)<=1,JSON.stringify(row));assert.equal(row.transition,'0s');if(initial){const before=initial.find(r=>r.id===row.id);assert.ok(before,'Existing marker retained');assert.equal(row.lat,before.lat);assert.equal(row.lon,before.lon);}}};
try{
 await page.goto(process.env.PLACEMENT_URL||server.base);assert.equal(await page.title(),'Help Net');
 await page.evaluate(async()=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity()),g=await c.register('create',{groupName:'Placement check',activityName:'Placement check'});await c.startVenue(g.groupId,'PlacementCheck123',{positionMode:'KYOTO'});await c.sync(g.groupId);s.close();});
 await page.reload();await page.waitForFunction(()=>!!document.querySelector('#incident-select')?.value);
 await page.locator('#safe').click();await page.locator('#status-update').click();
 await page.waitForFunction(()=>window.qaMap&&document.querySelectorAll('.community-map-marker').length===2);
 await page.evaluate(()=>window.qaMap.setView([35.0145,135.7622],15.5,{animate:false}));await page.waitForTimeout(800);
 await page.waitForFunction(()=>window.qaVector?.getLayer('kyoto-risk-buildings')&&window.qaVector.queryRenderedFeatures({layers:['kyoto-risk-buildings']}).length>0,null,{timeout:60000});
 const initial=await anchors();verify(initial);
 for(const row of initial){assert.equal(row.lat,35.0145);assert.ok([135.759,135.7622,135.7657].includes(row.lon),'Marker must use the actual neighborhood coordinate');}
 await page.screenshot({path:out+'/pwa-map.png'});
 for(let i=0;i<12;i++){await page.evaluate(i=>window.qaMap.panBy([i%2?65:-65,i%3?25:-50],{animate:false}),i);verify(await anchors(),initial);}
 for(const zoom of [14,17,15,16]){await page.evaluate(z=>window.qaMap.setZoom(z,{animate:false}),zoom);verify(await anchors(),initial);}
 // Include a frame during an actual held pointer gesture, not just its settled endpoint.
 const box=await page.locator('#tsunami-map').boundingBox();await page.mouse.move(box.x+box.width*.5,box.y+box.height*.7);await page.mouse.down();await page.mouse.move(box.x+box.width*.5+70,box.y+box.height*.7-50,{steps:8});verify(await anchors(),initial);await page.mouse.up();
 checks.push('Fixed coordinates and bottom anchor after 12 pans, 4 zooms and held drag; same marker objects; no delayed CSS motion');
 for(const width of [320,390,1000]){await page.setViewportSize({width,height:844});await page.waitForTimeout(250);verify(await anchors(),initial);}
 await page.setViewportSize({width:390,height:844});await page.evaluate(()=>window.qaMap.setView([35.0145,135.7622],15.5,{animate:false}));await page.waitForTimeout(500);
 await page.locator('.community-map-marker').first().click();await page.getByText('Zoom to this neighborhood',{exact:true}).waitFor();await page.screenshot({path:out+'/pwa-details.png'});
 checks.push('320, 390 and 1000px widths; neighborhood tap opens its summary');
 assert.deepEqual(errors,[]);await writeFile(out+'/result.json',JSON.stringify({passed:true,checks,errors},null,2));console.log(JSON.stringify({passed:true,out,checks}));
}catch(error){await page.screenshot({path:out+'/failure.png'});console.error(await page.locator('body').innerText());throw error;}
finally{await browser.close();await server.close();}
