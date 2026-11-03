import {placeBuildings,homeFor} from './places.mjs';
/** Overview tiles are aggregated geometry and must never be treated as individual buildings. */
export function mapBuildingFeatures(features){
 return [...new Map(features.filter(f=>['kyoto-risk','toranomon-risk'].includes(f.source)&&f.sourceLayer==='buildings'&&typeof f.properties?.bid==='string').map(f=>{
  const p=f.properties;return [p.bid,{id:p.bid,code:f.source==='kyoto-risk'?'26100':'13103',attributes:{year:Number(p.year)||null,floors:Number(p.floors)||null,structureCode:p.structure||null}}];
 })).values()];
}
export function linkedBuildingResidents(building,rows,people){
 const ids=new Set(placeBuildings(rows).filter(b=>b.buildingId===building.registeredId||b.locationReference?.plateauId===building.id).map(b=>b.buildingId));
 return people.filter(p=>ids.has(homeFor(rows,p.originId)?.buildingId));
}
export function registeredBuildingForPerson(rows,id){
 const home=homeFor(rows,id);return placeBuildings(rows).find(b=>b.buildingId===home?.buildingId)||null;
}
export async function buildingAtRegisteredPlace(place){
 const id=place.locationReference?.plateauId;
 if(id){const {buildingsAt}=await import('./building-picker.mjs');const rows=await buildingsAt(place.latitude,place.longitude,place.longitude>139?'13103':'26100');const found=rows.find(b=>b.id===id);if(found)return {...found,registeredId:place.buildingId,label:place.label};}
 return {id:id||place.buildingId,registeredId:place.buildingId,label:place.label,attributes:{}};
}
