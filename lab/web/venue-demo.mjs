import {latestDemoHome,validateDemoHome} from './device-records.mjs';
import {normalizePlace} from './places.mjs';
// Session-scoped demo homes never overwrite real resident/home or GPS records.
export function venueWithHomes(venue,records=[]){return !venue||venue.positionMode==='ACTUAL'?venue:{...venue,placements:venue.placements.map(p=>{const home=latestDemoHome(records,venue.groupId,venue.sessionId,p.deviceId)||p.home;return home?{...p,home,latitude:home.latitude,longitude:home.longitude}:p;})};}
export function venuePlaces(venue){
 if(!venue||venue.positionMode==='ACTUAL')return [];
 const rows=[],seen=new Set();
 for(const p of venue.placements){const h=p.home;if(!h)continue;const base={groupId:venue.groupId,authorId:p.deviceId,authorName:h.displayName||p.name,createdAt:venue.startedAt||0};
  if(!seen.has(h.buildingId)){seen.add(h.buildingId);rows.push(normalizePlace({...base,eventId:h.buildingId,sequence:1,kind:'BUILDING',buildingId:h.buildingId,label:h.address.slice(0,80),latitude:h.latitude,longitude:h.longitude,locationReference:{address:h.address,plateauId:h.plateauId,dataset:h.plateauId?'plateau-26100-kyoto-shi-2025':null}}));}
  rows.push(normalizePlace({...base,eventId:p.deviceId,sequence:2,kind:'HOME',buildingId:h.buildingId}));
 }return rows;
}
export function demoHomeFromPoint(point,old={},details={}){
 const same=Math.abs(point.latitude-old.latitude)<1e-6&&Math.abs(point.longitude-old.longitude)<1e-6;
 const plateauId=point.plateauId??(same?old.plateauId:null)??null;
 const nativeId=plateauId?.replace(/^bldg_/,'');
 const buildingId=/^[0-9a-f-]{36}$/i.test(nativeId||'')?nativeId:same&&old.buildingId?old.buildingId:crypto.randomUUID();
 return validateDemoHome({buildingId,latitude:point.latitude,longitude:point.longitude,plateauId,address:details.address?.trim()||(same?old.address:null)||'地図で選択した住居',floor:details.floor??(same?old.floor:'')??'',room:details.room??(same?old.room:'')??'',...(point.searchReference?{searchReference:point.searchReference}:same&&old.searchReference?{searchReference:old.searchReference}:{}),...(details.displayName?{displayName:details.displayName}:old.displayName?{displayName:old.displayName}:{})});
}
export function activeVenue(venue,group,incident){return venue?.version===1&&venue.mode==='VENUE_DEMO'&&venue.groupId===group&&venue.incidentId===incident&&!venue.closed?venue:null;}
export function venueResidents(venue){return venue.placements.map(p=>({groupId:venue.groupId,originId:p.deviceId,name:p.home?.displayName||p.name,latitude:p.latitude,longitude:p.longitude,floor:p.home?.floor||'',room:p.home?.room||'',homeAddress:p.home?.address||'',revision:1,updatedAt:0,safety:'UNREPORTED',venueSessionId:venue.sessionId}));}
export function venuePositions(venue,measured=[]){return venue.positionMode==='ACTUAL'?measured.filter(p=>p.groupId===venue.groupId&&p.acquiredAt>=(venue.startedAt??0)&&venue.placements.some(m=>m.deviceId===p.originId)):venue.placements.map(p=>({groupId:venue.groupId,originId:p.deviceId,latitude:p.latitude,longitude:p.longitude,source:'DEMO_ASSIGNED',acquiredAt:0,accuracyMeters:0}));}
export async function venueAuthor(store,group,incident,device){const venue=activeVenue(await store.read('meta','venue:'+group),group,incident);return venue?.placements.find(p=>p.deviceId===device)?.name;}
export async function saveVenue(store,body){
 if(!Object.hasOwn(body,'venue'))return;
 const venue=body.venue;
 if(venue){
  if(venue.version!==1||venue.mode!=='VENUE_DEMO'||venue.groupId!==body.groupId||venue.sessionId!==venue.incidentId||!Array.isArray(venue.placements)||venue.placements.length>64)throw Error('会場デモの情報が不正です');
  if(venue.positionMode!==undefined&&!['KYOTO','ACTUAL'].includes(venue.positionMode))throw Error('位置の表示方式を確認できません');
  for(const p of venue.placements)if(p.sessionId!==venue.sessionId||!body.roster.members.some(m=>m.memberId===p.memberId&&m.deviceIds.includes(p.deviceId))||(venue.positionMode==='ACTUAL'?(p.source!=='DEVICE_LOCATION'||p.latitude!==null||p.longitude!==null):(p.source!=='DEMO_ASSIGNED'||!Number.isFinite(p.latitude)||Math.abs(p.latitude)>85||!Number.isFinite(p.longitude)||Math.abs(p.longitude)>180)))throw Error('会場デモの配置を確認できません');
 }
 if(venue&&!venue.closed&&(await store.read?.('meta','venue:'+body.groupId))?.sessionId!==venue.sessionId)await store.update('meta','venue-onboarding:'+body.groupId,()=>venue.sessionId);
 await store.update('meta','venue:'+body.groupId,()=>venue);
}
