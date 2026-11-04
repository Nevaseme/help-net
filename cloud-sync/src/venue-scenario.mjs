import catalogue from './venue-buildings.json' with {type:'json'};
import {withDemoObservations} from '../../lab/web/venue-observations.mjs';

export const demoCommunities=[
 {id:'demo-west',latitude:35.0145,longitude:135.759,nameJa:'御所南西町内会',nameEn:'Gosho South West'},
 {id:'demo-central',latitude:35.0145,longitude:135.7622,nameJa:'御所南中央町内会',nameEn:'Gosho South Central'},
 {id:'demo-east',latitude:35.0145,longitude:135.7657,nameJa:'御所南東町内会',nameEn:'Gosho South East'}
];
import {communityLongitudeCuts,demoCommunityAt,demoPhonePoint} from './venue-layout.mjs';
export {demoCommunityAt};

/** Fictional people are scenario objects, never login identities or authenticated phone observations. */
export function createVenueScenario(sessionId){
 const names=['Alex Morgan','Jamie Taylor','Sam Rivera','Jordan Lee','Casey Brown','Riley Chen','Avery Wilson','Cameron Davis','Robin Patel','Drew Martin','Quinn Anderson','Skyler Thompson'];
 const homes=catalogue.homes.slice().sort((a,b)=>a.buildingId.localeCompare(b.buildingId));
 const ordered=[],used=new Set();
 for(let i=0;i<12;i++){const community=demoCommunities[i%3];const home=homes.find(h=>demoCommunityAt(h.longitude)===community.id&&!used.has(h.buildingId))||homes.find(h=>!used.has(h.buildingId));used.add(home.buildingId);ordered.push(home);}
 const usedPlacements=new Set(ordered.map(h=>h.placementId));ordered.push(...homes.filter(h=>!usedPlacements.has(h.placementId)));
 const given=['Taylor','Morgan','Parker','Reese','Rowan','Finley','Blake','Hayden'],family=['Brooks','Reed','Hayes','Bell','Carter','Adams','Wright'];
 const regionSlots=new Map();
 const people=ordered.map((home,i)=>{
  const region=demoCommunityAt(home.longitude),slot=regionSlots.get(region)||0;regionSlots.set(region,slot+1);
  const extra=i-12,phase=extra%13,safety=i<12?(i<4?'SAFE':i<8?'UNREPORTED':'SOS'):(phase<9?'SAFE':phase<11?'UNREPORTED':'SOS');
  const initialRescue=i===10||i===11||i>=12&&phase===12;
  const injury=i===8||i===10||i>=12&&phase===11?'INJURED':safety==='SAFE'?'UNINJURED':'UNKNOWN';
  const name=names[i]||given[Math.floor(extra/family.length)]+' '+family[extra%family.length];
  const countFloors=Math.max(1,Number(home.attributes?.floors)||1);
  return {position:demoPhonePoint(region,slot),deviceId:crypto.randomUUID(),name,safety,injury,initialRescue,note:safety==='SOS'?'I need help. The doorway is blocked.':'',communityId:demoCommunityAt(home.longitude),home:{...home,floor:String(i%Math.min(3,countFloors)+1),room:i%2?'Bedroom facing the street':'Bedroom at the back'}};
 });
 return withDemoObservations({version:1,sessionId,communityLongitudeCuts,communities:demoCommunities,people});
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
 for(const p of config.scenario.people.filter(p=>p.initialRescue===true))journal.append(actor,{collection:'incidents',record:{groupId,eventId:crypto.randomUUID(),incidentId,authorId:actor.deviceId,authorName:'Demo rescue team',sequence:++incidentSeq,createdAt:now,kind:'CARE',subjectId:p.deviceId,action:'RESCUED',note:'Assisted out of the building and taken to the first-aid point.'}});
}
