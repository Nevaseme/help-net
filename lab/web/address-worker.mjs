import {localGeocodeCandidates,loadCityPackage} from './city-addresses.mjs';
self.onmessage=async({data})=>{try{if(data.kind==='refresh'){await loadCityPackage(data.code,true);self.postMessage({id:data.id,rows:[]});return;}self.postMessage({id:data.id,rows:await localGeocodeCandidates(data.query,data.code)});}catch(e){self.postMessage({id:data.id,error:e.message});}};
