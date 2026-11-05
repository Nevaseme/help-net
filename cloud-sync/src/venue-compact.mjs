import layout from './venue-compact-layout.json' with {type:'json'};
export const compactCommunities=layout.communities;
export const compactSlots=()=>structuredClone(layout.slots);
export const compactBlockedRoadPoint=layout.blockedRoadPoint;
export function compactScenario(sessionId,startedAt){
 const given=['Alex','Jamie','Sam','Jordan','Casey','Riley','Avery','Cameron','Robin','Drew','Quinn','Skyler'];
 const family=['Morgan','Taylor','Rivera','Lee','Brown','Chen','Wilson','Davis'];
 const people=layout.people.map((person,i)=>{
  const {lastKnownAgeDays,...p}=structuredClone(person);
  if(p.lastKnownPosition)p.lastKnownPosition.acquiredAt=startedAt-lastKnownAgeDays*86400000;
  return {...p,deviceId:crypto.randomUUID(),name:given[i%given.length]+' '+family[Math.floor(i/given.length)]};
 });
 return {version:1,layoutVersion:2,sessionId,communities:structuredClone(layout.communities),communityLongitudeCuts:[135.760,135.7644],people};
}
