const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const communityLabels=en=>en?{title:'Neighborhood overview',total:'People',fictional:'Fictional residents',safe:'Safe',sos:'SOS',rescued:'Rescued',unreported:'No response',injured:'Injured',injuryUnknown:'Injury unknown',reports:'Reports',materials:'Material shortfalls',none:'None recorded',issue:'Issue report',saved:'Issued reports',unknown:'Unassigned',injury:'Injury information',UNINJURED:'Not injured',INJURED:'Injured',UNKNOWN:'Unknown',demo:'Demo neighborhoods',conflict:'Concurrent updates — largest shortfall shown',source:'Source record IDs'}:{title:'町内会の状況',total:'人数',fictional:'架空住民',safe:'無事',sos:'SOS',rescued:'救助済み',unreported:'応答なし',injured:'負傷者',injuryUnknown:'負傷状況不明',reports:'通報',materials:'不足資材',none:'記録なし',issue:'レポートを発行',saved:'発行済みレポート',unknown:'未分類',injury:'負傷情報',UNINJURED:'負傷なし',INJURED:'負傷あり',UNKNOWN:'不明',demo:'デモ用の町内会',conflict:'同時更新あり・最大の不足数を表示',source:'集計元の記録ID'};
export const demoCommunityFor=lon=>!Number.isFinite(lon)?null:lon<135.760?'demo-west':lon<135.7615?'demo-central':'demo-east';
const activeReports=row=>(row.reports||[]).filter(e=>e.change.type==='REPORT'&&!e.change.cancelled);
/** Classify only live incidents; response work on safe/rescued people is not a new SOS. */
export function areaCaseCategory(row){
 if(row.target?.kind==='PERSON')return row.statusIcon==='SOS'?'SOS':null;
 if(row.target?.kind!=='REPORT')return null;
 const categories=[...new Set(activeReports(row).map(e=>({ROAD_BLOCKED:'道路閉塞',BUILDING_COLLAPSE:'建物倒壊'}[e.change.category]||e.change.category)))];
 if(!categories.length)return null;
 return categories.length===1&&['道路閉塞','建物倒壊'].includes(categories[0])?categories[0]:'その他';
}
/** Match report regions to configured communities; never expose a person's identity here. */
export function areaCaseCommunityIds(row,{people=[],communities=[]}={}){
 const known=new Set(communities.map(c=>c.id)),fallback=communities.length===1?communities[0].id:'unknown';
 const ids=row.target?.kind==='PERSON'?[people.find(p=>p.id===row.target.id)?.communityId]:activeReports(row).flatMap(e=>e.change.regionIds||[]);
 const regions=[...new Set(ids.filter(id=>known.has(id)))];
 return regions.length?regions:[fallback];
}
/** Aggregate cases contribute counts only. `cases` alone grants detail links, including when empty. */
export function areaIncidentSummary(input){
 const {people=[],heads=[]}=input;
 const source=input.cases??[
  ...people.filter(p=>p.disclosed===true&&p.status==='SOS').map(p=>({target:{kind:'PERSON',id:p.id},statusIcon:p.status})),
  ...heads.filter(e=>e.change.type==='REPORT').map(e=>({target:{kind:'REPORT',id:e.change.reportId},reports:[e]}))
 ];
 const aggregate=countAreaCases(input.aggregateCases??source,input),accessible=countAreaCases(source,input);
 return {...aggregate,accessibleCounts:accessible.counts,accessibleRows:accessible.rows};
}
function countAreaCases(source,input){
 const counts=()=>({total:0,sos:0,blockedRoad:0,buildingCollapse:0,other:0});
 const rows=new Map((input.communities||[]).map(c=>[c.id,{...c,...counts()}])),total=counts();
 const unique=new Map();for(const row of source){const key=row.target?.kind+':'+row.target?.id,prior=unique.get(key);unique.set(key,prior&&row.target?.kind==='REPORT'?{...prior,reports:[...prior.reports||[],...row.reports||[]]}:row);}
 for(const row of unique.values()){
  const category=areaCaseCategory(row);if(!category)continue;
  const key=({SOS:'sos','道路閉塞':'blockedRoad','建物倒壊':'buildingCollapse','その他':'other'})[category];total.total++;total[key]++;
  for(const id of areaCaseCommunityIds(row,input)){if(!rows.has(id))rows.set(id,{id,nameJa:'未分類',nameEn:'Unassigned',...counts()});const region=rows.get(id);region.total++;region[key]++;}
 }
 return {counts:total,rows:[...rows.values()]};
}
export function communityInjuryPeople({people=[],visiblePersonIds}){const allowed=visiblePersonIds==null?null:new Set(visiblePersonIds);return allowed?people.filter(p=>allowed.has(p.id)):people;}
export function injuryFor(heads,id){const values=[...new Set(heads.filter(e=>e.change.type==='INJURY'&&e.change.target?.kind==='PERSON'&&e.change.target.id===id).map(e=>e.change.state))];return values.length===1?values[0]:'UNKNOWN';}
/** Map summaries contain counts only; other neighborhoods never become person links. */
export function communityMapOverview(input,ownId){
 const own=input.people.find(p=>p.id===ownId)?.communityId;
 const activeHeads=input.heads.filter(e=>{if(e.change.type!=='MATERIAL')return true;const progress=input.heads.filter(p=>p.change.type==='PROGRESS'&&p.change.target?.kind===e.change.target?.kind&&p.change.target?.id===e.change.target?.id);return !progress.length||progress.some(p=>p.change.status!=='DONE');});
 return communityOverview({...input,heads:activeHeads}).filter(r=>r.id!==own).flatMap(r=>{
  const points=input.people.filter(p=>p.communityId===r.id&&Number.isFinite(p.latitude)&&Number.isFinite(p.longitude));
  return points.length?[{...r,latitude:points.reduce((n,p)=>n+p.latitude,0)/points.length,longitude:points.reduce((n,p)=>n+p.longitude,0)/points.length}]:[];
 });
}
/** Inputs are current heads only. Never infer injury from SOS or rescue. */
export function communityOverview({people=[],heads=[],communities=[]}){
 const unique=[...new Map(people.map(p=>[p.id,p])).values()],known=new Set(communities.map(c=>c.id));
 const fallback=communities.length===1?communities[0].id:'unknown';const region=id=>known.has(id)?id:fallback;
 const rows=new Map(communities.map(c=>[c.id,{...c,total:0,fictional:0,safe:0,sos:0,rescued:0,unreported:0,injured:0,injuryUnknown:0,reports:0,urgentOpen:0,materials:[]}]));
 const get=id=>{id=region(id);if(!rows.has(id))rows.set(id,{id,nameJa:'未分類',nameEn:'Unassigned',total:0,fictional:0,safe:0,sos:0,rescued:0,unreported:0,injured:0,injuryUnknown:0,reports:0,urgentOpen:0,materials:[]});return rows.get(id);};
 const personRegions=new Map();for(const p of unique){const r=get(p.communityId);personRegions.set(p.id,r.id);r.total++;if(p.fictional)r.fictional++;r[({SAFE:'safe',SOS:'sos',RESCUED:'rescued'})[p.status]||'unreported']++;const injury=injuryFor(heads,p.id);if(injury==='INJURED')r.injured++;if(injury==='UNKNOWN')r.injuryUnknown++;}
 const reports=new Map();for(const e of heads.filter(e=>e.change.type==='REPORT'&&!e.change.cancelled)){const c=e.change;const ids=reports.get(c.reportId)||new Set();ids.add(region(c.regionIds?.[0]));reports.set(c.reportId,ids);}
 const reportRegions=new Map();for(const [id,ids]of reports){const r=get(ids.size===1?[...ids][0]:null);r.reports++;reportRegions.set(id,r.id);const related=heads.filter(e=>e.change.target?.kind==='REPORT'&&e.change.target.id===id),progress=related.filter(e=>e.change.type==='PROGRESS');if(related.some(e=>e.change.type==='PRIORITY'&&e.change.urgency==='HIGH')&&(!progress.length||progress.some(e=>e.change.status!=='DONE')))r.urgentOpen++;}
 const requests=new Map();for(const e of heads.filter(e=>e.change.type==='MATERIAL')){const c=e.change,key=c.requestId;const list=requests.get(key)||[];list.push(c);requests.set(key,list);}
 for(const revisions of requests.values()){const c=revisions[0],target=c.target;const regionId=target.kind==='PERSON'?personRegions.get(target.id):reportRegions.get(target.id);if(!regionId)continue;const r=get(regionId),amount=Math.max(...revisions.map(x=>Math.max(0,x.needed-x.arranged)));if(amount===0)continue;const labels=[...new Set(revisions.map(x=>x.name+'\u0000'+x.unit))];const name=labels.length===1?c.name:revisions.map(x=>x.name).filter((x,i,a)=>a.indexOf(x)===i).join(' / '),unit=labels.length===1?c.unit:'?';let m=r.materials.find(x=>x.name===name&&x.unit===unit);if(!m){m={name,unit,shortfall:0,conflict:false};r.materials.push(m);}m.shortfall+=amount;m.conflict||=revisions.length>1;}
 return [...rows.values()];
}
export function issueCommunityReport(input,language='ja',issuedAt=Date.now(),id=crypto.randomUUID()) {return structuredClone({version:1,id,issuedAt,language,groupId:input.groupId,groupName:input.groupName,incidentId:input.incidentId,demo:!!input.demo,rows:communityOverview(input),people:input.people.map(p=>input.visiblePersonIds!==undefined&&!new Set(input.visiblePersonIds).has(p.id)?{id:p.id,communityId:p.communityId,status:p.status,fictional:p.fictional}:p),sourceIds:[...new Set([...(input.sourceIds||[]),...input.heads.map(e=>e.eventId).filter(Boolean)])].sort()});}
export function communityReportHtml(report){const en=report.language==='en',l=communityLabels(en),metrics=['total','safe','sos','rescued','unreported','injured','injuryUnknown','reports'];return `<!doctype html><html lang="${en?'en':'ja'}"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${l.title}</title><style>body{font:16px system-ui;margin:24px;color:#172b35;line-height:1.6}table{border-collapse:collapse;width:100%}th,td{border:1px solid #c6d1d6;padding:8px;text-align:left}h1{font-size:24px}section{break-inside:avoid;margin:24px 0}small{color:#52616a}pre{white-space:pre-wrap;overflow-wrap:anywhere}@media print{body{margin:0}}</style><h1>${l.title}</h1><p>${esc(report.groupName)} · ${esc(new Date(report.issuedAt).toISOString())}</p>${report.demo?`<p>${l.demo}</p>`:''}${report.rows.map(r=>`<section><h2>${esc(en?r.nameEn:r.nameJa)}</h2><table>${metrics.map(k=>`<tr><th>${l[k]}</th><td>${r[k]}</td></tr>`).join('')}</table><h3>${l.materials}</h3>${r.materials.map(m=>`<p>${esc(m.name)}: ${m.shortfall} ${esc(m.unit)}${m.conflict?` (${l.conflict})`:''}</p>`).join('')||`<p>${l.none}</p>`}</section>`).join('')}<details><summary>${l.source}</summary><pre>${esc(report.sourceIds.join('\n'))}</pre></details><small>${esc(report.id)} / ${esc(report.incidentId)}</small></html>`;}
