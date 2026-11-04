import {mkdir,readFile,writeFile,copyFile,readdir,rm} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {gzipSync} from 'node:zlib';
import {build} from 'esbuild';

const root=fileURLToPath(new URL('../',import.meta.url)),web=join(root,'../lab/web');
for(const dir of ['dist/src','dist/shared','dist/public'])await mkdir(join(root,dir),{recursive:true});
// Keep rejected history and the offline research solver out of the saved-result PWA.
const archivedAssets=new Set(['tsunami-atami.json','tsunami-domain-atami.json','tsunami-compute.mjs','tsunami-solver.mjs','tsunami-solver-worker.mjs']);
const licenseAssets=new Set(['ASSETS-LICENSE.md','LEAFLET-LICENSE.md','web1617-LICENSES.md','web1617-phosphor-LICENSE.txt','maplibre-LICENSE.txt','leaflet-maplibre-LICENSE.txt']);
for(const name of archivedAssets)await rm(join(root,'dist/public',name),{force:true});
// Copy only the public PWA and pure shared modules. No lab server config, certificate or token is read.
for(const entry of await readdir(web,{withFileTypes:true})){
 if(!entry.isFile()||(!/[.](mjs|js|html|css|svg|webmanifest|png|ico|json)$/.test(entry.name)&&!licenseAssets.has(entry.name)))continue;
 if(archivedAssets.has(entry.name))continue;
 await copyFile(join(web,entry.name),join(root,'dist/public',entry.name));
 if(entry.name==='index.html'){
  const html=await readFile(join(web,entry.name),'utf8');
  await writeFile(join(root,'dist/public/index.html'),html.replace('<head>','<head><meta name="tasukeai-cloud" content="1">'));
 }
 if(entry.name==='manifest.webmanifest'){
  const manifest=JSON.parse(await readFile(join(web,entry.name),'utf8'));
  await writeFile(join(root,'dist/public',entry.name),JSON.stringify({...manifest,name:'Tasukeai — 安否と対応の共有',short_name:'Tasukeai'}));
 }
 if(entry.name.endsWith('.mjs'))await copyFile(join(web,entry.name),join(root,'dist/shared',entry.name));
}
await copyFile(join(root,'src/venue-buildings.json'),join(root,'dist/public/demo-building-addresses.json'));
await copyFile(join(root,'../tools/building_analysis/venue-address-evidence.json'),join(root,'dist/public/demo-building-address-sources.json'));
for(const entry of await readdir(join(root,'src'))){
 if(entry==='venue-buildings.json'){await copyFile(join(root,'src',entry),join(root,'dist/src',entry));continue;}
 if(!entry.endsWith('.mjs'))continue;
 const source=(await readFile(join(root,'src',entry),'utf8')).replaceAll('../../lab/web/','../shared/');
 await writeFile(join(root,'dist/src',entry),source);
}
await writeFile(join(root,'dist/public/_headers'),'/api/*\n  Cache-Control: no-store\n/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: no-referrer\n  X-Frame-Options: DENY\n');
console.log('Built isolated cloud-sync Worker and public PWA assets. No deployment performed.');
if(process.argv.includes('--standalone')){
 const mime={html:'text/html; charset=utf-8',css:'text/css; charset=utf-8',js:'text/javascript; charset=utf-8',mjs:'text/javascript; charset=utf-8',svg:'image/svg+xml',webmanifest:'application/manifest+json',json:'application/json',png:'image/png',ico:'image/x-icon',md:'text/plain; charset=utf-8',txt:'text/plain; charset=utf-8'};
 const assets={};
 for(const file of await readdir(join(root,'dist/public'))){
  if(file.startsWith('_'))continue;
  const type=mime[file.split('.').at(-1)];if(!type)continue;
  const binary=['png','ico'].includes(file.split('.').at(-1));
  assets['/'+file]={type,binary,data:(await readFile(join(root,'dist/public',file))).toString(binary?'base64':'utf8')};
 }
 const source=`import worker from './src/worker.mjs';
export {GroupJournal} from './src/worker.mjs';
const assets=${JSON.stringify(assets)};
export default {async fetch(request,env,ctx){
 const url=new URL(request.url);
 if(url.pathname.startsWith('/api/'))return worker.fetch(request,env,ctx);
 const headers={'X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','X-Frame-Options':'DENY','Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://cyberjapandata.gsi.go.jp; worker-src 'self' blob:; connect-src 'self' https://nevaseme.github.io https://tiles.openfreemap.org;  base-uri 'none'; frame-ancestors 'none'",'Cache-Control':'public, max-age=0, must-revalidate'};
 if(!['GET','HEAD'].includes(request.method))return new Response('Method not allowed',{status:405,headers});
 const asset=assets[url.pathname==='/'?'/index.html':url.pathname];
 if(!asset)return new Response('Not found',{status:404,headers});
 // Text assets (including precomputed maps) do not need a per-byte JS base64 loop.
 return new Response(request.method==='HEAD'?null:asset.binary?Uint8Array.from(atob(asset.data),c=>c.charCodeAt(0)):asset.data,{headers:{...headers,'Content-Type':asset.type}});
}};`;
 const result=await build({stdin:{contents:source,resolveDir:join(root,'dist'),sourcefile:'standalone-entry.mjs'},bundle:true,format:'esm',write:false,external:['cloudflare:workers'],platform:'browser',minify:true});
 const bytes=result.outputFiles[0].contents,compressed=gzipSync(bytes).length;
 if(compressed>3*1024*1024)throw Error('Standalone bundle exceeds the Free compressed Worker size limit');
 await writeFile(join(root,'dist/standalone.mjs'),bytes);
 const metadata={main_module:'standalone.mjs',compatibility_date:'2026-10-29',compatibility_flags:['nodejs_compat'],bindings:[{type:'durable_object_namespace',name:'GROUPS',class_name:'GroupJournal'},{type:'ratelimit',name:'SIGNUP_LIMITER',namespace_id:'10302026',simple:{limit:30,period:60}}],migrations:{new_tag:'v1',new_sqlite_classes:['GroupJournal']}};
 await writeFile(join(root,'dist/standalone-metadata.json'),JSON.stringify(metadata,null,2));
 console.log(`Standalone bundle: ${bytes.length} bytes, ${compressed} bytes gzip. Metadata is for first deployment; omit the migration on unchanged subsequent uploads.`);
}
if(process.argv.includes('--check')||process.argv.includes('--dev')){
 const command=process.argv.includes('--dev')?['dev']:['deploy','--dry-run'];
 const result=spawnSync(process.execPath,[join(root,'node_modules/wrangler/bin/wrangler.js'),...command],{cwd:root,stdio:'inherit'});
 process.exitCode=result.status??1;
}
