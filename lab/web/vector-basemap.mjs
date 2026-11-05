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
 layer.addTo(map);return layer;
}
