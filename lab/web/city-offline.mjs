import {translateUI as t,bindStaticUiLanguage,observeUiLanguage} from './web1617-english.mjs';
import {mapMunicipality,demoMapMunicipality} from './municipalities.mjs';
import {CITY_DATA,verifiedBytes,refreshAddressPackage} from './city-addresses.mjs';
import {CITY_MAP_PATHS} from './city-map-resources.mjs';
export const CITY_CACHE_PREFIX='tasukeai-city-v2-';
async function completedCaches(code){const names=(await caches.keys()).filter(n=>n.startsWith(CITY_CACHE_PREFIX+code+'-')||n==='tasukeai-city-v1-'+code);const found=[];for(const name of names){const cache=await caches.open(name),marker=await cache.match('/__city_complete__');if(marker)found.push({name,cache,value:await marker.json()});}return found.sort((a,b)=>b.value.savedAt-a.value.savedAt);}
export const mapResourceUrl=url=>{if(!url.startsWith('https://tiles.openfreemap.org/'))return url;const path=new URL(url).pathname;return CITY_MAP_PATHS.has(path)?CITY_DATA+'map-assets/'+path.slice(1):url;};
export async function cityDownloadPlan(code){
 const read=async path=>{const r=await fetch(CITY_DATA+path,{cache:'reload'});if(!r.ok)throw Error('地域データを取得できません');return r.json();};
 const [city,map]=await Promise.all([read(code+'/city.json'),read(code+'/map-manifest.json')]);
 if(map.tileCount>3000||map.bytes>50_000_000)throw Error('この地域は地図配信の保存上限を超えています');
 let riskBytes=0,buildingBytes=0;
 const files=[{url:CITY_DATA+'map-style.json'},{url:CITY_DATA+code+'/city.json'},{url:CITY_DATA+code+'/map-manifest.json'},{url:CITY_DATA+city.addresses.path,...city.addresses},...map.resources.map(r=>({...r,url:mapResourceUrl(r.url)}))];
 if(city.places)files.push({url:CITY_DATA+city.places.path,...city.places});
 if(['26100','13103'].includes(code)){
  const risk=await read(code+'/risk/index.json');riskBytes=risk.bytes;files.push({url:CITY_DATA+code+'/risk/index.json'},...risk.tiles.map(t=>({url:CITY_DATA+code+'/risk/'+t.file,sha256:t.sha256,bytes:t.bytes})));
  const index=await read(code+'/buildings/index.json');
  files.push({url:CITY_DATA+code+'/buildings/index.json'},{url:CITY_DATA+code+'/buildings/model.json',sha256:index.modelSha256});
  buildingBytes=index.chunks.reduce((n,c)=>n+c.bytes,0);
  files.push(...index.chunks.map(c=>({url:CITY_DATA+code+'/buildings/'+c.file,sha256:c.sha256,bytes:c.bytes})));
 }
 return {code,name:city.name,files,bytes:map.bytes+city.addresses.bytes+(city.places?.bytes||0)+riskBytes+buildingBytes};
}
export class CityDownload {
 constructor(onProgress=()=>{}){this.onProgress=onProgress;this.controller=null;}
 pause(){this.controller?.abort();}
 async status(code){
  for(const {cache,value} of await completedCaches(code)){
   let complete=true;for(const url of value.urls)if(!await cache.match(url)){complete=false;break;}
   if(complete)return value;
  }return null;
 }
 async download(plan){
  if(this.controller)throw Error('地図を保存中です');
  const controller=new AbortController();this.controller=controller;const signal=controller.signal;
  const previous=await completedCaches(plan.code),prefix=CITY_CACHE_PREFIX+plan.code+'-';
  const partial=(await caches.keys()).find(n=>n.startsWith(prefix)&&!previous.some(p=>p.name===n));
  const cacheName=partial||prefix+crypto.randomUUID(),cache=await caches.open(cacheName);let count=0,bytes=0,next=0;
  try {
   const space=await navigator.storage?.estimate?.();if(space?.quota&&space.quota-space.usage<plan.bytes*1.2)throw Error('保存する空き容量が不足しています。端末の空き容量を増やしてください');
   await navigator.storage?.persist?.();
   const worker=async()=>{while(next<plan.files.length){signal.throwIfAborted();const file=plan.files[next++];let response=file.sha256?await cache.match(file.url):null;let data;
    if(!response&&file.sha256)for(const p of previous){response=await p.cache.match(file.url);if(response)break;}
    if(response){try{const copy=response.clone();data=await verifiedBytes(response,file.sha256);await cache.put(file.url,copy);}catch{await cache.delete(file.url);response=null;}}
    if(!response){response=await fetch(file.url,{signal,cache:'no-store'});const type=response.headers.get('content-type')||'application/octet-stream';data=await verifiedBytes(response,file.sha256);signal.throwIfAborted();await cache.put(file.url,new Response(data,{headers:{'Content-Type':type}}));}
    bytes+=data.byteLength;count++;this.onProgress({count,total:plan.files.length,bytes});
   }};
   await Promise.all([worker(),worker()]);signal.throwIfAborted();
   const completed={name:plan.name,bytes,files:count,urls:plan.files.map(f=>f.url),savedAt:Date.now()};await cache.put('/__city_complete__',Response.json(completed));for(const p of previous)await caches.delete(p.name);if(plan.files.some(f=>f.url===CITY_DATA+plan.code+'/city.json'))await refreshAddressPackage(plan.code);return completed;
  }catch(e){controller.abort();throw e;}finally{this.controller=null;}
 }
}
export function attachCityOffline({store,getGroup,host}){
 const panel=document.createElement('section');panel.className='city-offline';panel.innerHTML='<h3>地図を端末に保存</h3><p data-city-state role="status"></p><button type="button" data-city-download>地図を保存</button><button type="button" class="secondary" data-city-pause hidden>一時停止</button>';
 host.append(panel);const state=panel.querySelector('[data-city-state]'),button=panel.querySelector('[data-city-download]'),pause=panel.querySelector('[data-city-pause]');let busy=false,code=null,plan=null,pendingRefresh=false,attempted=null;
 const downloader=new CityDownload(p=>{state.textContent=`${t('保存中')} ${p.count} / ${p.total} · ${(p.bytes/1048576).toFixed(1)} MB`;});
 async function refresh(){
  if(busy){pendingRefresh=true;return;}pendingRefresh=false;
  const group=getGroup(),city=await mapMunicipality(store,group),venue=await store.read('meta','venue:'+group);
  if(group!==getGroup())return;
  if(code!==city?.code){code=city?.code;plan=null;attempted=null;}
  button.disabled=!code;if(!code){state.textContent=t('グループの活動地域を管理者が設定してください');return;}
  const current=code,existing=await downloader.status(current),saved=current==='13103'&&existing&&!existing.urls.includes(CITY_DATA+'13103/risk/index.json')?null:existing;if(current!==code||group!==getGroup())return;
  state.textContent=saved?`${city.name}${t('全域')} · ${t('保存済み・圏外で利用可能')}（${(saved.bytes/1048576).toFixed(1)} MB）`:`${city.name}${t('全域')} · ${t('約')}${code==='26100'?'219':code==='13103'?'18':'8'} MB`;
  button.textContent=saved?t('地図を保存し直す'):t('地図を保存');
  if(!saved&&demoMapMunicipality(venue)&&attempted!==code){attempted=code;await download();}
 }
 async function download(){
  if(busy||!code)return;const target=code;busy=true;button.disabled=true;pause.hidden=false;pause.disabled=true;
  try {state.textContent=t('保存の準備中…');plan=await cityDownloadPlan(target);if(target!==code||pendingRefresh)return;pause.disabled=false;const saved=await downloader.download(plan);state.textContent=`${plan.name}${t('全域')} · ${t('保存済み・圏外で利用可能')}（${(saved.bytes/1048576).toFixed(1)} MB）`;button.textContent=t('地図を保存し直す');}
  catch(e){state.textContent=e.name==='AbortError'?t('保存を一時停止しました。再開できます。'):e.name==='QuotaExceededError'?t('端末の空き容量が不足しています。保存は未完了です。'):t(e.message);button.textContent=t('保存を再開');}
  finally{busy=false;button.disabled=false;pause.hidden=true;if(pendingRefresh)await refresh();}
 }
 button.onclick=download;pause.onclick=()=>downloader.pause();
 for(const event of ['municipality-change','group-change','venue-map-change'])window.addEventListener(event,()=>{(async()=>{const target=await mapMunicipality(store,getGroup());if(busy&&target?.code===code)return;if(busy){attempted=null;downloader.pause();}await refresh();})().catch(e=>{state.textContent=t(e.message);});});
 window.addEventListener('online',()=>{attempted=null;refresh().catch(e=>{state.textContent=t(e.message);});});
 bindStaticUiLanguage(panel);observeUiLanguage(()=>{if(!busy)refresh().catch(e=>{state.textContent=t(e.message);});});refresh().catch(e=>{state.textContent=t(e.message);});return {refresh};
}
