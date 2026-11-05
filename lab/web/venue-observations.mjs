// Fictional scenario metadata only. Never persist these as device sensor readings.
export function demoObservationFlags(person,index){
 const flags=person.observations;if(!flags&&person.positionUnknown)return {impact:false,enclosed:false};
 return flags??(index<12?{impact:[4,6,8,9,10].includes(index),enclosed:[4,5,6,7,8,10].includes(index)}:{impact:person.safety!=='SAFE'&&(person.safety==='SOS'||index%3===1),enclosed:person.safety!=='SAFE'&&index%2===0});
}
export function demoObservationTimes(person,index,startedAt){
 const flags=demoObservationFlags(person,index),at=Number.isSafeInteger(startedAt)&&startedAt>0?startedAt:null;
 return {impactAt:flags.impact===true?at:null,enclosedAt:flags.enclosed===true?at:null};
}
export function withDemoObservations(scenario){
 return scenario?{...scenario,people:scenario.people.map((p,i)=>({...p,observations:demoObservationFlags(p,i)}))}:scenario;
}

/** Two fictional examples; existing scenario identities and observations are preserved. */
export function withUnknownDemoResidents(scenario){
 if(!scenario||scenario.people.some(p=>p.positionUnknown===true))return scenario;
 const home=scenario.people.find(p=>p.communityId==='demo-central')?.home;
 if(!home)return scenario;
 const examples=[['a1','Alex Kim','UNREPORTED'],['a2','Jamie Park','SOS']].map(([suffix,name,safety])=>({deviceId:scenario.sessionId.slice(0,24)+'0000000000'+suffix,name,safety,injury:'UNKNOWN',note:safety==='SOS'?'I need help. My phone location is unavailable.':'',communityId:'demo-central',home:{...home},positionUnknown:true,position:null,observations:{impact:false,enclosed:false}}));
 return {...scenario,people:[...scenario.people,...examples]};
}
