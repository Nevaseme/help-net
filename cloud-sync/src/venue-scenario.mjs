import catalogue from './venue-buildings.json' with {type:'json'};
import {withDemoObservations} from '../../lab/web/venue-observations.mjs';

export const demoCommunities=[
 {id:'demo-west',nameJa:'御所南西町内会（デモ）',nameEn:'Gosho South West (demo)'},
 {id:'demo-central',nameJa:'御所南中央町内会（デモ）',nameEn:'Gosho South Central (demo)'},
 {id:'demo-east',nameJa:'御所南東町内会（デモ）',nameEn:'Gosho South East (demo)'}
];
export const demoCommunityAt=longitude=>longitude<135.760?'demo-west':longitude<135.7615?'demo-central':'demo-east';

/** Fictional people are scenario objects, never login identities or authenticated phone observations. */
export function createVenueScenario(sessionId){
 const names=['Alex Morgan','Jamie Taylor','Sam Rivera','Jordan Lee','Casey Brown','Riley Chen','Avery Wilson','Cameron Davis','Robin Patel','Drew Martin','Quinn Anderson','Skyler Thompson'];
 const homes=catalogue.homes.slice().sort((a,b)=>a.buildingId.localeCompare(b.buildingId));
 const used=new Set();const people=names.map((name,i)=>{
  const community=demoCommunities[i%3],home=homes.find(h=>demoCommunityAt(h.longitude)===community.id&&!used.has(h.buildingId))||homes.find(h=>!used.has(h.buildingId));used.add(home.buildingId);
  return {deviceId:crypto.randomUUID(),name,safety:i<4?'SAFE':i<8?'UNREPORTED':'SOS',injury:i===8||i===10?'INJURED':i<4?'UNINJURED':'UNKNOWN',note:i>=8?'I need help. The doorway is blocked.':'',communityId:demoCommunityAt(home.longitude),home:{...home,floor:String(i%3+1),room:i%2?'Bedroom facing the street':'Bedroom at the back'}};
 });
 return withDemoObservations({version:1,sessionId,communities:demoCommunities,people});
}

export function seedVenueScenario(journal,actor,config){
 const groupId=journal.meta('groupId'),incidentId=config.sessionId,now=config.startedAt;
 const maxSeq=collection=>Math.max(0,...journal.history(collection).filter(e=>(e.actorDeviceId??e.authorId)===actor.deviceId&&(collection!=='incidents'||e.incidentId===incidentId)).map(e=>e.sequence||0));
 let seq=maxSeq('responses'),incidentSeq=Math.max(1,maxSeq('incidents'));
 const append=change=>{const eventId=change.reportId||change.requestId||crypto.randomUUID();journal.append(actor,{collection:'responses',record:{groupId,eventId,actorDeviceId:actor.deviceId,sequence:++seq,operatedAt:now,incidentId,change,replaces:[]}});return eventId;};
 const reports=[['ROAD_BLOCKED','Fallen masonry blocks the narrow street. Keep pedestrians away.','Crowbars','pieces',4,1],['WATER_OUTAGE','Water supply is interrupted. Drinking water is needed.','Drinking water','litres',120,40],['BUILDING_DAMAGE','An exterior wall is damaged. The entrance needs inspection.','Safety helmets','pieces',8,3]];
 reports.forEach(([category,description,name,unit,needed,arranged],i)=>{
  const p=config.scenario.people[i],reportId=crypto.randomUUID(),target={kind:'REPORT',id:reportId};
  append({type:'REPORT',reportId,category,description,latitude:p.home.latitude,longitude:p.home.longitude,regionIds:[p.communityId]});
  append({type:'MATERIAL',target,requestId:crypto.randomUUID(),name,unit,needed,arranged});
  append({type:'PRIORITY',target,urgency:i===0?'HIGH':'MEDIUM',reason:i===0?'People may enter an obstructed street.':'A neighborhood response is needed.'});
  if(i===1)append({type:'PROGRESS',target,status:'IN_PROGRESS',result:'A team is checking available water supplies.'});
 });
 for(const p of config.scenario.people){
  append({type:'INJURY',target:{kind:'PERSON',id:p.deviceId},state:p.injury});
 }
 for(const p of config.scenario.people.slice(10))journal.append(actor,{collection:'incidents',record:{groupId,eventId:crypto.randomUUID(),incidentId,authorId:actor.deviceId,authorName:'Demo rescue team',sequence:++incidentSeq,createdAt:now,kind:'CARE',subjectId:p.deviceId,action:'RESCUED',note:'Assisted out of the building and taken to the first-aid point.'}});
}
