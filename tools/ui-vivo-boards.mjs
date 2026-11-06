import {readFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {chromium} from './browser-runtime.mjs';
const out=resolve('output/ui-quality');await mkdir(out,{recursive:true});
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
try {
 for(const [name,ref,current] of [['people','05-people-ja','people'],['safety','18-my-status-ja','safety'],['map','01-map-ja','map'],['menu','02-menu-ja','menu'],['settings','03-settings-ja','settings']]) {
  const a=await readFile(`docs/superpowers/plans/assets/2026-10-30-web-1617/${ref}.jpg`),b=await readFile(`output/ui-quality/vivo/${current}.png`);
  const page=await browser.newPage({viewport:{width:744,height:920}});
  await page.setContent(`<style>body{margin:0;background:#e8edf2;font:16px sans-serif}main{display:flex;gap:24px}figure{margin:0;width:360px}.ref{height:774px;width:360px;overflow:hidden;position:relative}.ref img{width:396px;max-width:none;position:absolute;left:-21.6px;top:-82.8px}figcaption{height:36px;display:grid;place-content:center}img{width:360px;height:auto}</style><main><figure><figcaption>Web 1.6.17 / reference</figcaption><div class="ref"><img src="data:image/jpeg;base64,${a.toString('base64')}"></div></figure><figure><figcaption>vivo / actual</figcaption><div style="height:760px;overflow:hidden"><img style="margin-top:-40px" src="data:image/png;base64,${b.toString('base64')}"></div></figure></main>`);
  await page.screenshot({path:`${out}/${name}-${'vivo'}.png`});await page.close();
 }
}finally{await browser.close();}
