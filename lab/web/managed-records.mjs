import {incidentOptions} from './incidents.mjs';
// Shared experiment rules. Stored event IDs remain immutable across corrections and merged incidents.
const uuid=/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const check=(ok,message='記録の形式が不正です')=>{if(!ok)throw Error(message);};
const integer=n=>Number.isSafeInteger(n)&&n>=0;
const nonempty=s=>typeof s==='string'&&s.trim().length>0;
export const memberFor=(roster,device)=>{const member=roster.members.find(m=>m.deviceIds.includes(device));check(member,'参加者に登録されていない端末です');return member;};
export const canTriage=member=>member?.role==='ADMIN'||member?.canTriage===true;
const couldTriage=(r,device)=>canTriage(memberFor(r,device))||memberFor(r,device).role==='LEADER'||(r.history??[]).some(h=>h.members.some(m=>m.deviceIds.includes(device)&&canTriage(m)));
export function requireTriagePermission(r,e){if(['INJURY','PRIORITY'].includes(e.change.type))permissionCheck(canTriage(memberFor(r,e.actorDeviceId)),'負傷登録・緊急度設定の権限がありません');}
export const preservesMember=(old,next)=>!!next&&old.memberId===next.memberId&&old.activityName===next.activityName&&old.role===next.role&&old.deviceIds.every((d,i)=>next.deviceIds[i]===d)&&(old.role==='MEMBER'||old.deviceIds.length===next.deviceIds.length);
export const requiredReporters=n=>Math.min(10,Math.max(2,Math.ceil(n/3)));
export function validateRoster(input){
 const r={version:1,...input};check(r.version===1&&/^[A-Za-z0-9_-]{1,40}$/.test(r.groupId)&&Array.isArray(r.members)&&r.members.length);
 const devices=r.members.flatMap(m=>m.deviceIds);check(new Set(devices).size===devices.length&&new Set(r.members.map(m=>m.memberId)).size===r.members.length);
 check(r.members.some(m=>m.role==='ADMIN'),'管理者が必要です');
 for(const m of r.members)check(uuid.test(m.memberId)&&nonempty(m.activityName)&&m.activityName.length<=60&&['MEMBER','LEADER','ADMIN'].includes(m.role)&&Array.isArray(m.deviceIds)&&m.deviceIds.length&&m.deviceIds.every(d=>uuid.test(d)));
 for(const m of r.members)check(m.canTriage===undefined||typeof m.canTriage==='boolean');
 check(integer(r.revision??0));
 if(r.history!==undefined){check(Array.isArray(r.history));const seen=new Set();for(const h of r.history){check(integer(h.revision)&&h.revision<=(r.revision??0)&&!seen.has(h.revision));seen.add(h.revision);validateRoster({version:1,groupId:r.groupId,members:h.members});}}
 return r;
}
export function rosterAtRevision(r,revision=0){
 if((r.revision??0)===revision)return r;
 const h=r.history?.find(h=>h.revision===revision);
 return h?{version:1,groupId:r.groupId,members:h.members}:null;
}
export function cloudRoster(previous,body,deviceId){
 const {roster,rosterRevision,rosterHistory}=body;
 check(integer(rosterRevision)&&rosterRevision>0&&Array.isArray(rosterHistory)&&rosterHistory.length===rosterRevision,'名簿の版と履歴を確認してください');
 const current=validateRoster(roster);memberFor(current,deviceId);
 const history=rosterHistory.map((h,i)=>{check(h.revision===i+1&&h.roster.groupId===current.groupId);const r=validateRoster(h.roster);return {revision:h.revision,members:r.members};});
 check(sameRecord(history.at(-1).members,current.members));
 for(let i=1;i<history.length;i++)for(const old of history[i-1].members)check(preservesMember(old,history[i].members.find(m=>m.memberId===old.memberId)),'既存の参加者・権限は変更できません');
 if(previous){
  check(previous.groupId===current.groupId&&(previous.revision??0)<=rosterRevision,'名簿を古い版へ戻せません');
  for(const old of previous.members)check(preservesMember(old,current.members.find(m=>m.memberId===old.memberId)),'既存の参加者・権限は変更できません');
  const known=[...(previous.history??[]),{revision:previous.revision??0,members:previous.members}];
  for(const h of known){const match=history.find(x=>x.revision===h.revision);if(match)check(sameRecord(match.members,h.members),'保存済み名簿と履歴が異なります');else {check(h.revision===0);if(!history.some(x=>x.revision===0))history.unshift(h);}}
 }
 return validateRoster({...current,revision:rosterRevision,history});
}
export function validateQuake(r,input){
 const e={targetId:null,reportIds:[],title:'',area:'',endedIncidentIds:[],...input};memberFor(r,e.actorDeviceId);
 check(e.groupId===r.groupId&&uuid.test(e.eventId)&&integer(e.operatedAt)&&typeof e.training==='boolean');
 check(e.targetId===null||uuid.test(e.targetId));check(Array.isArray(e.reportIds)&&e.reportIds.every(x=>uuid.test(x))&&new Set(e.reportIds).size===e.reportIds.length);
 check(Array.isArray(e.endedIncidentIds)&&e.endedIncidentIds.every(x=>uuid.test(x))&&new Set(e.endedIncidentIds).size===e.endedIncidentIds.length);
 check(e.action==='END'?(!e.endedIncidentIds.length||e.endedIncidentIds.includes(e.targetId)):!e.endedIncidentIds.length);
 check((e.action==='EEW')===(e.eew!=null));
 check(integer(e.rosterRevision??0)&&((e.rosterRevision??0)===0||e.action==='FORM'));
 if(e.action==='EEW'){const p=e.eew={startsNewPeriod:false,...e.eew};check(typeof p.startsNewPeriod==='boolean'&&['wolfx','p2pquake'].includes(p.source)&&/^[0-9]{14}$/.test(p.eventId)&&integer(p.issuedAt)&&integer(p.receivedAt)&&p.receivedAt-p.issuedAt<=180000);check(!e.training&&e.operatedAt===p.receivedAt&&e.targetId===eewIncidentId(p)&&!e.reportIds.length&&!e.title&&!e.area);}
 else if(['REPORT','TRAINING_START','ADMIN_START'].includes(e.action))check(e.targetId===null&&!e.reportIds.length&&nonempty(e.title)&&e.title.length<=80&&nonempty(e.area)&&e.area.length<=120);
 else {check(['CANCEL','FORM','END'].includes(e.action)&&e.targetId!==null&&e.targetId!==e.eventId&&!e.title&&!e.area);check(e.action==='FORM'?e.reportIds.length>0:e.reportIds.length===0);}
 if(e.action==='END')check(memberFor(r,e.actorDeviceId).role==='ADMIN','終了できるのは管理者だけです');
 if(e.action==='ADMIN_START')check(!e.training&&memberFor(r,e.actorDeviceId).role==='ADMIN','地震を開始できるのは管理者だけです');
 if(e.action==='TRAINING_START')check(e.training&&memberFor(r,e.actorDeviceId).role==='ADMIN','訓練を開始できるのは管理者だけです');
 return e;
}
export const eewIncidentId=p=>`ee000001-00${p.eventId.slice(0,2)}-4000-8000-${p.eventId.slice(-12)}`;
export const eewIncidentStart=(group,p)=>{const id=eewIncidentId(p);return {groupId:group,eventId:id,incidentId:id,authorId:id,authorName:'緊急地震速報',sequence:1,createdAt:0,kind:'START',title:'地震',area:'京都府南部',training:false,safety:null,subjectId:null,action:null,note:'',supersedes:null};};
const compare=(a,b)=>a.operatedAt-b.operatedAt||(a.eventId<b.eventId?-1:a.eventId>b.eventId?1:0);
function proof(r,events,e){
 if(e.action!=='FORM')return null;
 const basis=rosterAtRevision(r,e.rosterRevision??0);if(!basis)return null;
 const reports=e.reportIds.map(id=>events.find(x=>x.eventId===id&&x.action==='REPORT'&&x.groupId===r.groupId));
 if(reports.some(x=>!x))return null;reports.sort(compare);
 if(!reports.length||reports[0].eventId!==e.targetId||reports.some(x=>x.training!==e.training||!basis.members.some(m=>m.deviceIds.includes(x.actorDeviceId)))||new Set(reports.map(x=>memberFor(basis,x.actorDeviceId).memberId)).size<requiredReporters(basis.members.length))return null;
 return {groupId:r.groupId,incidentId:reports[0].eventId,firstReportedAt:reports[0].operatedAt,training:e.training,reportIds:reports.map(x=>x.eventId),...(e.rosterRevision?{rosterRevision:e.rosterRevision}:{})};
}
export {proof as quakeFormationProof};
// Reports are partitioned only by a valid administrator END, never by their age.
function quakePeriods(r,events){
 const relevant=events.filter(e=>e.groupId===r.groupId).map(e=>validateQuake(r,e));
 const receipts=[...new Set(relevant.filter(e=>e.action==='EEW').map(e=>e.targetId))].map(id=>{
  const first=relevant.filter(e=>e.action==='EEW'&&e.targetId===id).sort(compare)[0];
  const end=Math.min(Infinity,...relevant.filter(e=>e.action==='END'&&!e.training&&(e.targetId===id||e.endedIncidentIds.includes(id))).map(e=>e.operatedAt));
  return {...first,operatedAt:Math.min(first.operatedAt,end)};
 });
 const trainingStarts=relevant.filter(e=>['TRAINING_START','ADMIN_START'].includes(e.action));
 const reports=[...relevant.filter(e=>e.action==='REPORT'),...trainingStarts,...receipts].sort(compare),byId=new Map(reports.map(e=>[e.eventId,e]));
 const forms=[...relevant.map(e=>proof(r,relevant,e)).filter(Boolean),...trainingStarts.map(e=>({groupId:r.groupId,incidentId:e.eventId,firstReportedAt:e.operatedAt,training:e.training,reportIds:[e.eventId]})),...receipts.map(e=>({groupId:r.groupId,incidentId:e.targetId,firstReportedAt:e.operatedAt,training:false,reportIds:[e.eventId]}))].sort((a,b)=>a.firstReportedAt-b.firstReportedAt||(a.incidentId<b.incidentId?-1:a.incidentId>b.incidentId?1:0)||a.reportIds.join().localeCompare(b.reportIds.join()));
 const periods=[];
 for(const training of [false,true]){
  let after=-1;
  while(true){
   const remaining=forms.filter(f=>f.training===training&&f.reportIds.every(id=>byId.get(id).operatedAt>after));
   const end=relevant.filter(e=>e.action==='END'&&e.training===training&&e.operatedAt>after&&remaining.some(f=>(f.incidentId===e.targetId||e.endedIncidentIds.includes(f.incidentId))&&f.reportIds.every(id=>byId.get(id).operatedAt<=e.operatedAt))).sort(compare)[0];
   const until=end?.operatedAt??Infinity;
   periods.push({training,closed:!!end,reports:reports.filter(e=>e.training===training&&e.operatedAt>after&&e.operatedAt<=until),forms:remaining.filter(f=>f.reportIds.every(id=>byId.get(id).operatedAt<=until))});
   if(!end)break;after=until;
  }
 }return periods;
}
export function quakeStates(r,events){
 return quakePeriods(r,events).filter(p=>p.forms.length).map(p=>({formation:{...p.forms[0],firstReportedAt:p.reports[0].operatedAt},incidentIds:[...new Set(p.forms.map(f=>f.incidentId))],closed:p.closed}))
 .sort((a,b)=>a.formation.firstReportedAt-b.formation.firstReportedAt||(a.formation.incidentId<b.formation.incidentId?-1:1));
}
export function formationCandidates(r,events){
 const reports=events.filter(e=>e.groupId===r.groupId&&e.action==='REPORT');
 const cancelled=new Set(events.filter(e=>e.action==='CANCEL'&&e.groupId===r.groupId&&reports.some(x=>x.eventId===e.targetId&&x.training===e.training&&memberFor(r,x.actorDeviceId).memberId===memberFor(r,e.actorDeviceId).memberId)).map(e=>e.targetId));
 return quakePeriods(r,events).filter(p=>!p.closed&&!p.forms.length).flatMap(p=>{
  const evidence=[],members=new Set();for(const e of p.reports.filter(e=>e.action==='REPORT'&&!cancelled.has(e.eventId))){const member=memberFor(r,e.actorDeviceId).memberId;if(!members.has(member)){members.add(member);evidence.push(e);}}
  const needed=requiredReporters(r.members.length);if(evidence.length<needed)return [];
  return [{groupId:r.groupId,incidentId:evidence[0].eventId,firstReportedAt:evidence[0].operatedAt,training:p.training,reportIds:evidence.slice(0,needed).map(e=>e.eventId),...(r.revision?{rosterRevision:r.revision}:{})}];
 });
}
export function validateQuakeCommand(r,events,input){
 const e=validateQuake(r,input),states=quakeStates(r,events);
 if(e.action==='CANCEL'){
  const report=events.find(x=>x.eventId===e.targetId&&x.action==='REPORT'&&x.groupId===e.groupId);
  check(report&&report.training===e.training&&memberFor(r,report.actorDeviceId).memberId===memberFor(r,e.actorDeviceId).memberId,'自分の報告だけ取り消せます');
  check(!quakePeriods(r,events).some(p=>p.forms.length&&p.reports.some(x=>x.eventId===report.eventId)),'成立した報告は取り消せません');
 }else if(e.action==='FORM')check(formationCandidates(r,events).some(f=>sameRecord(f,proof(r,events,e))),'成立に必要な報告がありません');
 else if(e.action==='ADMIN_START')check(!states.some(s=>!s.formation.training&&!s.closed),'開始中の地震を終了してから開始してください');
 else if(e.action==='TRAINING_START')check(!states.some(s=>s.formation.training&&!s.closed),'開始中の訓練を終了してから新しい訓練を始めてください');
 else if(e.action==='END')check(states.some(s=>s.incidentIds.includes(e.targetId)&&e.endedIncidentIds.every(id=>s.incidentIds.includes(id))&&s.formation.training===e.training&&!s.closed),'開始中の災害を指定してください');
 return e;
}
const ordered=value=>Array.isArray(value)?value.map(ordered):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(k=>[k,ordered(value[k])])):value;
export const sameRecord=(a,b)=>JSON.stringify(ordered(a))===JSON.stringify(ordered(b));
export function validateResponse(r,input){
 const e={replaces:[],...input},c=e.change;memberFor(r,e.actorDeviceId);
 check(new TextEncoder().encode(JSON.stringify(e)).length<=22000,'記録が通信可能なサイズを超えています。本文または訂正元の数を減らしてください');
 check(e.groupId===r.groupId&&uuid.test(e.eventId)&&integer(e.sequence)&&e.sequence>0&&integer(e.operatedAt)&&c);
 check(Array.isArray(e.replaces)&&new Set(e.replaces).size===e.replaces.length&&e.replaces.every(x=>uuid.test(x)&&x!==e.eventId));
 check(c.type==='AVAILABILITY'?e.incidentId===null:uuid.test(e.incidentId));
 if(c.target)check(['PERSON','REPORT'].includes(c.target.kind)&&uuid.test(c.target.id));
 switch(c.type){
  case 'REPORT':{
   check(uuid.test(c.reportId)&&nonempty(c.category)&&nonempty(c.description)&&Number.isFinite(c.latitude)&&Math.abs(c.latitude)<=85&&Number.isFinite(c.longitude)&&Math.abs(c.longitude)<=180);
   // Do not add absent defaults to immutable received records.
   const ids=c.regionIds===undefined?[]:c.regionIds,cancelled=c.cancelled===undefined?false:c.cancelled,reason=c.cancelReason===undefined?'':c.cancelReason;
   check(Array.isArray(ids)&&new Set(ids).size===ids.length&&ids.every(id=>typeof id==='string'&&/^[A-Za-z0-9_-]{1,80}$/.test(id)),'地域IDを確認してください');
   check(typeof cancelled==='boolean'&&typeof reason==='string'&&(!cancelled||nonempty(reason)),'取消理由を入力してください');break;
  }
  case 'ASSIGNMENT':check(c.target&&r.members.some(m=>m.memberId===c.memberId)&&typeof c.active==='boolean'&&(c.memberId===memberFor(r,e.actorDeviceId).memberId||['ADMIN','LEADER'].includes(memberFor(r,e.actorDeviceId).role)));break;
  case 'PROGRESS':check(c.target&&['UNASSIGNED','PLANNED','IN_PROGRESS','DONE'].includes(c.status)&&typeof c.result==='string'&&(c.status!=='DONE'||nonempty(c.result)));break;
  case 'PRIORITY':check(c.target&&['UNSET','LOW','MEDIUM','HIGH'].includes(c.urgency)&&typeof c.reason==='string'&&(c.urgency==='UNSET'||nonempty(c.reason)));break;
  case 'INJURY':check(c.target?.kind==='PERSON'&&['UNKNOWN','INJURED','UNINJURED'].includes(c.state));break;
  case 'AVAILABILITY':check(['UNSET','AVAILABLE','WORKING','UNAVAILABLE'].includes(c.state));break;
  case 'MATERIAL':
   check(c.target&&uuid.test(c.requestId)&&nonempty(c.name)&&nonempty(c.unit)&&Number.isFinite(c.needed)&&c.needed>=0&&Number.isFinite(c.arranged)&&c.arranged>=0);
   check(typeof (c.cancelled??false)==='boolean'&&typeof (c.cancelReason??'')==='string'&&(!c.cancelled||nonempty(c.cancelReason)),'取消理由を入力してください');
   check(c.cancelled!==null&&c.cancelReason!==null,'取消の形式を確認してください');break;
  default:throw Error('対応記録の種別が不正です');
 }return e;
}
export function responseKey(r,e){
 const c=e.change,t=c.target;const value=c.type==='REPORT'?`report:${c.reportId}`:c.type==='MATERIAL'?`material:${c.requestId}`:c.type==='AVAILABILITY'?`availability:${memberFor(r,e.actorDeviceId).memberId}`:`${c.type.toLowerCase()}:${t.kind}:${t.id}${c.type==='ASSIGNMENT'?':'+c.memberId:''}`;
 return `${e.groupId}:${e.incidentId??''}:${value}`;
}
const permissionCheck=(ok,message)=>{if(!ok){const error=Error(message);error.code='response_forbidden';throw error;}};
const isCoordinator=member=>['ADMIN','LEADER'].includes(member.role);
function originalResponse(r,events,type,id,context){
 return events.find(e=>e.eventId===id&&e.groupId===r.groupId&&e.groupId===context.groupId&&e.incidentId===context.incidentId&&e.change.type===type&&(type==='REPORT'?e.change.reportId:e.change.requestId)===id&&!e.replaces?.length);
}
const ownsResponse=(r,member,event)=>!!event&&memberFor(r,event.actorDeviceId).memberId===member.memberId;
/** Use complete history with incident aliases already canonicalized, never heads alone. */
export function responseTargetPermissions(r,events,actorDeviceId,target,context){
 const member=memberFor(r,actorDeviceId),coordinator=isCoordinator(member);
 const original=target?.kind==='REPORT'?originalResponse(r,events,'REPORT',target.id,context):null;
 const canEdit=target?.kind==='REPORT'?!!original&&(coordinator||ownsResponse(r,member,original)):target?.kind==='PERSON';
 return {canEdit:!!canEdit,canCancel:target?.kind==='REPORT'&&!!canEdit,canChangePriority:canTriage(member)};
}
/** Record ownership is bound to the creation event, including after administrator corrections. */
export function responsePermissions(r,events,actorDeviceId,event){
 const member=memberFor(r,actorDeviceId),coordinator=isCoordinator(member),c=event.change;
 if(c.type==='REPORT')return responseTargetPermissions(r,events,actorDeviceId,{kind:'REPORT',id:c.reportId},event);
 if(c.type==='MATERIAL'){
  const original=originalResponse(r,events,'MATERIAL',c.requestId,event),report=original?.change.target.kind==='REPORT'?originalResponse(r,events,'REPORT',original.change.target.id,event):null;
  const canEdit=!!original&&(coordinator||ownsResponse(r,member,original)||ownsResponse(r,member,report));
  return {canEdit,canCancel:canEdit,canChangePriority:canTriage(member)};
 }
 return responseTargetPermissions(r,events,actorDeviceId,c.target,event);
}
function authorize(r,history,e){
 validateResponse(r,e);const member=memberFor(r,e.actorDeviceId),coordinator=['ADMIN','LEADER'].includes(member.role),c=e.change;
 const parents=e.replaces.map(id=>history.find(x=>x.eventId===id&&x.groupId===e.groupId));check(parents.every(p=>p&&responseKey(r,p)===responseKey(r,e)),'訂正元がありません');
 function creator(id){if(e.eventId===id){check(!e.replaces.length);return member;}const root=history.find(x=>x.eventId===id&&x.groupId===e.groupId&&x.incidentId===e.incidentId);check(root&&responseKey(r,root)===responseKey(r,e)&&e.replaces.length);return memberFor(r,root.actorDeviceId);}
 if(c.type==='REPORT')permissionCheck(creator(c.reportId).memberId===member.memberId||coordinator,'通報を訂正する権限がありません');
 else if(c.type==='ASSIGNMENT')permissionCheck(c.memberId===member.memberId||coordinator,'他の人の担当は班長・管理者が調整します');
 else if(c.type==='PRIORITY')permissionCheck(couldTriage(r,e.actorDeviceId),'負傷登録・緊急度設定の権限がありません');
 else if(c.type==='MATERIAL'){
  creator(c.requestId);
  if(e.eventId!==c.requestId)permissionCheck(responsePermissions(r,history,e.actorDeviceId,e).canEdit,'資材を訂正する権限がありません');
  const original=originalResponse(r,history,'MATERIAL',c.requestId,e)?.change;if(original)check(sameRecord(original.target,c.target),'資材の対象は変更できません');
 }
 else if(c.type==='AVAILABILITY')check(parents.every(p=>memberFor(r,p.actorDeviceId).memberId===member.memberId));
 if(c.target?.kind==='REPORT'&&c.type!=='PRIORITY'&&(c.type!=='MATERIAL'||e.eventId===c.requestId)){
  check(originalResponse(r,history,'REPORT',c.target.id,e),'対象の通報が見つかりません');
  permissionCheck(responseTargetPermissions(r,history,e.actorDeviceId,c.target,e).canEdit,'この通報は閲覧のみです');
 }
}
export function responseHeads(r,events){
 const waiting=events.filter(e=>e.groupId===r.groupId).sort((a,b)=>a.eventId<b.eventId?-1:1),accepted=[];let changed=true;
 while(changed){changed=false;for(let i=0;i<waiting.length;){const e=waiting[i];try{authorize(r,accepted,e);}catch{i++;continue;}accepted.push(e);waiting.splice(i,1);changed=true;}}
 const replaced=new Set(accepted.flatMap(e=>e.replaces));return accepted.filter(e=>!replaced.has(e.eventId)).sort((a,b)=>a.eventId<b.eventId?-1:1);
}
export {authorize as validateResponseAuthorization};
export function validateResponseCommand(r,events,e){requireTriagePermission(r,e);authorize(r,events,e);const heads=new Set(responseHeads(r,events).map(x=>x.eventId));check(e.replaces.every(id=>heads.has(id)),'記録が更新されています');return e;}
export function startWorkChanges(roster,heads,actorDeviceId,target){
 const member=memberFor(roster,actorDeviceId);check(target&&['PERSON','REPORT'].includes(target.kind)&&uuid.test(target.id));
 const relevant=heads.filter(e=>sameRecord(e.change.target,target));
 const assignments=relevant.filter(e=>e.change.type==='ASSIGNMENT'&&e.change.memberId===member.memberId),progress=relevant.filter(e=>e.change.type==='PROGRESS'),changes=[];
 if(assignments.length!==1||!assignments[0].change.active)changes.push({change:{type:'ASSIGNMENT',target,memberId:member.memberId,active:true},replaces:assignments.map(e=>e.eventId)});
 if(!progress.length||(progress.length===1&&['UNASSIGNED','PLANNED'].includes(progress[0].change.status)))changes.push({change:{type:'PROGRESS',target,status:'IN_PROGRESS',result:''},replaces:progress.map(e=>e.eventId)});
 return changes;
}
export function managedIncidentOptions(journal,roster,quakes){
 if(!roster)return incidentOptions(journal);
 const states=quakeStates(roster,quakes),originals=incidentOptions(journal),ids=new Set(states.flatMap(s=>s.incidentIds));
 const views=states.flatMap(state=>{
  const related=originals.filter(v=>state.incidentIds.includes(v.start.eventId)),canonical=related.find(v=>v.start.eventId===state.formation.incidentId);if(!canonical)return [];
  const answers={};for(const answer of related.flatMap(v=>Object.values(v.answers)).sort((a,b)=>a.createdAt-b.createdAt||a.sequence-b.sequence||(a.eventId<b.eventId?-1:1)))answers[answer.authorId]=answer;
  return [{...canonical,start:{...canonical.start,createdAt:state.formation.firstReportedAt},closed:state.closed,answers,care:related.flatMap(v=>v.care)}];
 });return [...views,...originals.filter(v=>!ids.has(v.start.eventId)).map(v=>({...v,closed:true}))].sort((a,b)=>b.start.createdAt-a.start.createdAt||(a.start.eventId<b.start.eventId?-1:1));
}
export const canonicalIncidentId=(roster,quakes,id)=>roster?quakeStates(roster,quakes).find(s=>s.incidentIds.includes(id))?.formation.incidentId??id:id;
