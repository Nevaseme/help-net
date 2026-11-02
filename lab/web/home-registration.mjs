import {placeBuildings,placeUnits,validHome,normalizePlace,validatePlace} from './places.mjs';
export function searchReference(candidate,input){if(!candidate)return undefined;return {input:input.trim().slice(0,240),label:candidate.address,latitude:candidate.latitude,longitude:candidate.longitude,matchLevel:candidate.matchLevel||candidate.precision,precision:candidate.precision,source:candidate.source||'legacy',sourceVersion:candidate.sourceVersion||'unknown'};}
export function validateSearchReference(r){
 if(!r||typeof r!=='object'||Array.isArray(r)||Object.keys(r).some(k=>!['input','label','latitude','longitude','matchLevel','precision','source','sourceVersion'].includes(k)))throw Error('住所検索の記録が不正です');
 for(const k of ['input','label','source','sourceVersion','matchLevel','precision'])if(typeof r[k]!=='string'||r[k].length>240)throw Error('住所検索の記録が不正です');
 if(!Number.isFinite(r.latitude)||Math.abs(r.latitude)>85||!Number.isFinite(r.longitude)||Math.abs(r.longitude)>180)throw Error('住所検索の位置が不正です');
 return r;
}
export function homeEvents(profile,home,rows){
 const existing=placeBuildings(rows),old=validHome(rows,profile.originId),previous=existing.find(b=>b.buildingId===old?.buildingId);
 const same=previous&&Math.abs(previous.latitude-profile.latitude)<1e-6&&Math.abs(previous.longitude-profile.longitude)<1e-6;
 const plateauId=home.plateauId||null;
 const match=home.buildingId?existing.find(b=>b.buildingId===home.buildingId):plateauId?existing.find(b=>b.locationReference?.plateauId===plateauId):same?previous:null;
 const buildingId=match?.buildingId||crypto.randomUUID();
 if(home.buildingId&&!match)throw Error('建物が未受信です');
 const unitId=Object.hasOwn(home,'unitId')?home.unitId:(old?.buildingId===buildingId?old.unitId:null);
 if(unitId&&!placeUnits(rows,buildingId).some(u=>u.unitId===unitId))throw Error('選んだ建物の住戸を指定してください');
 const reference={address:home.address?.trim()||'地図で選択した住居',plateauId,dataset:plateauId?'plateau-26100-kyoto-shi-2025':null,...(home.searchReference?{searchReference:validateSearchReference(home.searchReference)}:{})};
 let sequence=rows.filter(r=>r.authorId===profile.originId).reduce((n,r)=>Math.max(n,r.sequence),0);
 const base={groupId:profile.groupId,authorId:profile.originId,authorName:profile.name,createdAt:Date.now()};const out=[];
 if(!match||match.authorId===profile.originId)out.push(validatePlace(normalizePlace({...base,eventId:match?crypto.randomUUID():buildingId,buildingId,sequence:++sequence,kind:'BUILDING',label:reference.address.slice(0,80),latitude:profile.latitude,longitude:profile.longitude,locationReference:reference})));
 out.push(validatePlace(normalizePlace({...base,eventId:crypto.randomUUID(),sequence:++sequence,kind:'HOME',buildingId,unitId})));
 return out;
}
