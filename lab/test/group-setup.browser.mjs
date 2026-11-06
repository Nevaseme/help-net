import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {chromium} from '../../tools/browser-runtime.mjs';
import {startVenueServer} from './venue-server.mjs';
// Browser plugin not available; isolated Playwright profile, no personal browser state.
const server=await startVenueServer(),base=process.env.GROUP_TEST_URL||server.base;
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
const context=await browser.newContext({viewport:{width:390,height:844}}),page=await context.newPage(),errors=[];
const out=process.env.GROUP_TEST_OUTPUT||'.implementation/group-browser';await mkdir(out,{recursive:true});
page.on('pageerror',e=>errors.push(e.message));
let starts=0;page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/venue/start'))starts++;});
async function group(){await page.locator('#menu-open').click();await page.getByRole('button',{name:'グループ',exact:true}).click();}
try{
 await page.goto(base);
 await page.evaluate(async()=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud');await new CloudClient(s,await s.identity()).register('create',{groupName:'Group UX '+Date.now(),activityName:'Test organizer'});s.close();});
 await page.reload();await group();
 assert.equal(await page.locator('#group-details').isVisible(),false);
 assert.equal(await page.locator('#cloud-communication').isVisible(),false);
 await page.locator('#venue-admin-password').fill('123456');await page.locator('#venue-start').click();
 assert.match(await page.locator('#venue-feedback').innerText(),/現在6文字/);assert.equal(starts,0);
 await page.locator('#venue-position-mode').selectOption('ACTUAL');
 await page.locator('#group-settings-open').click();assert.equal(await page.locator('#venue-admin').isVisible(),false);
 assert.match(await page.locator('#group-details').innerText(),/通常の地図保存と住所検索/);
 await page.waitForTimeout(16000);assert.equal(await page.locator('#venue-admin').isVisible(),false);
 await page.locator('#group-details').getByRole('button',{name:'通信の詳細',exact:true}).click();assert.equal(await page.locator('#cloud-communication').isVisible(),true);assert.equal(await page.locator('#group-details').isVisible(),false);
 await page.locator('#group-page-back').click();assert.equal(await page.locator('#venue-admin-password').inputValue(),'123456');assert.equal(await page.locator('#venue-position-mode').inputValue(),'ACTUAL');
 await page.locator('#venue-admin-password').fill('DemoTest2026');await page.locator('#venue-position-mode').selectOption('KYOTO');
 await page.locator('#venue-password-visibility').click();assert.equal(await page.locator('#venue-admin-password').getAttribute('type'),'text');await page.locator('#venue-password-visibility').click();
 await page.screenshot({path:out+'/group-setup.png'});
 await page.setViewportSize({width:320,height:740});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await page.locator('#venue-start').click();await page.waitForFunction(()=>document.body.dataset.page==='safety'&&!document.querySelector('#safe').disabled);
 await page.locator('#safe').click();await page.locator('#status-update').click();await page.waitForFunction(()=>document.body.dataset.page==='buildings');
 await group();assert.match(await page.locator('#group-demo-state').innerText(),/デモ開催中/);
 assert.equal(await page.locator('#venue-admin').isVisible(),false);
 assert.deepEqual(errors,[]);
 await writeFile(out+'/result.json',JSON.stringify({passed:true,checks:['Short password produces inline error without request','Settings replace setup; remain stable through background sync','Mode/password survive navigation','Valid password starts demo, answer leads to map','320 px layout, no runtime errors']},null,2));
 console.log('Group setup browser checks passed');
}catch(e){await page.screenshot({path:out+'/failure.png'});await writeFile(out+'/failure.txt',String(e)+'\n'+errors.join('\n'));throw e;}
finally{await browser.close();await server.close();}
