import {CITY_DATA,cityFetch,verifiedBytes,gunzipJson} from './city-addresses.mjs';

// Land parcel correspondence is a candidate address, never a verified postal address.
export function parcelAddressChoices(link,source){
 if(!link||!Array.isArray(link.candidates))return [];
 return [...new Map(link.candidates.filter(c=>c.numbered!==false&&typeof c.address==='string'&&c.address.trim()&&typeof c.parcelId==='string').map(c=>[c.address,{...c,status:link.status,source}])).values()];
}
export class ParcelAddressRepository {
 constructor(fetcher=url=>cityFetch(url,'26100'),base=CITY_DATA+'26100/parcel-addresses/'){this.fetcher=fetcher;this.base=base;this.index=null;this.chunks=new Map();}
 async choices(buildingId,latitude,longitude,code='26100'){
  if(code!=='26100'||!buildingId)return [];
  if(!this.index){const response=await this.fetcher(this.base+'index.json');if(!response.ok)throw Error('住所候補を取得できません');const index=await response.json();if(index.schemaVersion!==1||!Array.isArray(index.chunks))throw Error('住所候補のデータが不正です');this.index=index;}
  for(const chunk of this.index.chunks){const b=chunk.bbox;if(!Array.isArray(b)||longitude<b[0]||latitude<b[1]||longitude>b[2]||latitude>b[3])continue;
   let body=this.chunks.get(chunk.mesh);
   if(!body){body=await gunzipJson(await verifiedBytes(await this.fetcher(this.base+chunk.file),chunk.sha256));if(body.schemaVersion!==1||body.mesh!==chunk.mesh||!body.buildings)throw Error('住所候補のデータが不正です');if(this.chunks.size>=2)this.chunks.delete(this.chunks.keys().next().value);this.chunks.set(chunk.mesh,body);}
   if(body.buildings[buildingId])return parcelAddressChoices(body.buildings[buildingId],this.index.source);
  }
  return [];
 }
}
export const parcelAddresses=new ParcelAddressRepository();
