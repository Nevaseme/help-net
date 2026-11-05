import {validateSearchReference} from './home-registration.mjs';
const uuid=/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const check=(ok)=>{if(!ok)throw Error('端末の観測・訓練住居記録が不正です');};
export function validateDemoHome(h){
 if(h?.searchReference!==undefined)validateSearchReference(h.searchReference);
 check(h&&uuid.test(h.buildingId)&&typeof h.address==='string'&&h.address.trim().length>0&&h.address.length<=240);
 check(Number.isFinite(h.latitude)&&Math.abs(h.latitude)<=85&&Number.isFinite(h.longitude)&&Math.abs(h.longitude)<=180);
 check(h.plateauId===null||typeof h.plateauId==='string'&&h.plateauId.length>0&&h.plateauId.length<=160);
 check(typeof h.floor==='string'&&h.floor.length<=40&&typeof h.room==='string'&&h.room.length<=240);
 check(h.displayName===undefined||typeof h.displayName==='string'&&h.displayName.trim().length>0&&h.displayName.length<=60);
 return structuredClone(h);
}
export function validateDeviceRecord(r){
 check(r&&/^[A-Za-z0-9_-]{1,40}$/.test(r.groupId)&&[r.eventId,r.authorId].every(v=>uuid.test(v)));
 check(Number.isSafeInteger(r.sequence)&&r.sequence>0&&Number.isSafeInteger(r.createdAt)&&r.createdAt>=0);
 check(Object.keys(r).every(k=>['groupId','eventId','authorId','sequence','createdAt','kind','observedAt','source','sessionId','home'].includes(k)));
 if(r.kind==='IMPACT'){check(Number.isSafeInteger(r.observedAt)&&r.observedAt>0&&r.observedAt<=r.createdAt&&r.source==='VIVO_DROP'&&r.home==null&&r.sessionId==null);}
 else {check(r.kind==='DEMO_HOME'&&uuid.test(r.sessionId)&&r.observedAt==null&&r.source==null);validateDemoHome(r.home);}
 return structuredClone(r);
}
export function latestDemoHome(rows,group,session,device){return rows.filter(r=>r.groupId===group&&r.sessionId===session&&r.authorId===device&&r.kind==='DEMO_HOME').sort((a,b)=>b.sequence-a.sequence||b.eventId.localeCompare(a.eventId))[0]?.home;}
export function latestImpact(rows,group,device,now=Date.now()){return rows.filter(r=>r.groupId===group&&r.authorId===device&&r.kind==='IMPACT'&&r.observedAt<=now&&r.observedAt>now-30*86400000).reduce((at,r)=>Math.max(at,r.observedAt),0)||null;}
