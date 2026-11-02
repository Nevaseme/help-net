import {sameRecord,quakeStates} from './managed-records.mjs';
import {saveVenue} from './venue-demo.mjs';
import {saveMunicipality} from './municipalities.mjs';

const collections=['residents','quakes','incidents','events','places','positions','memos','responses','deviceRecords'];
const author=(table,r)=>['residents','positions'].includes(table)?r.originId:['quakes','responses'].includes(table)?r.actorDeviceId:r.authorId;
const keyOf=r=>r.eventId??r.answerId??r.originId;
const stable=v=>Array.isArray(v)?v.map(stable):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,stable(v[k])])):v;
const token=()=>Array.from(crypto.getRandomValues(new Uint8Array(32)),x=>x.toString(16).padStart(2,'0')).join('');
async function digest(value){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify(stable(value))))),x=>x.toString(16).padStart(2,'0')).join('');}
export class CloudApiError extends Error {constructor(status,body){super(body?.error?.message||`共有サーバーに接続できません (${status})`);this.status=status;this.code=body?.error?.code;}}

/** Local IndexedDB is authoritative offline. Cursor and upload receipts follow durable merges. */
export class CloudClient {
 constructor(store,deviceId,{base='',fetcher=globalThis.fetch.bind(globalThis)}={}){Object.assign(this,{store,deviceId,base,fetcher});this.running=null;}
 async request(path,{session,body}={}){
  const timeout=new AbortController();const timer=setTimeout(()=>timeout.abort(),20000);
  try {
   const response=await this.fetcher(`${this.base}/api/v1${path}`,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json','X-PlanD-Records-Version':'5',...(session?{Authorization:`Bearer ${session.token}`}:{})},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',signal:timeout.signal});
   const data=await response.json();if(!response.ok)throw new CloudApiError(response.status,data);return data;
  }finally{clearTimeout(timer);}
 }
 session(group){return this.store.read('meta','cloud-session:'+group);}
 checkVenue(groupName,password){return this.request('/venue/check',{body:{groupName,password}});}
 joinVenue(input){return this.register('venue',input);}
 async endVenue(group){
  const session=await this.requireSession(group),venue=await this.store.read('meta','venue:'+group);if(!venue||venue.closed)return;
  const key='venue-end:'+venue.sessionId;let record;
  await this.store.update('meta',key,old=>{record=old??{groupId:group,eventId:crypto.randomUUID(),actorDeviceId:this.deviceId,operatedAt:Date.now(),action:'END',training:true,targetId:venue.sessionId};return old?undefined:record;});
  await this.request(`/groups/${group}/records`,{session,body:{records:[{collection:'quakes',record}]}});await this.sync(group);await this.store.update('meta',key,()=>null);
 }
 async access(group,action,memberId){const result=await this.request(`/groups/${group}/access`,{session:await this.requireSession(group),body:{action,memberId}});if(action==='revoke'){await this.store.importCloudRoster(result,this.deviceId);await saveVenue(this.store,result);}return result;}
 async startVenue(group,password,options={}){
  const key='venue-start:'+group;let pending;
  await this.store.update('meta',key,old=>{pending=old??{requestId:crypto.randomUUID(),areaId:'kyoto-gosho-south',positionMode:options.positionMode??'KYOTO',...(options.privateTraining?{privateTraining:true,title:options.title,area:options.area}:{})};return old?undefined:pending;});
  const body=await this.request(`/groups/${group}/venue`,{session:await this.requireSession(group),body:{...pending,password}});
  await this.store.importCloudRoster(body,this.deviceId);await saveVenue(this.store,body);await saveMunicipality(this.store,body);
  await this.store.update('meta','venue-onboarding:'+group,()=>body.venue.sessionId);
  await this.store.update('meta',key,()=>null);return body.venue;
 }
 async register(kind,input){
  const pendingKey='cloud-registration:'+kind;let pending;
  const durableInput=kind==='venue'?{groupName:input.groupName,activityName:input.activityName}:input;
  const candidate={input:durableInput,sourceGroup:await this.store.read('meta','group'),body:{...durableInput,deviceId:this.deviceId,registrationToken:token(),...(kind==='create'?{requestId:crypto.randomUUID()}:{})}};
  // Tabs share one IndexedDB transaction when claiming a pending registration.
  await this.store.update('meta',pendingKey,old=>{
   if(old){if(!sameRecord(old.input,durableInput))throw Error('前回の作成・参加を同じ入力で再試行してください。記録は端末に残っています。');pending=old;return undefined;}
   pending=candidate;return candidate;
  });
  const clearPending=()=>this.store.update('meta',pendingKey,old=>old?.body?.registrationToken===pending.body.registrationToken?null:undefined);
  const path=kind==='create'?'/groups':kind==='recover'?'/venue/recover':kind==='venue'?'/venue/join':`/groups/${encodeURIComponent(input.groupId)}/join`;
  let result;
  try{result=await this.request(path,{body:kind==='venue'?{...pending.body,password:input.password}:pending.body});}
  catch(error){
   // A definite rejection created no membership; allow a corrected invitation.
   // Network/5xx failures retain the request because the server may have committed it.
   if(error instanceof CloudApiError&&[400,403,404,409].includes(error.status))await clearPending();
   throw error;
  }
  if(result.deviceId!==this.deviceId||!result.groupId)throw Error('参加する端末を確認できません');
  const session={groupId:result.groupId,groupName:result.groupName,deviceId:this.deviceId,memberId:result.memberId,activityName:result.activityName??input.activityName,role:result.role,token:result.token??pending.body.registrationToken};
  // Persist credential before further work, so a later roster/cursor failure never loses membership.
  await this.store.update('meta','cloud-session:'+session.groupId,()=>session);
  await this.store.importCloudRoster(result,this.deviceId);
  await saveVenue(this.store,result);await saveMunicipality(this.store,result);
  if(kind==='venue'||kind==='recover')await this.store.update('meta','venue-onboarding:'+session.groupId,()=>result.venue.sessionId);
  // Joining carries only this device's profile; the new group starts without a safety answer.
  if(!['venue','recover'].includes(kind)&&pending.sourceGroup&&pending.sourceGroup!==session.groupId&&!await this.store.get(session.groupId,this.deviceId)){
   const own=await this.store.get(pending.sourceGroup,this.deviceId);
   if(own)await this.store.merge({...own,groupId:session.groupId,revision:1,updatedAt:Date.now(),safety:'UNREPORTED'});
  }
  await this.store.update('meta','group',()=>session.groupId);
  await clearPending();
  return session;
 }
 async setMunicipality(group,code){const body=await this.request(`/groups/${group}/municipality`,{session:await this.requireSession(group),body:{code}});await saveMunicipality(this.store,body);return body.municipality;}
 async invitation(group){return this.request(`/groups/${encodeURIComponent(group)}/invitations`,{session:await this.requireSession(group),body:{}});}
 async requireSession(group){const session=await this.session(group);if(!session)throw Error('グループを作成するか、招待コードで参加してください');return session;}
 async roster(session){const body=await this.request(`/groups/${session.groupId}/roster`,{session});const roster=await this.store.importCloudRoster(body,this.deviceId);await saveVenue(this.store,body);await saveMunicipality(this.store,body);return roster;}
 async merge(table,record){
  const s=this.store;
  switch(table){
   case 'residents': {const old=await s.get(record.groupId,record.originId);if(old?.revision===record.revision&&!sameRecord(old,record))throw Error('同じ版の登録情報が一致しません');return s.merge(record);}
   case 'quakes':return s.mergeQuake(record,this.deviceId);
   case 'incidents':return s.mergeIncident(record);
   case 'events':return s.mergeEvent(record);
   case 'places':return s.mergePlace(record);
   case 'positions':return s.savePosition(record);
   case 'memos':return s.mergeMemo(record);
   case 'responses':return s.mergeResponse(record);
   case 'deviceRecords':return s.mergeDeviceRecord(record);
   default:throw Error('対応していない共有記録です');
  }
 }
 async activeActual(group){
  const [roster,events]=await Promise.all([this.store.roster(group),this.store.quakeEvents(group)]);
  return roster?quakeStates(roster,events).filter(state=>!state.closed&&!state.formation.training):[];
 }
 async beginArrivalSelection(group){
  const previousActiveIds=(await this.activeActual(group)).flatMap(state=>state.incidentIds);let marker;
  // Retain the pre-merge baseline on replay: record persistence may have succeeded
  // before the selected incident could be saved. Tabs must not replace that baseline.
  await this.store.update('meta','cloud-arrival:'+group,old=>{marker=old??{id:crypto.randomUUID(),previousActiveIds};return old?undefined:marker;});
  return marker;
 }
 async finishArrivalSelection(group,marker){
  const active=await this.activeActual(group),known=new Set(marker.previousActiveIds);
  const arriving=active.findLast(state=>state.incidentIds.every(id=>!known.has(id)));
  if(arriving)await this.store.update('meta','incident:'+group,selected=>active.some(state=>state.incidentIds.includes(selected))?undefined:arriving.formation.incidentId);
  // Selection is durable before retiring its recovery marker or advancing the cursor.
  await this.store.update('meta','cloud-arrival:'+group,old=>old?.id===marker.id?null:undefined);
 }
 async pull(session){
  const cursorKey='cloud-cursor:'+session.groupId;let cursor=(await this.store.read('meta',cursorKey))??0,received=0;
  for(let page=0;page<200;page++){
   const body=await this.request(`/groups/${session.groupId}/records?after=${cursor}&limit=100`,{session});
   const current=await this.store.roster(session.groupId);
   if(!current||(current.revision??0)<body.rosterRevision)await this.roster(session);
   if(!Array.isArray(body.records)||!Number.isSafeInteger(body.nextCursor)||body.nextCursor<cursor||body.hasMore&&body.nextCursor===cursor)throw Error('共有記録の取得位置が不正です');
   const arrival=body.records.length?await this.beginArrivalSelection(session.groupId):await this.store.read('meta','cloud-arrival:'+session.groupId);
   for(const row of body.records){if(row.record?.groupId!==session.groupId||!Number.isSafeInteger(row.cursor)||row.cursor<=cursor||row.cursor>body.nextCursor)throw Error('別グループまたは不正な記録です');await this.merge(row.collection,row.record);const hash=await digest(row.record);await this.store.update('meta',`cloud-seen:${session.groupId}:${row.collection}:${keyOf(row.record)}:${row.record.revision??1}`,()=>hash);received++;}
   if(arrival)await this.finishArrivalSelection(session.groupId,arrival);
   await this.store.update('meta',cursorKey,()=>body.nextCursor);cursor=body.nextCursor;
   if(!body.hasMore)break;
  }return received;
 }
 sync(group){if(this.running)return this.runningGroup===group?this.running:this.running.catch(()=>{}).then(()=>this.sync(group));this.runningGroup=group;this.running=this.performSync(group).finally(()=>{this.running=null;});return this.running;}
 async performSync(group){
  const session=await this.requireSession(group);await this.roster(session);let received=await this.pull(session),sent=0,foreign=0,pending=0;const errors=[];
  const venue=await this.store.read('meta','venue:'+group),onboarding=await this.store.read('meta','venue-onboarding:'+group);
  if(venue&&!venue.closed&&venue.sessionId===onboarding&&(await this.store.quakeEvents(group)).some(e=>e.eventId===venue.incidentId)){
   await this.store.update('meta','incident:'+group,()=>venue.incidentId);await this.store.update('meta','venue-onboarding:'+group,()=>null);
  }
  const own=[];
  for(const table of collections){const rows=(await this.store.read(table)).filter(r=>r.groupId===group).sort((a,b)=>(a.operatedAt??a.createdAt??0)-(b.operatedAt??b.createdAt??0)||(a.sequence??0)-(b.sequence??0));for(const record of rows){
   // Managed START is materialized from proof on every client, never impersonated as an upload.
   if(table==='incidents'&&record.kind==='START')continue;
   if(table==='positions'&&venue&&!venue.closed&&venue.positionMode!=='ACTUAL'&&author(table,record)===this.deviceId)continue;
   if(author(table,record)!==this.deviceId){if(await this.store.read('meta',`cloud-seen:${group}:${table}:${keyOf(record)}:${record.revision??1}`)!==await digest(record))foreign++;continue;}
   const ackKey=`cloud-ack:${group}:${table}:${keyOf(record)}:${record.revision??1}`,hash=await digest(record);
   if(await this.store.read('meta',ackKey)!==hash)own.push({table,record,ackKey,hash});
  }}
  for(const row of own.slice(0,50)){
   try {
    const body=await this.request(`/groups/${group}/records`,{session,body:{records:[{collection:row.table,record:row.record,...(row.record.rosterRevision?{rosterRevision:row.record.rosterRevision}:{})}]}});
    const ack=body.acks?.[0];if(body.acks?.length!==1||ack.collection!==row.table||ack.key!==keyOf(row.record)||!['stored','duplicate'].includes(ack.status))throw Error('サーバーの保存確認が一致しません');
    await this.store.update('meta',row.ackKey,()=>row.hash);sent++;
   }catch(e){errors.push(e.message);if(!(e instanceof CloudApiError)||e.status>=500||e.status===401||e.status===429)break;}
  }
  pending=own.length-sent;if(sent)received+=await this.pull(session);
  return {sent,received,pending,foreign,error:errors[0]??null};
 }
}
