import {compactScenario,compactReportPoints} from './venue-compact.mjs';
import catalogue from './venue-buildings.json' with {type:'json'};
import {withDemoObservations,withUnknownDemoResidents} from '../../lab/web/venue-observations.mjs';

import {communityLongitudeCuts,demoCommunityAt,demoCommunities,demoPhonePoint} from './venue-layout.mjs';
export {demoCommunityAt,demoCommunities,demoPhonePoint};

// Reviewed on OpenStreetMap: south carriageway of Oike-dori, east of Muromachi-dori.
export const blockedRoadSamplePoint={latitude:35.01078,longitude:135.75824};
const blockedRoadDescription='Fallen masonry blocks the narrow street. Keep pedestrians away.';

/** Append a correction only for the untouched, identifiable original scenario report. */
export function repairSeededBlockedRoad(journal,actor){
 if(actor.role!=='ADMIN')return false;
 const config=journal.venueConfig(),person=config?.scenario?.people?.[0];
 if(!person||config.privateTraining||journal.venueSnapshot().closed)return false;
 const events=journal.history('responses');
 const original=events.find(e=>e.incidentId===config.sessionId&&e.operatedAt===config.startedAt&&e.eventId===e.change.reportId&&
  e.change.type==='REPORT'&&e.change.category==='ROAD_BLOCKED'&&e.change.description===blockedRoadDescription&&
  Object.keys(e.change).every(k=>['type','reportId','category','description','latitude','longitude','regionIds'].includes(k))&&
  e.change.latitude===person.home.latitude&&e.change.longitude===person.home.longitude&&
  JSON.stringify(e.change.regionIds)===JSON.stringify([person.communityId])&&!(e.replaces?.length));
 if(!original)return false;
 const related=events.filter(e=>e.change.reportId===original.change.reportId||e.change.target?.kind==='REPORT'&&e.change.target.id===original.change.reportId);
 // The initial report, material and priority must all still match the seed; any edit opts out.
 if(related.length!==3||related.some(e=>e.actorDeviceId!==original.actorDeviceId||e.operatedAt!==config.startedAt||e.incidentId!==config.sessionId||e.replaces?.length))return false;
 if(!related.some(e=>e.change.type==='MATERIAL'&&e.change.requestId===e.eventId&&e.change.name==='Crowbars'&&e.change.unit==='pieces'&&e.change.needed===4&&e.change.arranged===1&&!e.change.cancelled)||
    !related.some(e=>e.change.type==='PRIORITY'&&e.change.urgency==='HIGH'&&e.change.reason==='People may enter an obstructed street.'))return false;
 const sequence=1+Math.max(0,...events.filter(e=>e.actorDeviceId===actor.deviceId).map(e=>e.sequence));
 journal.append(actor,{collection:'responses',record:{...original,eventId:crypto.randomUUID(),actorDeviceId:actor.deviceId,sequence,operatedAt:journal.now(),change:{...original.change,...blockedRoadSamplePoint},replaces:[original.eventId]}});
 return true;
}

/** Fictional people are scenario objects, never login identities or authenticated phone observations. */
export function createVenueScenario(sessionId,startedAt=Date.now()){return compactScenario(sessionId,startedAt);}

export function seedVenueScenario(journal,actor,config){
 const groupId=journal.meta('groupId'),incidentId=config.sessionId,now=config.startedAt;
 const maxSeq=collection=>Math.max(0,...journal.history(collection).filter(e=>(e.actorDeviceId??e.authorId)===actor.deviceId&&(collection!=='incidents'||e.incidentId===incidentId)).map(e=>e.sequence||0));
 let seq=maxSeq('responses'),incidentSeq=Math.max(1,maxSeq('incidents'));
 const append=change=>{const eventId=change.reportId||change.requestId||crypto.randomUUID();journal.append(actor,{collection:'responses',record:{groupId,eventId,actorDeviceId:actor.deviceId,sequence:++seq,operatedAt:now,incidentId,change,replaces:[]}});return eventId;};
 const reports=[['ROAD_BLOCKED','Fallen masonry blocks the narrow street. Keep pedestrians away.','Crowbars','pieces',4,1],['WATER_OUTAGE','Water supply is interrupted. Drinking water is needed.','Drinking water','litres',120,40],['BUILDING_DAMAGE','An exterior wall is damaged. The entrance needs inspection.','Safety helmets','pieces',8,3]];
 reports.forEach(([category,description,name,unit,needed,arranged],i)=>{
  const p=config.scenario.people.find(p=>p.communityId===['demo-west','demo-central','demo-east'][i])||config.scenario.people[i],reportId=crypto.randomUUID(),target={kind:'REPORT',id:reportId};
  const point=[4,5].includes(config.scenario.layoutVersion)?compactReportPoints[category]:config.scenario.layoutVersion===3?({ROAD_BLOCKED:{latitude:35.01408978053494,longitude:135.7631439569464},WATER_OUTAGE:{latitude:35.01389829885585,longitude:135.76360659543633},BUILDING_DAMAGE:{latitude:35.01417099783852,longitude:135.7643079065655}})[category]:i===0?(config.scenario.layoutVersion===2?{latitude:35.01208791859861,longitude:135.75812686573374}:blockedRoadSamplePoint):p.home;
  append({type:'REPORT',reportId,category,description,latitude:point.latitude,longitude:point.longitude,regionIds:[p.communityId]});
  append({type:'MATERIAL',target,requestId:crypto.randomUUID(),name,unit,needed,arranged});
  append({type:'PRIORITY',target,urgency:i===0?'HIGH':'MEDIUM',reason:i===0?'People may enter an obstructed street.':'A neighborhood response is needed.'});
  if(i===1)append({type:'PROGRESS',target,status:'IN_PROGRESS',result:'A team is checking available water supplies.'});
 });
 for(const p of config.scenario.people){
  append({type:'INJURY',target:{kind:'PERSON',id:p.deviceId},state:p.injury});
 }
 for(const p of config.scenario.people.filter(p=>p.initialRescue===true))journal.append(actor,{collection:'incidents',record:{groupId,eventId:crypto.randomUUID(),incidentId,authorId:actor.deviceId,authorName:'Rescue team',sequence:++incidentSeq,createdAt:now,kind:'CARE',subjectId:p.deviceId,action:'RESCUED',note:'Assisted out of the building and taken to the first-aid point.'}});
}
