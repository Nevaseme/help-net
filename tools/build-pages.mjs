// Keep all existing comparison files; only replace the public entry page.
import {cp,copyFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));
const out=path.join(root,'output/pages');
await mkdir(out,{recursive:true});
await cp(path.join(root,'demo'),out,{recursive:true});
await copyFile(path.join(root,'demo/index.html'),path.join(out,'comparison.html'));
for(const name of ['index.html','portal.css','portal.mjs','icon-192.png'])await copyFile(path.join(root,'demo/help-net',name),path.join(out,name));
console.log('Help Net participant guide and preserved comparison history: output/pages');
