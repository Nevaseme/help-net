import {addressRows,addressForms,queryForms,CITY_DATA,cityFetch,verifiedBytes,gunzipJson} from './city-addresses.mjs';

// The town index is small. Parcel rows are loaded only for an exact town + number.
// Both town and number must match; nearby numbers never stand in for missing parcels.
export class ParcelSearch {
 constructor(loadIndex,loadChunk){this.loadIndex=loadIndex;this.loadChunk=loadChunk;this.chunks=new Map();}
 async prepare(){
  if(!this.pending)this.pending=this.loadIndex().then(index=>{
   if(index.schemaVersion!==1)throw Error('Unsupported parcel search index');
   this.index=index;this.towns=new Map();
   for(const [town,id] of index.towns)for(const form of addressForms(town)){
    const ids=this.towns.get(form)||new Set();ids.add(id);this.towns.set(form,ids);
   }
   this.files=new Map(index.chunks.map(c=>[c.id,c]));
  }).catch(e=>{this.pending=null;throw e;});
  return this.pending;
 }
 async search(query){
  const queries=queryForms(query);if(!queries.some(q=>/[0-9]/.test(q)))return [];
  await this.prepare();const ids=new Set();
  for(const q of queries)for(let i=1;i<q.length;i++){
   for(const id of this.towns.get(q.slice(0,i))||[])ids.add(id);
  }
  const hits=[];
  for(const id of ids){
   let pending=this.chunks.get(id);
   if(pending){this.chunks.delete(id);this.chunks.set(id,pending);}
   else {
    pending=this.loadChunk(this.files.get(id)).then(body=>{
     if(body.schemaVersion!==1)throw Error('Unsupported parcel search chunk');
     const exact=new Map();
     for(const r of addressRows(body.rows))for(const f of r.forms){const found=exact.get(f)||[];found.push(r);exact.set(f,found);}
     return exact;
    }).catch(e=>{this.chunks.delete(id);throw e;});
    this.chunks.set(id,pending);if(this.chunks.size>4)this.chunks.delete(this.chunks.keys().next().value);
   }
   const exact=await pending;
   for(const q of queries)hits.push(...(exact.get(q)||[]));
  }
  return [...new Map(hits.map(r=>[r.id,r])).values()].sort((a,b)=>a.address.localeCompare(b.address,'ja')||a.id.localeCompare(b.id)).slice(0,12);
 }
}

const repositories=new Map();
export function clearParcelSearch(code){for(const url of repositories.keys())if(url.includes(`/${code}/`))repositories.delete(url);}
export async function searchParcelAddresses(query,city){
 if(!city?.parcelSearch)return [];
 const file=city.parcelSearch,url=CITY_DATA+file.path;
 let repository=repositories.get(url);
 if(!repository){
  repository=new ParcelSearch(async()=>JSON.parse(new TextDecoder().decode(await verifiedBytes(await cityFetch(url,'26100'),file.sha256))),
   async chunk=>gunzipJson(await verifiedBytes(await cityFetch(new URL(chunk.file,url).href,'26100'),chunk.sha256)));
  repositories.set(url,repository);
 }
 return repository.search(query);
}

export function mergeParcelCandidates(existing,parcels){
 if(!parcels.length)return existing;
 // An official residential/building point keeps priority over a land interior point.
 const precise=existing.filter(r=>!['town','block','area','parcel-area'].includes(r.precision));
 const qualified=r=>r.search.replace(/^京都府/,'').replace(/^京都市/,'');
 const trusted=new Set(precise.map(qualified));
 return precise.concat(parcels.filter(r=>!trusted.has(qualified(r)))).slice(0,12);
}
