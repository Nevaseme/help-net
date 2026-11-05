import {readFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL('C:/Users/TAKUMI/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs'));
const out=resolve('output/ui-quality');await mkdir(out,{recursive:true});
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
try {
 for(const [name,ref,current] of [['people','05-people-ja','05-people-ja'],['response','10-response-ja','10-response-ja'],['detail','11-report-detail-ja','11-report-detail-ja'],['safety','18-my-status-ja','18-my-status-ja'],['map','01-map-ja','01-map-ja']]) {
  const a=await readFile(`docs/superpowers/plans/assets/2026-10-30-web-1617/${ref}.jpg`),b=await readFile(`output/ui-quality/android/${current}.png`);
  const page=await browser.newPage({viewport:{width:824,height:920}});
  await page.setContent(`<style>body{margin:0;background:#e8edf2;font:16px sans-serif}main{display:flex;gap:24px}figure{margin:0;width:400px}.ref{height:860px;width:400px;overflow:hidden;position:relative}.ref img{width:440px;max-width:none;position:absolute;left:-24px;top:-92px}figcaption{height:36px;display:grid;place-content:center}img{width:400px;height:auto}</style><main><figure><figcaption>Web 1.6.17 / reference</figcaption><div class="ref"><img src="data:image/jpeg;base64,${a.toString('base64')}"></div></figure><figure><figcaption>Android / actual</figcaption><div style="height:842px;overflow:hidden"><img style="margin-top:-23.3px" src="data:image/png;base64,${b.toString('base64')}"></div></figure></main>`);
  await page.screenshot({path:`${out}/${name}-${'android'}.png`});await page.close();
 }
}finally{await browser.close();}
