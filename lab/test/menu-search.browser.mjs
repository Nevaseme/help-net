// Real browser, isolated profiles and fictional groups; no existing device data.
import assert from 'node:assert/strict';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {startVenueServer} from './venue-server.mjs';
import {chromium} from '../../tools/browser-runtime.mjs';
const server=await startVenueServer(),base=process.env.CITY_TEST_URL||server.base;
const out='output/qa/menu-search-1542/pwa';await mkdir(out,{recursive:true});
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const context=await browser.newContext({viewport:{width:400,height:860}}),other=await browser.newContext({viewport:{width:390,height:844}}),page=await context.newPage(),member=await other.newPage(),errors=[];
for(const p of [page,member])p.on('pageerror',e=>errors.push(e.message));
const menu=async(p,name)=>{for(const d of await p.locator('dialog.design-sheet[open]').all())await d.locator('.sheet-close').click();await p.locator('#menu-open').click();await p.locator(`[data-menu="${name}"]`).click();};
const sync=async p=>{await menu(p,'group');await p.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled);const response=p.waitForResponse(r=>r.url().includes('/records?')&&r.request().method()==='GET');await p.locator('#cloud-sync').click();await response;await p.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled&&document.querySelector('#cloud-status').textContent.includes('共有サーバーへ保存済み'));};
try{
 if(!process.env.CITY_TEST_URL)await page.route('https://nevaseme.github.io/safety_confirm/data/cities/**',async route=>{const path=new URL(route.request().url()).pathname.split('/data/cities/')[1];await route.fulfill({body:await readFile('demo/data/cities/'+path),contentType:path.endsWith('.json')?'application/json':'application/octet-stream'});});
 await page.goto(base);await page.locator('#cloud-group-name').fill('検索確認・架空');await page.locator('#cloud-admin-name').fill('架空管理者');await page.locator('#cloud-create button').click();await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('架空管理者'));
 await menu(page,'group');await page.locator('#cloud-city').selectOption('26100');await page.locator('#cloud-city-form button').click();await page.waitForFunction(()=>document.querySelector('#cloud-city-state').textContent.includes('京都市'));
 await menu(page,'settings');await page.screenshot({path:out+'/settings.png'});await page.locator('#menu-open').click();assert.equal(await page.locator('#design-settings').evaluate(e=>e.open),false,'Hamburger must close settings before opening menu');assert.equal(await page.locator('#app-menu').evaluate(e=>e.open),true);
 await page.locator('#menu-open').click();assert.equal(await page.locator('#app-menu').evaluate(e=>e.open),false);
 await menu(page,'settings');await page.locator('#design-settings .sheet-handle').click();assert.equal(await page.locator('#design-settings').evaluate(e=>e.open),false);
 await menu(page,'settings');const handle=await page.locator('#design-settings .sheet-handle').boundingBox();await page.mouse.move(handle.x+handle.width/2,handle.y+handle.height/2);await page.mouse.down();await page.mouse.move(handle.x+handle.width/2,handle.y+handle.height/2+90,{steps:10});await page.mouse.up();assert.equal(await page.locator('#design-settings').evaluate(e=>e.open),false);console.log('Menu toggle, settings dismissal, handle tap and swipe passed');
 await menu(page,'group');await page.locator('#cloud-city').selectOption('22205');await page.locator('#cloud-city-form button').click();await page.waitForFunction(()=>document.querySelector('#cloud-city-state').textContent.includes('熱海市'));
 await menu(page,'profile');await page.locator('#name').fill('架空管理者');await page.locator('#lat').fill('35.0145');await page.locator('#lon').fill('135.7625');await page.locator('#profile-save').click();await page.waitForFunction(()=>document.querySelector('#profile-feedback').dataset.status==='success');await page.locator('#profile-building').click();
 await page.locator('#building-search').fill('左京区');await page.locator('#building-search-results button').first().waitFor();assert.match(await page.locator('#building-search-results').textContent(),/左京区/);await page.screenshot({path:out+'/address-search.png'});
 await page.locator('#building-search').fill('京都タワー');await page.locator('#building-search-results button').filter({hasText:'京都タワー'}).first().waitFor();await page.screenshot({path:out+'/facility-search.png'});await page.locator('#building-search-results button').filter({hasText:'京都タワー'}).first().click();await page.locator('.building-picker-map').waitFor();assert.equal(await page.locator('#building-label').inputValue(),'京都タワー');assert.equal(await page.locator('#building-address').inputValue(),'','Facility names are not street addresses');await page.locator('[data-back]').click();
 await page.locator('#building-cancel').click();await page.locator('#building-search').fill('見つからない架空施設123456');await page.waitForFunction(()=>document.querySelector('#building-search-state').textContent.includes('見つかりません'));assert.deepEqual(errors,[]);console.log('Ward and landmark search, map selection and empty result passed');
 if(process.env.MENU_OFFLINE==='1'){
 await menu(page,'group');await page.locator('#cloud-city').selectOption('26100');await page.locator('#cloud-city-form button').click();await page.waitForFunction(()=>document.querySelector('#cloud-city-state').textContent.includes('京都市'));
 await page.evaluate(async()=>{const url='https://nevaseme.github.io/safety_confirm/data/cities/26100/city.json';const city=await(await fetch(url,{cache:'reload'})).json();delete city.places;await(await caches.open('tasukeai-city-v1-26100')).put(url,Response.json(city));});
 await menu(page,'settings');await page.locator('[data-city-download]').click();
 await page.waitForFunction(()=>document.querySelector('[data-city-state]').textContent.includes('保存済み'),{},{timeout:480000});console.log('City package, including facility index, saved');
 await context.setOffline(true);await page.reload();await page.locator('#menu-open').waitFor();await menu(page,'profile');await page.locator('#profile-building').click();await page.locator('#building-search').fill('京都タワー');await page.locator('#building-search-results button').filter({hasText:'京都タワー'}).first().waitFor();console.log('Facility search passed after offline reload');
 }
 await writeFile(out+'/verification.json',JSON.stringify({passed:true,base,errors},null,2));
}catch(e){await page.screenshot({path:out+'/failure.png'}).catch(()=>{});throw e;}finally{await browser.close();await server.close();}
