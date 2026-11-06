// Real browser, isolated profiles and fictional groups; no existing device data.
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {startVenueServer} from './venue-server.mjs';
import {chromium} from '../../tools/browser-runtime.mjs';
const server=await startVenueServer(),base=process.env.CITY_TEST_URL||server.base;
const out='output/qa/city-1541/pwa';await mkdir(out,{recursive:true});
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const context=await browser.newContext({viewport:{width:400,height:860}}),other=await browser.newContext({viewport:{width:390,height:844}}),page=await context.newPage(),member=await other.newPage(),errors=[];
for(const p of [page,member])p.on('pageerror',e=>errors.push(e.message));
const menu=async(p,name)=>{for(const d of await p.locator('dialog.design-sheet[open]').all())await d.locator('.sheet-close').click();await p.locator('#menu-open').click();await p.locator(`[data-menu="${name}"]`).click();};
const sync=async p=>{await menu(p,'group');await p.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled);const response=p.waitForResponse(r=>r.url().includes('/records?')&&r.request().method()==='GET');await p.locator('#cloud-sync').click();await response;await p.waitForFunction(()=>!document.querySelector('#cloud-sync').disabled&&document.querySelector('#cloud-status').textContent.includes('共有サーバーへ保存済み'));};
try{
 await page.goto(base);await page.locator('#cloud-group-name').fill('市全域試験・架空');await page.locator('#cloud-admin-name').fill('架空管理者');await page.locator('#cloud-create button').click();await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('架空管理者'));
 await menu(page,'group');await page.locator('#cloud-city').selectOption('26100');await page.locator('#cloud-city-form button').click();await page.waitForFunction(()=>document.querySelector('#cloud-city-state').textContent.includes('京都市'));await page.screenshot({path:out+'/group.png'});
 await page.locator('#cloud-invite').click();await page.waitForFunction(()=>document.querySelector('#cloud-invite-output').value.includes(':'));const invite=await page.locator('#cloud-invite-output').inputValue();assert.ok(invite.includes(':'));
 await member.goto(base);await member.locator('#cloud-mode').click();await member.locator('#cloud-invite-input').fill(invite);await member.locator('#cloud-member-name').fill('架空メンバー');await member.locator('#cloud-join button').click();await member.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('架空メンバー'));await menu(member,'group');assert.match(await member.locator('#cloud-city-state').textContent(),/京都市/);assert.equal(await member.locator('#cloud-city-form').isVisible(),false);console.log('Administrator city setting shared with member');
 await page.locator('#cloud-city').selectOption('22205');await page.locator('#cloud-city-form button').click();await page.waitForFunction(()=>document.querySelector('#cloud-city-state').textContent.includes('熱海市'));await sync(member);assert.match(await member.locator('#cloud-city-state').textContent(),/熱海市/);await menu(member,'settings');await member.waitForFunction(()=>document.querySelector('[data-city-state]').textContent.includes('熱海市'));await page.locator('#cloud-city').selectOption('26100');await page.locator('#cloud-city-form button').click();await page.waitForFunction(()=>document.querySelector('#cloud-city-state').textContent.includes('京都市'));await sync(member);console.log('Existing member receives city change without a membership change');
 await menu(member,'settings');await member.waitForFunction(()=>document.querySelector('[data-city-state]').textContent.includes('京都市'));
 await member.locator('[data-city-download]').click();await member.waitForFunction(()=>document.querySelector('[data-city-state]').textContent.includes('保存中'),{},{timeout:60000});
 await page.locator('#cloud-city').selectOption('22205');await page.locator('#cloud-city-form button').click();
 await member.waitForFunction(()=>document.querySelector('#cloud-city-state').textContent.includes('熱海市'),{},{timeout:45000});
 await member.locator('[data-city-pause]').click();await member.waitForFunction(()=>document.querySelector('[data-city-state]').textContent.includes('熱海市'));
 console.log('Automatic metadata sync and city changes while downloading passed');
 await member.locator('[data-city-download]').click();await member.waitForFunction(()=>document.querySelector('[data-city-state]').textContent.includes('保存済み'),{},{timeout:180000});
 await other.setOffline(true);await member.reload();await menu(member,'settings');await member.waitForFunction(()=>document.querySelector('[data-city-state]').textContent.includes('熱海市全域 · 保存済み'));
 console.log('Atami package survives offline reload');
 assert.deepEqual(errors,[]);await writeFile(out+'/municipality-verification.json',JSON.stringify({passed:true,base,errors},null,2));
}catch(e){await page.screenshot({path:out+'/failure.png'}).catch(()=>{});await writeFile(out+'/failure.json',JSON.stringify({error:String(e),errors},null,2));throw e;}
finally{await browser.close();await server.close();}
