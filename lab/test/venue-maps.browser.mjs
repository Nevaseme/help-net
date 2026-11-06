import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {chromium} from '../../tools/browser-runtime.mjs';
import {startVenueServer} from './venue-server.mjs';
const server=await startVenueServer({cityFixture:true}),base=process.env.VENUE_MAP_URL||server.base;
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const context=await browser.newContext({viewport:{width:390,height:844}}),page=await context.newPage();
const out='.implementation/venue-maps-browser';await mkdir(out,{recursive:true});
const requests=[];page.on('request',r=>requests.push(r.url()));
try{
 await page.goto(base);await page.evaluate(()=>navigator.serviceWorker.ready);await page.reload();
 await page.evaluate(async()=>{
  const {attachCityOffline}=await import('/city-offline.mjs');
  window.testVenue={positionMode:'KYOTO',closed:false};window.testOrdinary={code:'22205',name:'熱海市'};
  const host=document.createElement('div');host.id='venue-map-test';document.body.append(host);
  window.mapTest=attachCityOffline({host,getGroup:()=> 'map-test',store:{read:async(_,key)=>key.startsWith('venue:')?window.testVenue:window.testOrdinary}});
 });
 await page.waitForFunction(()=>document.querySelector('#venue-map-test [data-city-state]').textContent.startsWith('保存中'),null,{timeout:60000});
 assert.ok(requests.some(u=>u.includes('/26100/')));
 // Switch while Kyoto is downloading: incomplete Kyoto cache must not become "ready".
 await page.evaluate(()=>{window.testVenue={positionMode:'ACTUAL',closed:false};window.dispatchEvent(new Event('venue-map-change'));});
 await page.waitForFunction(()=>/港区全域 · 保存済み/.test(document.querySelector('#venue-map-test [data-city-state]').textContent),null,{timeout:240000});
 const saved=await page.evaluate(async()=>{const {CityDownload}=await import('/city-offline.mjs');return new CityDownload().status('13103');});
 assert.ok(saved.urls.some(u=>u.includes('/13103/risk/index.json')));
 await context.setOffline(true);
 const checks=await page.evaluate(async()=>{
  const {geocodeCandidates}=await import('/city-addresses.mjs'),{buildingsAt}=await import('/building-picker.mjs');
  return {venue:(await geocodeCandidates('虎ノ門ヒルズ','13103')).length,address:(await geocodeCandidates('港区虎ノ門1丁目23番1号','13103')).length,buildings:(await buildingsAt(35.6668,139.7495,'13103')).length,ordinary:window.testOrdinary.code};
 });
 assert.ok(checks.venue>0&&checks.address>0);assert.ok(checks.buildings>0);assert.equal(checks.ordinary,'22205');
 await page.evaluate(async()=>{
  const lib=await import('/maplibre-gl.mjs'),{installBuildingRisk}=await import('/building-risk.mjs'),{CITY_DATA}=await import('/city-addresses.mjs'),{mapResourceUrl}=await import('/city-offline.mjs');
  lib.setWorkerUrl('/maplibre-gl-worker.mjs');lib.setWorkerCount(1);
  const host=document.createElement('div');host.id='verified-toranomon-map';host.style='position:fixed;inset:0;z-index:99999;background:white';document.body.append(host);
  window.venueRiskMap=new lib.Map({container:host,style:CITY_DATA+'map-style.json',center:[139.7495,35.6665],zoom:16,localIdeographFontFamily:'sans-serif',transformRequest:url=>({url:mapResourceUrl(url)})});
  await installBuildingRisk(window.venueRiskMap);
 });
 await page.waitForFunction(()=>window.venueRiskMap.getLayer('toranomon-risk-buildings')&&window.venueRiskMap.queryRenderedFeatures({layers:['toranomon-risk-buildings']}).length>0,null,{timeout:30000});
 checks.rendered=await page.evaluate(()=>window.venueRiskMap.queryRenderedFeatures({layers:['toranomon-risk-buildings']}).length);
 await page.screenshot({path:out+'/saved.png'});
 await writeFile(out+'/result.json',JSON.stringify({base,passed:true,bytes:saved.bytes,files:saved.files,...checks},null,2));
 console.log(JSON.stringify({passed:true,bytes:saved.bytes,files:saved.files,...checks}));
}catch(e){await page.screenshot({path:out+'/failure.png'});console.error(await page.locator('#venue-map-test').innerText().catch(()=>''));throw e;}
finally{await browser.close();await server.close();}
