import {searchParcelAddresses,mergeParcelCandidates,clearParcelSearch} from './parcel-search.mjs';
export const CITY_DATA='https://nevaseme.github.io/help-net/data/cities/';
// Read existing offline files after the repository rename; never fetch the old URL.
export const legacyCityUrl=url=>String(url).replace(CITY_DATA,'https://nevaseme.github.io/safety_confirm/data/cities/');
export const cachedCityFile=async(cache,url)=>(await cache?.match(url))||(await cache?.match(legacyCityUrl(url)));
export const CITY_CODES=['26100','22205','13103'];
const numerals={'〇':0,'零':0,'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'七':7,'八':8,'九':9};
function chome(s){if(/^\d+$/.test(s))return s;let value=0,digit=0;for(const c of s){if(c==='十'){value+=(digit||1)*10;digit=0;}else digit=numerals[c]??0;}return String(value+digit);}
export function normalizeAddress(value){return value.normalize('NFKC').toLowerCase().replace(/[\s　]/g,'').replace(/[‐‑–—−]/g,'-').replace(/(\d)ー(?=\d)/g,'$1-').replace(/([〇零一二三四五六七八九十0-9]+)丁目/g,(_,n)=>chome(n)+'-').replace(/(\d)(?:番地の|番地|番)/g,'$1-').replace(/(\d)号/g,'$1').replace(/-$/,'');}
export function addressForms(s){const city=s.replace(/^(京都府|静岡県|東京都)/,''),ward=city.replace(/^(京都市|熱海市)/,''),town=ward.replace(/^[^区]+区/,'');return [...new Set([s,city,ward,town])];}
export function queryForms(input){const q=normalizeAddress(input);const withoutStreet=q.replace(/^((?:京都府)?京都市[^区]+区|(?:左京|右京|上京|中京|下京|北|南|東山|山科|伏見|西京)区).+?通.*?(?:上[るル]|下[るル]|東入[るル]?|西入[るル]?)/,'$1');return [...new Set([q,withoutStreet])];}
const prepared=new WeakMap();
export function addressRows(input){if(input[0]?.forms)return input;if(prepared.has(input))return prepared.get(input);const rows=input.map(v=>{if(v.forms)return v;const r=Array.isArray(v)?{id:v[0],address:v[1],longitude:v[2],latitude:v[3],precision:v[4],...(v[5]||{})}:{...v};r.matchLevel??=r.precision;r.search=Array.isArray(v)&&v[6]?v[6]:normalizeAddress(r.address);r.forms=addressForms(r.search);r.municipalityCode??=r.address.startsWith('東京都港区')?'13103':r.address.startsWith('静岡県熱海市')?'22205':r.address.startsWith('京都府京都市')?'26100':null;return r;});prepared.set(input,rows);return rows;}
function townFallback(rows,queries){const matches=[];let longest=0;for(const r of rows){if(r.precision!=='town')continue;for(const q of queries)for(const s of r.forms)if(q.startsWith(s)&&/^[-0-9]/.test(q.slice(s.length))){if(s.length>longest){matches.length=0;longest=s.length;}if(s.length===longest)matches.push({...r,matchLevel:'town',approximate:true});}}return matches;}
export function searchAddresses(input,query,municipalityCode=null){const qs=queryForms(query);if(qs[0].length<2)return [];const rows=addressRows(input),explicit=/^(京都府|静岡県|東京都|京都市|熱海市|港区)/.test(qs[0]);let matches=[];
 for(const r of rows){let rank=Infinity;for(const q of qs){if(r.forms.includes(q))rank=Math.min(rank,r.search===q?0:1);else if(r.search.includes(q)&&!/[0-9]$/.test(q))rank=Math.min(rank,2);}if(Number.isFinite(rank))matches.push({r,rank});}
 if(!matches.length)matches=townFallback(rows,qs).map(r=>({r,rank:3}));
 matches.sort((a,b)=>a.rank-b.rank||((!explicit&&municipalityCode)?Number(b.r.municipalityCode===municipalityCode)-Number(a.r.municipalityCode===municipalityCode):0)||a.r.address.length-b.r.address.length||a.r.address.localeCompare(b.r.address,'ja'));
 return [...new Map(matches.map(({r})=>[`${r.address}:${r.latitude}:${r.longitude}`,r])).values()].slice(0,12);
}
export function resolveAddress(input,query,municipalityCode=null,hits=searchAddresses(input,query,municipalityCode)){const rows=addressRows(input),q=normalizeAddress(query);if(q.length<2)return null;if(hits.length===1)return hits[0];if(/[市区]$/.test(q)){const area=rows.filter(r=>r.precision==='town'&&r.search.includes(q));if(area.length)return {...area[0],id:'area:'+q,address:query,precision:'area',matchLevel:'area',approximate:true,latitude:(Math.min(...area.map(r=>r.latitude))+Math.max(...area.map(r=>r.latitude)))/2,longitude:(Math.min(...area.map(r=>r.longitude))+Math.max(...area.map(r=>r.longitude)))/2};}return null;}
export async function verifiedBytes(response,sha){if(!response.ok)throw Error('地域データを取得できません。通信があるときに再試行してください');const bytes=await response.arrayBuffer();if(sha){const digest=[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(x=>x.toString(16).padStart(2,'0')).join('');if(digest!==sha)throw Error('地域データが更新されています。地図を保存し直してください');}return bytes;}
export async function gunzipJson(bytes){return JSON.parse(await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'))).text());}
export async function cityFetch(url,code){
 try{const response=await fetch(url,{cache:'reload'});if(response.ok)return response;throw Error('地域データを取得できません');}catch(error){
  if(globalThis.caches)for(const name of (await caches.keys()).reverse()){if(!name.startsWith('tasukeai-city-v2-'+code+'-')&&name!=='tasukeai-city-v1-'+code)continue;const cache=await caches.open(name);if(!await cache.match('/__city_complete__'))continue;const saved=await cachedCityFile(cache,url);if(saved)return saved;}throw error;
 }
}
const packages=new Map();
export async function loadCityPackage(code,refresh=false){
 if(!CITY_CODES.includes(code))throw Error('対応地域を選んでください');
 if(!refresh&&packages.has(code))return packages.get(code);
 const pending=(async()=>{const cache=globalThis.caches?await caches.open('tasukeai-address-v2'):null,key=CITY_DATA+code+'/search-package-v3';const previous=await cachedCityFile(cache,key),legacy=await cachedCityFile(cache,CITY_DATA+code+'/search-package-v2');
 if(previous&&!refresh)return previous.json();
 try{const r=await cityFetch(CITY_DATA+code+'/city.json',code);if(!r.ok)throw Error('地域データを取得できません');const city=await r.json();
 const read=async file=>file?gunzipJson(await verifiedBytes(await cityFetch(CITY_DATA+file.path,code),file.sha256)):({rows:[]});
 const [addresses,places]=await Promise.all([read(city.addresses),read(city.places)]);const value={city,addresses,places};
 await cache?.put(key,Response.json(value));return value;
 }catch(error){if(previous||legacy)return (previous||legacy).json();throw error;}
 })().catch(e=>{packages.delete(code);throw e;});packages.set(code,pending);return pending;
}
export async function cityAddresses(code){const {addresses}=await loadCityPackage(code);return addressRows(addresses.rows);}
export async function cityLocations(code){const p=await loadCityPackage(code);if(!p.rows)p.rows=(p.addresses.rows=addressRows(p.addresses.rows)).concat(p.places.rows=addressRows(p.places.rows).map(r=>({...r,municipalityCode:code,source:r.source||'OSM/OpenFreeMap'})));return p.rows;}
export async function searchCityLocations(query,includePlaces=true,code=null){if(includePlaces)return geocodeCandidates(query,code);const explicit=regionForQuery(query);const loaded=await Promise.allSettled((explicit||code?[explicit||code]:CITY_CODES).map(cityAddresses));let hits=searchAddresses(loaded.filter(r=>r.status==='fulfilled').flatMap(r=>r.value),query,code);if(!explicit||explicit==='26100'){hits=await withParcelCandidates(hits,query);}return hits;}
let searchWorker,workerId=0;const requests=new Map();
export function geocodeCandidates(query,code=null){if(typeof window==='undefined'||typeof Worker==='undefined')return localGeocodeCandidates(query,code);return workerRequest({query,code});}
export function refreshAddressPackage(code){packages.delete(code);clearParcelSearch(code);return typeof window==='undefined'||typeof Worker==='undefined'?loadCityPackage(code,true):workerRequest({kind:'refresh',code});}
function workerRequest(payload){
 if(!searchWorker){searchWorker=new Worker(new URL('./address-worker.mjs',import.meta.url),{type:'module'});searchWorker.onmessage=({data})=>{const p=requests.get(data.id);if(!p)return;requests.delete(data.id);data.error?p.reject(Error(data.error)):p.resolve(data.rows);};searchWorker.onerror=()=>{for(const p of requests.values())p.reject(Error('住所検索を開始できません。画面を開き直してください'));requests.clear();searchWorker.terminate();searchWorker=null;};}
 return new Promise((resolve,reject)=>{const id=++workerId;requests.set(id,{resolve,reject});searchWorker.postMessage({id,...payload});});
}
async function withParcelCandidates(hits,query){
 // Partial downloads or offline upgrades keep the original, honestly labelled precision.
 try{const kyoto=await loadCityPackage('26100');return mergeParcelCandidates(hits,await searchParcelAddresses(query,kyoto.city));}catch{return hits;}
}
export async function localGeocodeCandidates(query,code=null){
 const explicit=regionForQuery(query),target=explicit||code,facility=!/[0-9０-９]/.test(query)&&query.trim().length>=2;
 const targets=explicit?[explicit]:facility||!target?CITY_CODES:[target];
 let loaded=await Promise.allSettled(targets.map(cityLocations)),rows=loaded.filter(r=>r.status==='fulfilled').flatMap(r=>r.value);
 let hits=searchAddresses(rows,query,target);const area=/[市区]$/.test(query.trim())?resolveAddress(rows,query,target,hits):null;if(area?.precision==='area')hits=[area];
 if(!explicit&&!facility&&target&&!hits.length){loaded=await Promise.allSettled(CITY_CODES.filter(c=>c!==target).map(cityLocations));rows=rows.concat(loaded.filter(r=>r.status==='fulfilled').flatMap(r=>r.value));hits=searchAddresses(rows,query,target);}
 if(!rows.length)throw Error('地域データを取得できません。通信があるときに再試行してください');
 if(!explicit||explicit==='26100'){hits=await withParcelCandidates(hits,query);}return hits;
}
export function regionForQuery(query){if(/東京都|港区|六本木/.test(query))return '13103';if(/静岡県|熱海市/.test(query))return '22205';if(/京都府|京都市|左京区|右京区|中京区|上京区|下京区|北区|南区|東山区|山科区|伏見区|西京区/.test(query))return '26100';return null;}
export async function geocodeAddress(query,code=null){const candidates=await geocodeCandidates(query,code);return candidates.length===1?candidates[0]:null;}
