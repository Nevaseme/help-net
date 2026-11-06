// Package the supplied mark without redrawing it. Run with Playwright + Chrome.
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE||'playwright');
const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
try {
 const page=await browser.newPage();
 const mark='data:image/png;base64,'+(await readFile(path.join(root,'image/branding/help-net-mark.png'))).toString('base64');
 const dimensions=await page.evaluate(async src=>{const i=new Image();i.src=src;await i.decode();window.mark=i;return {width:i.width,height:i.height};},mark);
 async function raster(file,size,heightRatio,white=true,type='image/png') {
  const data=await page.evaluate(({size,heightRatio,white,type})=>{
   const c=document.createElement('canvas');c.width=c.height=size;const x=c.getContext('2d');
   if(white){x.fillStyle='#ffffff';x.fillRect(0,0,size,size);}
   const h=size*heightRatio,w=h*window.mark.width/window.mark.height;
   x.drawImage(window.mark,(size-w)/2,(size-h)/2,w,h);
   return c.toDataURL(type,1).split(',')[1];
  },{size,heightRatio,white,type});
  const target=path.join(root,file);await mkdir(path.dirname(target),{recursive:true});await writeFile(target,Buffer.from(data,'base64'));
 }
 const h=736,w=h*dimensions.width/dimensions.height;
 await writeFile(path.join(root,'lab/web/icon.svg'),`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><rect width="1024" height="1024" fill="#fff"/><image x="${(1024-w)/2}" y="${(1024-h)/2}" width="${w}" height="${h}" href="${mark}"/></svg>\n`);
 for(const size of [192,512])await raster(`lab/web/icon-${size}.png`,size,.71875);
 await raster('lab/web/icon-maskable-512.png',512,.62);
 await raster('lab/web/apple-touch-icon.png',180,.71875);
 await raster('android/app/src/main/res/drawable-nodpi/help_net_foreground.png',432,58/108,false);
 for(const [density,size] of Object.entries({mdpi:48,hdpi:72,xhdpi:96,xxhdpi:144,xxxhdpi:192})){
  for(const name of ['ic_launcher','ic_launcher_round'])await raster(`android/app/src/main/res/mipmap-${density}/${name}.webp`,size,.71875,true,'image/webp');
 }
 console.log(JSON.stringify({source:dimensions,outputs:16}));
} finally {await browser.close();}
