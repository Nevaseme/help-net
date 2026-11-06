import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {chromium} from './browser-runtime.mjs';
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try {const page=await browser.newPage();await page.goto('https://tasukeai-sync.tomodachi-takumi.workers.dev/');const checks=[];
for(const name of ['index.html','style.css','ui-accessibility.mjs','app.mjs','cloud-ui.mjs','cloud-client.mjs','incidents.mjs','venue-demo.mjs','web1617-ui.mjs','tsunami-map.mjs','sw.js']) {const source=await readFile('lab/web/'+name,'utf8');const live=await page.evaluate(async name=>{const r=await fetch('/'+name+'?verify='+Date.now(),{cache:'no-store'});return {status:r.status,text:await r.text()};},name);assert.equal(live.status,200);assert.equal(live.text.replaceAll('\r\n','\n'),source.replaceAll('\r\n','\n'));checks.push(name);}
await writeFile('output/ui-quality/public-assets.json',JSON.stringify({passed:true,checks,cache:'r20-ui'},null,2));console.log('All eleven published assets match local source.');}finally {await browser.close();}
