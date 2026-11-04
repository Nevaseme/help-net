// Authorization is supplied by the trusted group roster, never by a role in an event.
export const careAdministrator=(roster,device,groupId)=>!!roster&&(groupId===undefined||roster.groupId===groupId)&&!!roster.members?.some(m=>m.role==='ADMIN'&&m.deviceIds?.includes(device));
export function canReplaceCare(event,previous,roster=null){
 if(!previous||event.groupId!==previous.groupId||event.subjectId!==previous.subjectId||(event.incidentId??null)!==(previous.incidentId??null))return false;
 return event.authorId===previous.authorId||(event.action==='REVOKED'&&previous.action==='RESCUED'&&careAdministrator(roster,event.authorId,event.groupId));
}
export function currentCareHeads(events,roster=null){
 const byId=new Map(events.map(e=>[e.eventId,e]));
 const replaced=new Set(events.filter(e=>canReplaceCare(e,byId.get(e.supersedes),roster)).map(e=>e.supersedes));
 return events.filter(e=>!replaced.has(e.eventId));
}
