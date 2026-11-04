import {canReplaceCare} from '../../lab/web/care-policy.mjs';
import {validateResident,validateEvent} from '../../lab/web/sync.mjs';
import {validateIncident} from '../../lab/web/incidents.mjs';
import {validatePlace} from '../../lab/web/places.mjs';
import {validateLocal} from '../../lab/web/local-situation.mjs';
import {validateDeviceRecord} from '../../lab/web/device-records.mjs';
import {validateQuake,quakeFormationProof,quakeStates,memberFor,validateResponse,validateResponseAuthorization,canonicalIncidentId} from '../../lab/web/managed-records.mjs';

export class ApiError extends Error {constructor(status,code,message=code){super(message);this.status=status;this.code=code;}}
export const requireValue=(ok,code='invalid_request',status=400,message)=>{if(!ok)throw new ApiError(status,code,message);};
export const uuid=value=>typeof value==='string'&&/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(value);
export const canonical=value=>JSON.stringify(order(value));
function order(v){return Array.isArray(v)?v.map(order):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,order(v[k])])):v;}
export const collections=Object.freeze({
 deviceRecords:{actor:'authorId',key:'eventId',validate:validateDeviceRecord},
 residents:{actor:'originId',key:'originId',revision:true,validate:validateResident},
 events:{actor:'authorId',key:'eventId',validate:validateEvent},
 incidents:{actor:'authorId',key:'eventId',validate:validateIncident},
 places:{actor:'authorId',key:'eventId',validate:validatePlace},
 positions:{actor:'originId',key:'originId',revision:true,validate:r=>validateLocal(r,true)},
 memos:{actor:'authorId',key:'answerId',revision:true,validate:r=>validateLocal(r,false)},
 quakes:{actor:'actorDeviceId',key:'eventId'},responses:{actor:'actorDeviceId',key:'eventId'}
});
export function validateShape(collection,input,roster){
 const def=collections[collection];requireValue(def&&input&&typeof input==='object'&&!Array.isArray(input));
 requireValue(uuid(input[def.actor])&&uuid(input[def.key]));
 requireValue(new TextEncoder().encode(JSON.stringify(input)).length<=24000,'record_too_large',413);
 // Context-dependent managed validators run only after duplicate detection and actor authorization.
 try{return def.validate?def.validate(input):structuredClone(input);}catch(e){throw new ApiError(400,'invalid_record',e.message);}
}
export function validateContext(collection,record,identity,roster,history){
 if(collection==='quakes'&&['END','TRAINING_START','ADMIN_START'].includes(record.action)||collection==='incidents'&&record.kind==='END')requireValue(identity.role==='ADMIN','admin_required',403);
 if(collection==='incidents'&&(record.kind==='START'||record.kind==='END'))throw new ApiError(400,'managed_incident_required','Upload managed REPORT/FORM/EEW/END; synthetic journal rows are derived locally.');
 try{
  if(collection==='incidents'){
   const events=history('quakes');
   requireValue(quakeStates(roster,events).some(s=>s.incidentIds.includes(record.incidentId)),'missing_incident',409);
   const receipt=events.find(e=>e.action==='EEW'&&e.targetId===record.incidentId),report=events.find(e=>['REPORT','TRAINING_START','ADMIN_START'].includes(e.action)&&e.eventId===record.incidentId);
   const startAuthor=receipt?record.incidentId:report?.actorDeviceId;
   requireValue(!(record.sequence===1&&record.authorId===startAuthor),'sequence_conflict',409);
   if(record.kind==='CARE'&&record.supersedes){
    const old=history('incidents').find(e=>e.eventId===record.supersedes);
    requireValue(old,'missing_care_record',409);
    const canonical=e=>({...e,incidentId:canonicalIncidentId(roster,events,e.incidentId)});
    requireValue(canReplaceCare(canonical(record),canonical(old),roster),'care_forbidden',403);
   }
  }
  if(collection==='quakes'){
   const e=validateQuake(roster,record),events=history('quakes');
   if(e.action==='FORM')requireValue(quakeFormationProof(roster,events,e),'missing_formation_evidence',409);
   if(e.action==='END')requireValue(quakeStates(roster,events).some(s=>s.incidentIds.includes(e.targetId)&&s.formation.training===e.training&&e.endedIncidentIds.every(id=>s.incidentIds.includes(id))),'missing_incident',409);
   if(e.action==='CANCEL')requireValue(events.some(p=>p.action==='REPORT'&&p.eventId===e.targetId&&p.training===e.training&&memberFor(roster,p.actorDeviceId).memberId===identity.memberId),'invalid_cancel',403);
   return e;
  }
  if(collection==='responses'){
   const quakes=history('quakes'),canonical=e=>({...e,incidentId:canonicalIncidentId(roster,quakes,e.incidentId)});
   validateResponseAuthorization(roster,history('responses').map(canonical),canonical(record));
   return validateResponse(roster,record);
  }
  return record;
 }catch(e){if(e instanceof ApiError)throw e;if(e.code==='response_forbidden')throw new ApiError(403,e.code,e.message);throw new ApiError(400,'invalid_record',e.message);}
}
