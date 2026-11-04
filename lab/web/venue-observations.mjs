// Fictional scenario metadata only. Never persist these as device sensor readings.
export function demoObservationFlags(person,index){
 const flags=person.observations;
 return flags??(index<12?{impact:[4,6,8,9,10].includes(index),enclosed:[4,5,6,7,8,10].includes(index)}:{impact:person.safety!=='SAFE'&&(person.safety==='SOS'||index%3===1),enclosed:person.safety!=='SAFE'&&index%2===0});
}
export function demoObservationTimes(person,index,startedAt){
 const flags=demoObservationFlags(person,index),at=Number.isSafeInteger(startedAt)&&startedAt>0?startedAt:null;
 return {impactAt:flags.impact===true?at:null,enclosedAt:flags.enclosed===true?at:null};
}
export function withDemoObservations(scenario){
 return scenario?{...scenario,people:scenario.people.map((p,i)=>({...p,observations:demoObservationFlags(p,i)}))}:scenario;
}
