import {CITY_DATA,verifiedBytes} from './city-addresses.mjs';
export function riskPercent(model,profile,intensity=6.2){
 if(!Number.isFinite(intensity)||intensity<5||intensity>7)throw Error('invalid intensity');
 const curve=model.curves[profile]?.full;if(!curve)return -1;
 const raw=(intensity-5)*10,nearest=Math.round(raw),p=Math.abs(raw-nearest)<1e-10?nearest:raw,lo=Math.min(19,Math.floor(p)),f=p-lo;
 return curve[lo]*(1-f)+curve[lo+1]*f;
}
export const riskExpression=(model,intensity=6.2)=>['match',['get','p'],...Object.keys(model.curves).flatMap(key=>[key,riskPercent(model,key,intensity)]),-1];
let resources,registered=false;
async function load(){
 if(!resources)resources=(async()=>{const r=await fetch(CITY_DATA+'26100/risk/index.json');if(!r.ok)throw Error('建物データを取得できません');const index=await r.json();const data=await verifiedBytes(await fetch(CITY_DATA+'26100/buildings/model.json'),index.modelSha256);return {index,model:JSON.parse(new TextDecoder().decode(data)),tiles:new Map(index.tiles.map(t=>[t.file.replace('.pbf.gz',''),t]))};})().catch(e=>{resources=null;throw e;});return resources;
}
export async function installBuildingRisk(map){
 const [{index,model,tiles},lib]=await Promise.all([load(),import('./maplibre-gl.mjs')]);
 if(!registered){lib.addProtocol('pland-risk',async(request,controller)=>{const key=request.url.replace('pland-risk://',''),tile=tiles.get(key);if(!tile)return {data:new ArrayBuffer(0)};const response=await fetch(CITY_DATA+'26100/risk/'+tile.file,{signal:controller.signal});const bytes=await verifiedBytes(response,tile.sha256);const data=await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();return {data};});registered=true;}
 const install=()=>{
  if(map.getSource('kyoto-risk'))return;
  map.addSource('kyoto-risk',{type:'vector',tiles:['pland-risk://{z}/{x}/{y}'],minzoom:index.minzoom,maxzoom:index.maxzoom,bounds:index.bounds});
  const rate=riskExpression(model),color=['case',['<',rate,0],'#D4D7DC',['<',rate,10],'#FBE8C5',['<',rate,40],'#F2AE5D','#C5672E'];
  const before=map.getStyle().layers.find(l=>l.type==='symbol')?.id;
  for(const [id,sourceLayer,minzoom,maxzoom] of [['kyoto-risk-overview','footprints',8,14],['kyoto-risk-buildings','buildings',14,24]])map.addLayer({id,type:'fill',source:'kyoto-risk','source-layer':sourceLayer,minzoom,maxzoom,paint:{'fill-color':color,'fill-opacity':.72,...(minzoom===14?{'fill-outline-color':'#B5A696'}:{})}},before);
  map.getContainer().dataset.buildingRisk='ready';
 };
 if(map.isStyleLoaded())install();else map.once('load',install);
}
