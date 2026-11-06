import layout from './venue-compact-layout.json' with {type:'json'};
export const compactCommunities=layout.communities;
// Forty safe resident roles are replaced one-for-one by real attendees.
const replaceableResidents=()=>layout.people.filter(p=>p.communityId==='demo-central'&&p.safety==='SAFE');
export const compactSlots=()=>structuredClone([...layout.slots.slice(0,32),...replaceableResidents().map(p=>({...p.position,home:p.home,communityId:p.communityId}))]);
export const compactBlockedRoadPoint=layout.blockedRoadPoint;
export const compactReportPoints=layout.reportPoints;
export function compactScenario(sessionId,startedAt){
 const given=['Alex','Jamie','Sam','Jordan','Casey','Riley','Avery','Cameron','Robin','Drew','Quinn','Skyler'];
 const family=['Morgan','Taylor','Rivera','Lee','Brown','Chen','Wilson','Davis','Park','Reed','Evans'];
 const replaceable=replaceableResidents();
 const residents=[...layout.people.map(p=>({...p,...(replaceable.includes(p)?{replacementSlotId:32+replaceable.indexOf(p)}:{})})),...layout.slots.slice(0,32).map((p,i)=>({communityId:p.communityId,home:p.home,position:{latitude:p.latitude,longitude:p.longitude},safety:'SAFE',injury:'UNINJURED',note:'',observations:{impact:false,enclosed:false},replacementSlotId:i})),...layout.slots.slice(36,40).map(p=>({communityId:p.communityId,home:p.home,position:{latitude:p.latitude,longitude:p.longitude},safety:'SOS',injury:'UNINJURED',note:'',observations:{impact:false,enclosed:false},initialRescue:true}))];
 const people=residents.map((person,i)=>{
  const {lastKnownAgeDays,...p}=structuredClone(person);
  if(p.lastKnownPosition)p.lastKnownPosition.acquiredAt=startedAt-lastKnownAgeDays*86400000;
  return {...p,deviceId:crypto.randomUUID(),name:given[i%given.length]+' '+family[Math.floor(i/given.length)]};
 });
 return {version:1,layoutVersion:5,sessionId,communities:structuredClone(layout.communities),communityLongitudeCuts:structuredClone(layout.communityLongitudeCuts),people};
}

/** Project only new sessions; persisted identities, earlier sessions and records stay intact. */
export function occupiedVenueScenario(scenario,placements){
 if(scenario?.layoutVersion!==5)return scenario;
 const occupied=new Set(placements.map(p=>p.slotId));
 return {...scenario,people:scenario.people.filter(p=>!Number.isInteger(p.replacementSlotId)||!occupied.has(p.replacementSlotId))};
}
