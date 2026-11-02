// Isolated browser test: fictional invitations and an APK fixture, no real membership or downloads.
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {tmpdir} from 'node:os';
import {pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const out = resolve(process.env.JOIN_TEST_OUTPUT || join(tmpdir(),'pland-join-browser'));
await mkdir(out,{recursive:true});
const origin = 'https://audience.pland.test', invite = '12345678-1234-1234-1234-123456789abc:' + 'a'.repeat(64);
const release = {version:'0.0.0-test',url:'/demo-test.apk',sha256:'b'.repeat(64),bytes:12000000};
const assets = new Set(['join.html','join.css','join.mjs','join-qrcode.js','icon.svg']);
let releaseMode = 'ready';
const server = createServer(async (request,response) => {
  const path = new URL(request.url,'http://localhost').pathname;
  if (path === '/demo-release.json') { response.writeHead(releaseMode === 'missing' ? 404 : 200,{'content-type':'application/json'});response.end(JSON.stringify(releaseMode === 'invalid' ? {...release,url:'javascript:alert(1)'} : release));return; }
  if (path === '/demo-test.apk') { response.writeHead(200,{'content-type':'application/vnd.android.package-archive','content-disposition':'attachment; filename="test-only.apk"'});response.end('TEST FIXTURE — NOT AN APK');return; }
  const name = path.slice(1);
  if (!assets.has(name)) {response.writeHead(404);response.end();return;}
  const mime = {html:'text/html',css:'text/css',js:'text/javascript',mjs:'text/javascript',svg:'image/svg+xml'};
  response.writeHead(200,{'content-type':mime[name.split('.').at(-1)] + '; charset=utf-8'});
  response.end(await readFile(new URL('../web/' + name, import.meta.url)));
});
await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
const local = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch({headless:true,executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE});
const context = await browser.newContext({viewport:{width:390,height:844},permissions:['clipboard-read','clipboard-write']});
const page = await context.newPage(), errors = [], requests = [], checks = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {if (['warning','error'].includes(message.type()) && !/404/.test(message.text())) errors.push(message.text());});
page.on('request', request => requests.push({url:request.url(),body:request.postData(),headers:request.headers()}));
await context.route(origin + '/**', async route => {
  const url = new URL(route.request().url());
  const response = await fetch(local + url.pathname + url.search);
  await route.fulfill({status:response.status,headers:Object.fromEntries(response.headers),body:Buffer.from(await response.arrayBuffer())});
});
const note = message => {checks.push(message);console.log(message);};
try {
  await page.goto(origin + '/join.html#' + invite);
  await page.waitForFunction(() => document.querySelector('#apk-download').getAttribute('aria-disabled') === 'false');
  assert.equal(await page.title(),'デモに参加 · Tasukeai');
  assert.equal(await page.locator('h1').textContent(),'デモに参加');
  assert.equal(new URL(await page.locator('#app-join').getAttribute('href')).searchParams.get('invite'), invite);
  assert.match(await page.locator('#invite-state').textContent(),/招待を確認しました/);
  assert.equal(await page.locator('[data-nextjs-dialog],vite-error-overlay').count(),0);
  assert.ok(await page.locator('main').innerText());
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await page.screenshot({path:join(out,'join-mobile.png'),fullPage:true});
  note('Mobile page identity, meaningful content, no overlay, layout width, and validated join intent pass');
  await page.locator('#manual-invite summary').click();
  await page.locator('#copy-invite').click();
  assert.equal(await page.evaluate(() => navigator.clipboard.readText()),invite);
  assert.match(await page.locator('#copy-state').textContent(),/コピーしました/);
  await page.locator('#qr-section summary').click();
  assert.equal(await page.locator('#qr svg').count(),1);
  assert.equal(await page.locator('#print-qr').isDisabled(),false);
  if (process.env.JSQR_MODULE) {
    const jsQR = createRequire(import.meta.url)(process.env.JSQR_MODULE);
    const pixels = await page.locator('#qr svg').evaluate(async svg => {
      const image = new Image();
      image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(svg));
      await image.decode();
      const canvas = document.createElement('canvas');canvas.width=800;canvas.height=800;
      const context = canvas.getContext('2d');context.fillStyle='#fff';context.fillRect(0,0,800,800);context.drawImage(image,0,0,800,800);
      return Array.from(context.getImageData(0,0,800,800).data);
    });
    assert.equal(jsQR(new Uint8ClampedArray(pixels),800,800)?.data,origin + '/join.html#' + invite);
    note('Independent jsQR decoding of rendered QR pixels recovers the exact HTTPS invite URL');
  } else note('Independent QR decoding skipped: set JSQR_MODULE to a local jsqr installation');
  await page.evaluate(() => {window.__printed=false;window.print=()=>{window.__printed=true;};});
  await page.locator('#print-qr').click();
  assert.equal(await page.evaluate(()=>window.__printed),true);
  await page.screenshot({path:join(out,'join-qr-mobile.png'),fullPage:true});
  const downloaded = page.waitForEvent('download');
  await page.locator('#apk-download').click();
  assert.equal((await downloaded).suggestedFilename(),'test-only.apk');
  note('Invite copy, QR display/print, and metadata-backed APK download controls respond');
  await page.setViewportSize({width:1280,height:900});
  await page.locator('#manual-invite summary').click();
  await page.screenshot({path:join(out,'join-desktop.png'),fullPage:true});
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),true);
  for (const mode of ['missing','invalid']) {
    releaseMode = mode;
    await page.goto(origin + '/join.html#' + invite);
    await page.waitForFunction(() => document.querySelector('#release-state').textContent.includes('現在ダウンロードできません'));
    assert.equal(await page.locator('#apk-download').getAttribute('href'),null);
    assert.equal(await page.locator('#apk-download').getAttribute('aria-disabled'),'true');
  }
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:join(out,'join-unavailable.png'),fullPage:true});
  note('Missing and unsafe release metadata keep APK download unavailable with clear guidance');
  releaseMode = 'ready';
  await page.goto(origin + '/join.html#https://evil.test');
  assert.equal(await page.locator('#app-join').getAttribute('href'),null);
  assert.equal(await page.locator('#qr-section').isVisible(),false);
  assert.match(await page.locator('#invite-state').textContent(),/確認できません/);
  await page.goto(origin + '/join.html');
  assert.match(await page.locator('#invite-state').textContent(),/受け取ってください/);
  await page.locator('#invite-entry summary').click();
  await page.locator('#invite-input').fill('wrong');
  await page.locator('#invite-form button').click();
  assert.match(await page.locator('#invite-input-state').textContent(),/形式が違います/);
  await page.locator('#invite-input').fill(invite);
  await page.locator('#invite-form button').click();
  assert.equal(new URL(page.url()).hash,'#' + invite);
  assert.equal(await page.locator('#app-join').getAttribute('aria-disabled'),'false');
  await page.evaluate(() => Object.defineProperty(navigator,'clipboard',{value:{writeText:async()=>{throw new Error('Denied');}},configurable:true}));
  await page.locator('#manual-invite summary').click();
  await page.locator('#copy-invite').click();
  assert.match(await page.locator('#copy-state').textContent(),/コードを選択しました/);
  assert.equal(await page.locator('#invite-code').evaluate(el => el.selectionEnd - el.selectionStart),101);
  note('Missing/invalid invites disable app launch; manual entry and denied-clipboard fallback work');
  for (const request of requests) {
    assert.ok(!JSON.stringify(request).includes('a'.repeat(64)), 'Invitation leaked into a network request');
    assert.equal(new URL(request.url).origin,origin,'Unexpected third-party request');
  }
  assert.deepEqual(errors,[]);
  note('No invitation in network URLs/body/headers, no external requests, and no relevant console/runtime errors');
  await writeFile(join(out,'verification.json'),JSON.stringify({passed:true,origin,viewports:['390×844','1280×900'],checks,errors,requestCount:requests.length},null,2));
} catch (error) {
  await page.screenshot({path:join(out,'failure.png'),fullPage:true}).catch(()=>{});
  throw error;
} finally {await browser.close();await new Promise(resolve=>server.close(resolve));}
