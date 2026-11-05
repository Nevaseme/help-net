import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs').href);
const out='output/platform-quality/pwa';await mkdir(out,{recursive:true});
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const page=await browser.newPage({viewport:{width:400,height:860}}),errors=[],checks=[];page.on('pageerror',e=>errors.push(e.message));
const openEditor=async()=>{await page.locator('[data-tab="response"]').click();await page.locator('.case-row').filter({hasText:'建物倒壊'}).click();await page.locator('[data-edit="progress"]').click();};
try{
 await page.goto('http://127.0.0.1:4227/');await page.locator('.leaflet-container').waitFor();
 await openEditor();await page.locator('[name="result"]').fill('タブ移動後も残す下書き');await page.locator('[name="status"][value="IN_PROGRESS"]').check();
 await page.locator('[data-tab="buildings"]').click();await openEditor();assert.equal(await page.locator('[name="result"]').inputValue(),'タブ移動後も残す下書き');assert.ok(await page.locator('[value="IN_PROGRESS"]').isChecked());checks.push('Draft survives tab navigation');
 await page.locator('#response-editor .sheet-close').click();await page.locator('[data-edit="progress"]').click();assert.equal(await page.locator('[name="result"]').inputValue(),'タブ移動後も残す下書き');checks.push('Draft survives close and reopen');
 await page.reload();await page.locator('.leaflet-container').waitFor();await openEditor();assert.equal(await page.locator('[name="result"]').inputValue(),'タブ移動後も残す下書き');checks.push('Draft survives page reload');
 await page.screenshot({path:out+'/draft.png'});
 await page.locator('#response-edit-form button[type="submit"]').click();await page.locator('#response-editor').waitFor({state:'hidden'});await page.locator('[data-edit="progress"]').click();assert.equal(await page.locator('[name="result"]').inputValue(),'タブ移動後も残す下書き');assert.equal(await page.evaluate(()=>Object.keys(sessionStorage).filter(k=>k.startsWith('tasukeai-draft:')).length),0);checks.push('Successful save clears draft and preserves record');
 await page.locator('[data-tab="buildings"]').click();assert.ok((await page.locator('.map-legend summary').boundingBox()).height>=48);checks.push('Legend target >=48 CSS pixels');
 for(const mode of ['light','dark']){
  await page.emulateMedia({colorScheme:mode});
  for(const tab of ['buildings','residents','response','safety']){await page.locator(`[data-tab="${tab}"]`).click();await page.screenshot({path:`${out}/${mode}-${tab}.png`});}
  await page.locator('#menu-open').click();await page.screenshot({path:`${out}/${mode}-menu.png`});await page.locator('#menu-close').click();
  const rgb=await page.locator('html').evaluate(e=>getComputedStyle(e).backgroundColor);assert.equal(rgb,mode==='dark'?'rgb(16, 33, 43)':'rgb(255, 255, 255)');
 }
 checks.push('Light and dark main screens and menu');
 await page.emulateMedia({colorScheme:'light'});await page.setViewportSize({width:320,height:640});await page.evaluate(()=>document.documentElement.style.fontSize='200%');
 await page.locator('#menu-open').click();await page.locator('[data-menu="settings"]').click();await page.locator('[name="ui-language"][value="en"]').check();await page.screenshot({path:out+'/large-english.png'});
 const nav=await page.locator('.app-nav').evaluate(e=>({height:e.getBoundingClientRect().height,labels:[...e.querySelectorAll('button')].map(b=>b.innerText.trim()),overflow:document.documentElement.scrollWidth>innerWidth}));assert.deepEqual(nav.labels,['Map','People','Tasks','Safety']);assert.ok(nav.height<120);assert.equal(nav.overflow,false);checks.push('200% English labels remain whole and navigation under120px');
 await page.locator('#design-settings .sheet-close').click();await page.locator('#menu-open').click();await page.locator('.menu-items').evaluate(e=>e.scrollTop=e.scrollHeight);assert.ok(await page.locator('#menu-close').isVisible());await page.keyboard.press('Escape');assert.ok(await page.locator('#menu-open').evaluate(e=>e===document.activeElement));checks.push('Large menu close and focus return');
 assert.deepEqual(errors,[]);await writeFile(out+'/verification.json',JSON.stringify({passed:true,checks,errors,nav},null,2));console.log('Platform quality PWA passed:',checks.length);
}catch(error){await page.screenshot({path:out+'/failure.png'});await writeFile(out+'/verification.json',JSON.stringify({passed:false,checks,errors,error:String(error)},null,2));throw error;}finally{await browser.close();}

