import {validateRoster,cloudRoster,memberFor,validateQuake,validateQuakeCommand,formationCandidates,quakeStates,validateResponse,validateResponseCommand,responseHeads,sameRecord,canonicalIncidentId,eewIncidentStart,startWorkChanges} from './managed-records.mjs';
import {regionalSummary} from './regions.mjs';
import {operationRows,operationCsv} from './operations-export.mjs';

function knownPerson(tables,group,incident,id){return tables.residents.some(p=>p.groupId===group&&p.originId===id)||(tables.meta||[]).some(v=>v?.mode==='VENUE_DEMO'&&v.groupId===group&&v.incidentId===incident&&!v.closed&&[...v.placements,...v.scenario?.people||[]].some(p=>p.deviceId===id));}

export class ManagedStore {
 constructor(db){this.db=db;}
 roster(group){return this.read('rosters',group);}
 async quakeEvents(group){return (await this.read('quakes')).filter(e=>e.groupId===group);}
 async responseEvents(group){return (await this.read('responses')).filter(e=>e.groupId===group);}
 exportCsv(group,incidentId,regions={}, {language='ja',allowedResidentIds=null}={}){return this.managedTransaction(tables=>{
  const roster=tables.rosters.find(r=>r.groupId===group);if(!roster)throw Error('参加者設定を読み込んでください');
  return operationCsv(operationRows(roster,incidentId,tables.residents,tables.incidents,tables.quakes,tables.responses,regions,{language,allowedResidentIds}),{language});
 });}
 reportRegionSummary(group,incidentId,catalog,assignments={}){return this.managedTransaction(tables=>{
  const roster=tables.rosters.find(r=>r.groupId===group);if(!roster)throw Error('参加者設定を読み込んでください');
  const quakes=tables.quakes.filter(e=>e.groupId===group),selected=canonicalIncidentId(roster,quakes,incidentId);
  if(!quakeStates(roster,quakes).some(s=>s.formation.incidentId===selected))throw Error('集計する災害を選んでください');
  const heads=responseHeads(roster,tables.responses.filter(e=>e.groupId===group).map(e=>({...e,incidentId:canonicalIncidentId(roster,quakes,e.incidentId)})));
  const reports=heads.filter(e=>e.incidentId===selected&&e.change.type==='REPORT'&&!e.change.cancelled),ids=[...new Set(reports.map(e=>e.change.reportId))];
  return regionalSummary(catalog,ids.map(id=>({target:{kind:'REPORT',id},regionIds:assignments[id]??[...new Set(reports.filter(e=>e.change.reportId===id).flatMap(e=>e.change.regionIds||[]))]})));
 });}
 startWork(group,incidentId,actorDeviceId,target,at){return this.managedTransaction((tables,put)=>{
  const roster=tables.rosters.find(r=>r.groupId===group);if(!roster)throw Error('参加者設定を読み込んでください');
  const quakes=tables.quakes.filter(e=>e.groupId===group),selected=canonicalIncidentId(roster,quakes,incidentId),history=tables.responses.filter(e=>e.groupId===group);
  if(!quakeStates(roster,quakes).some(s=>s.incidentIds.includes(incidentId)&&!s.closed))throw Error('成立済みで開始中の災害を指定してください');
  const projected=history.map(e=>({...e,incidentId:canonicalIncidentId(roster,quakes,e.incidentId)})),heads=responseHeads(roster,projected).filter(e=>e.incidentId===selected);
  if(target?.kind==='PERSON'&&!knownPerson(tables,group,incidentId,target.id))throw Error('対象住民が見つかりません');
  if(target?.kind==='REPORT'&&!heads.some(e=>e.change.type==='REPORT'&&e.change.reportId===target.id&&!e.change.cancelled))throw Error('対象の通報が見つかりません');
  let sequence=history.filter(e=>e.actorDeviceId===actorDeviceId).reduce((n,e)=>Math.max(n,e.sequence),0);
  return startWorkChanges(roster,heads,actorDeviceId,target).map(plan=>{
   const event=validateResponse(roster,{groupId:group,eventId:crypto.randomUUID(),actorDeviceId,sequence:++sequence,operatedAt:at,incidentId,...plan});
   const normalized={...event,incidentId:selected};validateResponseCommand(roster,projected,normalized);projected.push(normalized);put('responses',event);return event;
  });
 });}
 // All reads, validation and writes are inside one transaction; ACK callers await oncomplete.
 managedTransaction(work){return new Promise((resolve,reject)=>{
  const names=['rosters','quakes','responses','incidents','residents','meta'];
  const tx=this.db.transaction(names,'readwrite'),tables={};let remaining=names.length,result,problem;
  for(const name of names){const q=tx.objectStore(name).getAll();q.onsuccess=()=>{
   tables[name]=q.result;if(--remaining)return;
   try{result=work(tables,(name,value)=>tx.objectStore(name).put(value));}catch(e){problem=e;tx.abort();}
  };}
  tx.oncomplete=()=>resolve(result);tx.onabort=()=>reject(problem||tx.error||Error('保存が中断されました'));tx.onerror=()=>{};
 });}
 importRoster(text,ownDeviceId){return this.managedTransaction((tables,put)=>{
  const r=validateRoster(typeof text==='string'?JSON.parse(text):text);memberFor(r,ownDeviceId);
  const old=tables.rosters.find(x=>x.groupId===r.groupId);if(old&&!sameRecord(old,r))throw Error('異なる参加者設定が保存されています');
  if(!old)put('rosters',r);return r;
 });}
 importCloudRoster(body,ownDeviceId){return this.managedTransaction((tables,put)=>{
  const next=cloudRoster(tables.rosters.find(r=>r.groupId===body.roster.groupId),body,ownDeviceId);
  put('rosters',next);return next;
 });}
 quakeTransaction(input,localDeviceId,local){return this.managedTransaction((tables,put)=>{
  const r=tables.rosters.find(x=>x.groupId===input.groupId);if(!r)throw Error('参加者設定を読み込んでください');
  let e=validateQuake(r,input);const events=tables.quakes.filter(x=>x.groupId===r.groupId),old=events.find(x=>x.eventId===e.eventId);
  if(old){if(!sameRecord(old,e)&&!(local&&e.action==='END'&&!e.endedIncidentIds.length&&sameRecord({...old,endedIncidentIds:[]},e)))throw Error('同じIDに異なる災害記録があります');return false;}
  if(local&&e.action==='END')e={...e,endedIncidentIds:quakeStates(r,events).find(s=>s.incidentIds.includes(e.targetId))?.incidentIds.toSorted()||[]};
  if(local)validateQuakeCommand(r,events,e);
  events.push(e);put('quakes',e);
  if(localDeviceId){memberFor(r,localDeviceId);for(const f of formationCandidates(r,events)){
   const formed=validateQuake(r,{groupId:r.groupId,eventId:crypto.randomUUID(),actorDeviceId:localDeviceId,operatedAt:local?e.operatedAt:Date.now(),action:'FORM',training:f.training,targetId:f.incidentId,reportIds:f.reportIds,...(f.rosterRevision?{rosterRevision:f.rosterRevision}:{})});events.push(formed);put('quakes',formed);
  }}
  for(const state of quakeStates(r,events)){
   for(const incidentId of state.incidentIds){
    const receipt=events.find(x=>x.action==='EEW'&&x.targetId===incidentId),report=events.find(x=>x.eventId===incidentId);
    const start=receipt?eewIncidentStart(r.groupId,receipt.eew):{groupId:r.groupId,eventId:report.eventId,incidentId:report.eventId,authorId:report.actorDeviceId,authorName:memberFor(r,report.actorDeviceId).activityName,sequence:1,createdAt:report.operatedAt,kind:'START',title:report.title,area:report.area,training:report.training,safety:null,subjectId:null,action:null,note:'',supersedes:null};
    if(!tables.incidents.some(x=>x.groupId===r.groupId&&x.eventId===incidentId))put('incidents',start);
   }
  }return true;
 });}
 mergeQuake(e,localDeviceId){return this.quakeTransaction(e,localDeviceId,false);}
 commandQuake(e){return this.quakeTransaction(e,e.actorDeviceId,true);}
 responseTransaction(input,local){return this.managedTransaction((tables,put)=>{
  const r=tables.rosters.find(x=>x.groupId===input.groupId);if(!r)throw Error('参加者設定を読み込んでください');
  const history=tables.responses.filter(x=>x.groupId===r.groupId);
  const e=validateResponse(r,local?{...input,sequence:history.filter(x=>x.actorDeviceId===input.actorDeviceId).reduce((n,x)=>Math.max(n,x.sequence),0)+1}:input);
  const old=history.find(x=>x.eventId===e.eventId);if(old){if(local||!sameRecord(old,e))throw Error('同じIDに異なる対応記録があります');return false;}
  if(history.some(x=>x.actorDeviceId===e.actorDeviceId&&x.sequence===e.sequence))throw Error('同じ更新番号に異なる対応記録があります');
  if(local){
   if(e.incidentId&&!quakeStates(r,tables.quakes).some(s=>s.incidentIds.includes(e.incidentId)&&!s.closed))throw Error('成立済みで開始中の災害を指定してください');
   const t=e.change.target;
   if(t?.kind==='PERSON'&&!knownPerson(tables,r.groupId,e.incidentId,t.id))throw Error('対象住民が見つかりません');
   if(t?.kind==='REPORT'&&!history.some(x=>x.eventId===t.id&&canonicalIncidentId(r,tables.quakes,x.incidentId)===canonicalIncidentId(r,tables.quakes,e.incidentId)&&x.change.type==='REPORT'))throw Error('対象の通報が見つかりません');
   validateResponseCommand(r,history.map(x=>({...x,incidentId:canonicalIncidentId(r,tables.quakes,x.incidentId)})),{...e,incidentId:canonicalIncidentId(r,tables.quakes,e.incidentId)});
  }put('responses',e);return local?e:true;
 });}
 mergeResponse(e){return this.responseTransaction(e,false);}
 appendResponse(e){return this.responseTransaction(e,true);}
 async responseHeads(group){const roster=await this.roster(group);const quakes=await this.quakeEvents(group);return roster?responseHeads(roster,(await this.responseEvents(group)).map(e=>({...e,incidentId:canonicalIncidentId(roster,quakes,e.incidentId)}))):[];}
}
