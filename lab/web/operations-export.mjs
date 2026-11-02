import {validateRoster,canonicalIncidentId,managedIncidentOptions,responseHeads} from './managed-records.mjs';

const unique=values=>[...new Set(values)].sort();
const compare=(a,b)=>a<b?-1:a>b?1:0;
const targetKey=t=>`${t.kind}:${t.id}`;

/** Same approved columns and visibility rules as the Android exporter. */
export function operationRows(roster,incidentId,residents,journal,quakes,responses,regions={}, {language='ja'}={}){
 if(!['ja','en'].includes(language))throw Error('Unsupported CSV language');
 const en=language==='en',unset=en?'Unset':'未設定',unassigned=en?'Unassigned':'未対応';
 validateRoster(roster);
 quakes=quakes.filter(e=>e.groupId===roster.groupId);
 const selected=canonicalIncidentId(roster,quakes,incidentId);
 const view=managedIncidentOptions(journal.filter(e=>e.groupId===roster.groupId),roster,quakes).find(v=>v.start.eventId===selected);
 if(!view)throw Error('出力する災害を選んでください');
 const heads=responseHeads(roster,responses.filter(e=>e.groupId===roster.groupId).map(e=>({...e,incidentId:canonicalIncidentId(roster,quakes,e.incidentId)}))).filter(e=>e.incidentId===selected);
 function row(target,kind,name,safety,updatedAt){
  const records=heads.filter(e=>targetKey(e.change.type==='REPORT'?{kind:'REPORT',id:e.change.reportId}:e.change.target||{})===targetKey(target));
  const changes=records.map(e=>e.change),progress=changes.filter(c=>c.type==='PROGRESS');
  const statuses=unique(progress.map(c=>(en?{UNASSIGNED:'Unassigned',PLANNED:'Planned',IN_PROGRESS:'In progress',DONE:'Done'}:{UNASSIGNED:'未対応',PLANNED:'対応予定',IN_PROGRESS:'対応中',DONE:'対応済み'})[c.status]));
  const priorities=unique(changes.filter(c=>c.type==='PRIORITY').map(c=>(en?{UNSET:'Unset',LOW:'Low',MEDIUM:'Medium',HIGH:'High'}:{UNSET:'未設定',LOW:'低',MEDIUM:'中',HIGH:'高'})[c.urgency]));
  const assignees=unique(changes.filter(c=>c.type==='ASSIGNMENT'&&c.active).map(c=>c.memberId)).map(id=>roster.members.find(m=>m.memberId===id).activityName);
  const materials=changes.filter(c=>c.type==='MATERIAL').sort((a,b)=>compare(a.requestId,b.requestId)||compare(a.name,b.name)||a.needed-b.needed||a.arranged-b.arranged).map(c=>en?`${c.name}: needed ${c.needed}${c.unit}; arranged ${c.arranged}${c.unit}; shortfall ${Math.max(0,c.needed-c.arranged)}${c.unit}`:`${c.name}：必要${c.needed}${c.unit}・手配済み${c.arranged}${c.unit}・不足${Math.max(0,c.needed-c.arranged)}${c.unit}`);
  const reportRegions=changes.filter(c=>c.type==='REPORT'&&!c.cancelled).flatMap(c=>c.regionIds||[]);
  return {region:unique(regions[targetKey(target)]??reportRegions).join(' / ')||unset,targetId:target.id,kind,name,
   status:[safety,statuses.join(' / ')||unassigned].filter(Boolean).join(en?' / ':'・'),urgency:priorities.join(' / ')||unset,
   assignees:assignees.join(' / '),result:unique(progress.map(c=>c.result).filter(s=>s.trim())).join(' / '),materials:materials.join(' / '),
   updatedAt:records.reduce((at,e)=>Math.max(at,e.operatedAt),updatedAt)};
 }
 const latest=new Map();
 for(const p of residents.filter(p=>p.groupId===roster.groupId))if(!latest.has(p.originId)||latest.get(p.originId).revision<p.revision)latest.set(p.originId,p);
 const people=[...latest.values()].map(person=>{
  const answer=view.answers[person.originId],care=view.care.filter(e=>e.subjectId===person.originId);
  const byId=new Map(care.map(e=>[e.eventId,e]));
  const replaced=new Set(care.filter(e=>{const old=byId.get(e.supersedes);return old&&old.groupId===e.groupId&&old.authorId===e.authorId&&old.subjectId===e.subjectId;}).map(e=>e.supersedes));
  const current=care.filter(e=>!replaced.has(e.eventId));
  const rescued=current.some(e=>e.action==='RESCUED'&&e.createdAt>=(answer?.createdAt??0));
  const state=rescued?'RESCUED':answer?.safety==='SAFE'?'SAFE':answer?.safety==='SOS'?'SOS':'UNREPORTED';
  const safety=(en?{RESCUED:'Rescued',SAFE:'Safe',SOS:'SOS',UNREPORTED:'No response'}:{RESCUED:'救助済み',SAFE:'無事',SOS:'SOS',UNREPORTED:'応答なし'})[state];
  return row({kind:'PERSON',id:person.originId},en?'Resident':'住民',['SOS','UNREPORTED'].includes(state)?person.name:'',safety,
   current.reduce((at,e)=>Math.max(at,e.createdAt),Math.max(person.updatedAt,answer?.createdAt??view.start.createdAt)));
 });
 const reports=new Map();
 for(const e of heads.filter(e=>e.change.type==='REPORT'&&!e.change.cancelled)){const records=reports.get(e.change.reportId)||[];records.push(e);reports.set(e.change.reportId,records);}
 return [...people,...[...reports].map(([id,records])=>row({kind:'REPORT',id},`${en?'Report: ':'通報：'}${unique(records.map(e=>e.change.category)).join(' / ')}`,'','',records.reduce((at,e)=>Math.max(at,e.operatedAt),0)))].sort((a,b)=>compare(a.kind,b.kind)||compare(a.targetId,b.targetId));
}

function localTimestamp(timestamp){
 const d=new Date(timestamp),pad=n=>String(n).padStart(2,'0'),offset=-d.getTimezoneOffset();
 return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}${offset===0?'Z':`${offset<0?'-':'+'}${pad(Math.floor(Math.abs(offset)/60))}:${pad(Math.abs(offset)%60)}`}`;
}
export function operationCsv(rows,{language='ja'}={}){
 if(!['ja','en'].includes(language))throw Error('Unsupported CSV language');
 const header=language==='en'?['Region','Target ID','Type','Name','Status','Urgency','Assignee activity names','Result','Materials and shortfall','Updated at']:['地域','対象ID','種別','氏名','状況','緊急度','担当の活動名','対応結果','必要資材と不足数','更新日時'];
 const cell=value=>{const text=/^[=+@-]/.test(value.trimStart())||/^[\t\r\n]/.test(value)?`'${value}`:value;return `"${text.replaceAll('"','""')}"`;};
 return '\uFEFF'+[header,...rows.map(r=>[r.region,r.targetId,r.kind,r.name,r.status,r.urgency,r.assignees,r.result,r.materials,localTimestamp(r.updatedAt)])].map(line=>line.map(cell).join(',')).join('\r\n')+'\r\n';
}
