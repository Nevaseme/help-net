import {latestDemoHome,validateDemoHome} from './device-records.mjs';
import {normalizePlace} from './places.mjs';
import {demoObservationTimes} from './venue-observations.mjs';
// Session-scoped drill homes never overwrite real resident/home or GPS records.
export function venueWithHomes(venue,records=[]){if(venue?.scenario)venue={...venue,scenario:{...venue.scenario,communities:venue.scenario.communities.map(c=>({...c,nameJa:c.nameJa.replace(/（(?:デモ|訓練)）$/,''),nameEn:c.nameEn.replace(/ \((?:demo|drill)\)$/i,'')}))}};return !venue||venue.positionMode==='ACTUAL'?venue:{...venue,placements:venue.placements.map(p=>{const home=latestDemoHome(records,venue.groupId,venue.sessionId,p.deviceId)||p.home;return home?{...p,home}:p;})};}
export const venuePlacements=venue=>[...(venue?.placements||[]),...(venue?.scenario?.people||[]).map((p,i)=>({...p,sessionId:venue.sessionId,memberId:p.deviceId,slotId:64+i,latitude:p.positionUnknown?null:p.position?.latitude??p.home.latitude,longitude:p.positionUnknown?null:p.position?.longitude??p.home.longitude,source:'SCENARIO'}))];
export function scenarioAnswers(venue){return (venue?.scenario?.people||[]).filter(p=>['SAFE','SOS'].includes(p.safety)).map(p=>({groupId:venue.groupId,eventId:p.deviceId,incidentId:venue.incidentId,authorId:p.deviceId,authorName:p.name,sequence:1,createdAt:venue.startedAt,kind:'ANSWER',safety:p.safety}));}
export function venuePlaces(venue){
 if(!venue)return [];
 const rows=[],seen=new Set();
 for(const p of venuePlacements(venue).filter(p=>venue.positionMode!=='ACTUAL'||p.source==='SCENARIO')){const h=p.home;if(!h)continue;const base={groupId:venue.groupId,authorId:p.deviceId,authorName:h.displayName||p.name,createdAt:venue.startedAt||0};
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
export function venueResidents(venue){return venuePlacements(venue).map(p=>({groupId:venue.groupId,originId:p.deviceId,name:p.home?.displayName||p.name,latitude:p.latitude,longitude:p.longitude,floor:p.home?.floor||'',room:p.home?.room||'',homeAddress:p.home?.address||'',homeLatitude:p.home?.latitude,homeLongitude:p.home?.longitude,revision:1,updatedAt:0,safety:'UNREPORTED',venueSessionId:venue.sessionId,...(p.source==='SCENARIO'?{fictional:true,...demoObservationTimes(p,venue.scenario.people.findIndex(x=>x.deviceId===p.deviceId),venue.startedAt)}:{})}));}
export function venuePositions(venue,measured=[]){const assigned=venuePlacements(venue).filter(p=>Number.isFinite(p.latitude)&&Number.isFinite(p.longitude)&&(venue.positionMode!=='ACTUAL'||p.source==='SCENARIO')).map(p=>({groupId:venue.groupId,originId:p.deviceId,latitude:p.latitude,longitude:p.longitude,source:'DEMO_ASSIGNED',acquiredAt:venue.startedAt,accuracyMeters:0}));return venue.positionMode==='ACTUAL'?[...measured.filter(p=>p.groupId===venue.groupId&&p.acquiredAt>=(venue.startedAt??0)&&venue.placements.some(m=>m.deviceId===p.originId)),...assigned]:assigned;}

export async function venueAuthor(store,group,incident,device){const venue=activeVenue(await store.read('meta','venue:'+group),group,incident);return venue?.placements.find(p=>p.deviceId===device)?.name;}
export async function saveVenue(store,body){
 if(!Object.hasOwn(body,'venue'))return;
 const venue=body.venue;
 if(venue){
  if(venue.version!==1||venue.mode!=='VENUE_DEMO'||venue.groupId!==body.groupId||venue.sessionId!==venue.incidentId||!Array.isArray(venue.placements)||venue.placements.length>64)throw Error('会場訓練の情報が不正です');
  if(venue.scenario){const s=venue.scenario;if(s.version!==1||s.sessionId!==venue.sessionId||!Array.isArray(s.people)||s.people.length>66||!Array.isArray(s.communities)||s.communities.length<1||s.communities.length>10||new Set(s.communities.map(c=>c.id)).size!==s.communities.length||new Set(s.people.map(p=>p.deviceId)).size!==s.people.length)throw Error('Invalid drill scenario');for(const p of s.people){if(!/^[0-9a-f-]{36}$/i.test(p.deviceId)||venue.placements.some(x=>x.deviceId===p.deviceId)||body.roster.members.some(m=>m.deviceIds.includes(p.deviceId))||typeof p.name!=='string'||!p.name.trim()||p.name.length>60||typeof p.note!=='string'||p.note.length>500||!['SAFE','SOS','UNREPORTED'].includes(p.safety)||!['UNKNOWN','INJURED','UNINJURED'].includes(p.injury)||!s.communities.some(c=>c.id===p.communityId))throw Error('Invalid resident');validateDemoHome(p.home);if(p.position&&(!Number.isFinite(p.position.latitude)||Math.abs(p.position.latitude)>85||!Number.isFinite(p.position.longitude)||Math.abs(p.position.longitude)>180))throw Error('Invalid drill phone position');}}
  for(const c of venue.scenario?.communities||[])if(c.boundary!=null&&(!Array.isArray(c.boundary)||c.boundary.length<3||c.boundary.length>100||!c.boundary.every(p=>Array.isArray(p)&&p.length===2&&Number.isFinite(p[0])&&Math.abs(p[0])<=180&&Number.isFinite(p[1])&&Math.abs(p[1])<=85)))throw Error('Invalid neighborhood boundary');
  for(const p of venue.placements)if(p.communityId!=null&&!['demo-west','demo-central','demo-east'].includes(p.communityId))throw Error('Invalid neighborhood membership');
  if(venue.simulationStartedAt!=null&&(!Number.isSafeInteger(venue.simulationStartedAt)||venue.simulationStartedAt<0))throw Error('Invalid drill clock');
  const cuts=venue.scenario?.communityLongitudeCuts;
  if(cuts!==undefined&&cuts!==null&&(!Array.isArray(cuts)||cuts.length!==2||!cuts.every(x=>Number.isFinite(x)&&x>=-180&&x<=180)||cuts[0]>=cuts[1]))throw Error('Invalid drill community boundaries');
  if(venue.presentationMode!==undefined&&!['DRILL','EARTHQUAKE'].includes(venue.presentationMode))throw Error('Invalid presentation mode');
  if(venue.positionMode!==undefined&&!['KYOTO','ACTUAL'].includes(venue.positionMode))throw Error('位置の表示方式を確認できません');
  for(const p of venue.placements)if(p.sessionId!==venue.sessionId||!body.roster.members.some(m=>m.memberId===p.memberId&&m.deviceIds.includes(p.deviceId))||(venue.positionMode==='ACTUAL'?(p.source!=='DEVICE_LOCATION'||p.latitude!==null||p.longitude!==null):(p.source!=='DEMO_ASSIGNED'||!Number.isFinite(p.latitude)||Math.abs(p.latitude)>85||!Number.isFinite(p.longitude)||Math.abs(p.longitude)>180)))throw Error('会場訓練の配置を確認できません');
 }
 if(venue&&!venue.closed&&(await store.read?.('meta','venue:'+body.groupId))?.sessionId!==venue.sessionId)await store.update('meta','venue-onboarding:'+body.groupId,()=>venue.sessionId);
 const previous=await store.read('meta','venue:'+body.groupId);
 await store.update('meta','venue:'+body.groupId,()=>venue);
 if(typeof window!=='undefined'&&JSON.stringify([previous?.sessionId,previous?.positionMode,previous?.closed])!==JSON.stringify([venue?.sessionId,venue?.positionMode,venue?.closed]))window.dispatchEvent(new CustomEvent('venue-map-change'));
}
