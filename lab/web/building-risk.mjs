import {CITY_DATA,verifiedBytes} from './city-addresses.mjs';
export function riskPercent(model,profile,intensity=6.2){
 if(!Number.isFinite(intensity)||intensity<5||intensity>7)throw Error('invalid intensity');
 const curve=model.curves[profile]?.full;if(!curve)return -1;
 const raw=(intensity-5)*10,nearest=Math.round(raw),p=Math.abs(raw-nearest)<1e-10?nearest:raw,lo=Math.min(19,Math.floor(p)),f=p-lo;
 return curve[lo]*(1-f)+curve[lo+1]*f;
}
export const riskExpression=(model,intensity=6.2)=>['match',['get','p'],...Object.keys(model.curves).flatMap(key=>[key,riskPercent(model,key,intensity)]),-1];
const resources=new Map();let registered=false;
async function load(code){
 if(!resources.has(code))resources.set(code,(async()=>{const r=await fetch(CITY_DATA+code+'/risk/index.json');if(!r.ok)throw Error('建物データを取得できません');const index=await r.json();const data=await verifiedBytes(await fetch(CITY_DATA+code+'/buildings/model.json'),index.modelSha256);return {index,model:JSON.parse(new TextDecoder().decode(data)),tiles:new Map(index.tiles.map(t=>[t.file.replace('.pbf.gz',''),t]))};})().catch(e=>{resources.delete(code);throw e;}));return resources.get(code);
}
export async function installBuildingRisk(map){
 const lib=await import('./maplibre-gl.mjs');
 if(!registered){lib.addProtocol('pland-risk',async(request,controller)=>{const [code,...path]=request.url.replace('pland-risk://','').split('/'),{tiles}=await load(code),tile=tiles.get(path.join('/'));if(!tile)return {data:new ArrayBuffer(0)};const response=await fetch(CITY_DATA+code+'/risk/'+tile.file,{signal:controller.signal});const bytes=await verifiedBytes(response,tile.sha256);const data=await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();return {data};});registered=true;}
 const results=await Promise.allSettled([['26100','kyoto-risk'],['13103','toranomon-risk']].map(async([code,source])=>{
  const {index,model}=await load(code);
  const install=()=>{
   if(map.getSource(source))return;
   map.addSource(source,{type:'vector',tiles:[`pland-risk://${code}/{z}/{x}/{y}`],minzoom:index.minzoom,maxzoom:index.maxzoom,bounds:index.bounds});
   const rate=riskExpression(model),color=['case',['<',rate,0],'#D4D7DC',['<',rate,10],'#FBE8C5',['<',rate,40],'#F2AE5D','#C5672E'];
   const before=map.getStyle().layers.find(l=>l.type==='symbol')?.id;
   for(const [suffix,sourceLayer,minzoom,maxzoom] of [['overview','footprints',8,14],['buildings','buildings',14,24]])map.addLayer({id:source+'-'+suffix,type:'fill',source,'source-layer':sourceLayer,minzoom,maxzoom,paint:{'fill-color':color,'fill-opacity':.72,...(minzoom===14?{'fill-outline-color':'#B5A696'}:{})}},before);
   map.getContainer().dataset.buildingRisk='ready';map.getContainer().dataset[source==='kyoto-risk'?'kyotoRisk':'toranomonRisk']='ready';
  };
  await installWhenMapReady(map,install);
 }));
 if(results.every(r=>r.status==='rejected'))throw results[0].reason;
}

export function installWhenMapReady(map,install){return new Promise(resolve=>{const apply=()=>{install();resolve();};if(map.isStyleLoaded())apply();else map.once('load',apply);});}
