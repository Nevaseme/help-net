import {CITY_DATA} from './city-addresses.mjs';
import {mapResourceUrl} from './city-offline.mjs';
import * as maplibre from './maplibre-gl.mjs';
globalThis.maplibregl=maplibre;
maplibre.setWorkerUrl('/maplibre-gl-worker.mjs');
maplibre.setWorkerCount(1);
let loaded;
export async function addVectorBasemap(map){
 loaded??=import('./leaflet-maplibre-gl.js');await loaded;
 const layer=globalThis.MaplibreGLLeaflet.maplibreGL({style:CITY_DATA+'map-style.json',interactive:false,localIdeographFontFamily:'sans-serif',attribution:'© OpenFreeMap · OpenMapTiles · OpenStreetMap',transformRequest:url=>({url:mapResourceUrl(url)})});
 layer.addTo(map);
 const vector=layer.getMaplibreMap(),original=new Map();
 const applyLanguage=()=>{if(!vector.isStyleLoaded())return;for(const item of vector.getStyle().layers){const field=item.layout?.['text-field'];if(item.type!=='symbol'||!JSON.stringify(field)?.includes('name'))continue;if(!original.has(item.id))original.set(item.id,field);vector.setLayoutProperty(item.id,'text-field',document.documentElement.lang==='en'?['coalesce',['get','name:en'],['get','name_en'],['get','name:latin'],'']:original.get(item.id));}};
 vector.on('load',applyLanguage);const languageObserver=new MutationObserver(applyLanguage);languageObserver.observe(document.documentElement,{attributes:true,attributeFilter:['lang']});map.on('unload',()=>languageObserver.disconnect());
 return layer;
}
