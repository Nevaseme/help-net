import {readFile,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const checked=[];
for(const name of ['index.html','style.css','app.mjs','web1617-ui.mjs','cloud-ui.mjs','sw.js','form-drafts.mjs','app-update.mjs']){
 let local=await readFile('lab/web/'+name,'utf8');
 if(name==='index.html')local=local.replace('<head>','<head><meta name="tasukeai-cloud" content="1">');
 const response=await fetch('https://tasukeai-sync.tomodachi-takumi.workers.dev/'+name);
 assert.equal(response.status,200);assert.ok((await response.text()).replaceAll('\r\n','\n')===local.replaceAll('\r\n','\n'),name+' content mismatch');checked.push(name);
}
await writeFile('output/platform-quality/public-assets.json',JSON.stringify({matched:true,checked},null,2));console.log('All eight published assets match.');
