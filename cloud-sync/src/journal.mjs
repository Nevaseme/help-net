import {ApiError,requireValue,uuid,canonical,collections,validateShape,validateContext} from './validation.mjs';
import {importRelayKey,verifyRelayProof} from './relay-proof.mjs';
import {venueMethods,participationCredentials} from './venue.mjs';
import {repairSeededBlockedRoad} from './venue-scenario.mjs';
import {demoAuthMethods} from './demo-auth.mjs';
import {municipalityByCode} from '../../lab/web/municipalities.mjs';

export const hashSecret=async value=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))),b=>b.toString(16).padStart(2,'0')).join('');
const secret=()=>Array.from(crypto.getRandomValues(new Uint8Array(32)),b=>b.toString(16).padStart(2,'0')).join('');
const text=(v,max)=>typeof v==='string'&&v.trim().length>0&&v.length<=max;

/** Synchronous SQL adapter with transaction rollback and an awaited durable flush. */
export class Journal {
 constructor(storage,now=()=>Date.now()){
  this.s=storage;this.now=now;
  for(const statement of [
   'CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY,value TEXT NOT NULL)',
   'CREATE TABLE IF NOT EXISTS roster_history (revision INTEGER PRIMARY KEY,body TEXT NOT NULL)',
   'CREATE TABLE IF NOT EXISTS devices (device_id TEXT PRIMARY KEY,member_id TEXT NOT NULL,role TEXT NOT NULL,token_hash TEXT UNIQUE NOT NULL)',
   'CREATE TABLE IF NOT EXISTS relay_keys (device_id TEXT PRIMARY KEY,key_id TEXT UNIQUE NOT NULL,public_key_spki TEXT NOT NULL)',
   'CREATE TABLE IF NOT EXISTS invitations (code_hash TEXT PRIMARY KEY,expires_at INTEGER NOT NULL)',
   'CREATE TABLE IF NOT EXISTS journal (cursor INTEGER PRIMARY KEY AUTOINCREMENT,collection TEXT NOT NULL,record_key TEXT NOT NULL,revision INTEGER NOT NULL,roster_revision INTEGER NOT NULL,body TEXT NOT NULL,UNIQUE(collection,record_key,revision))',
   'CREATE INDEX IF NOT EXISTS journal_collection ON journal(collection,cursor)'
  ])this.s.run(statement);
  this.venueInit();this.authInit();
 }
 meta(key){return this.s.all('SELECT value FROM metadata WHERE key=?',key)[0]?.value;}
 mustExist(){requireValue(this.meta('groupId'),'group_not_found',404);}
 async identity(token){
  this.mustExist();requireValue(typeof token==='string'&&/^[a-f0-9]{64}$/.test(token),'unauthorized',401);
  const row=this.s.all('SELECT device_id,member_id,role FROM devices WHERE token_hash=?',await hashSecret(token))[0];
  requireValue(row,'unauthorized',401);
  const access=this.s.all('SELECT revoked,last_seen FROM device_access WHERE device_id=?',row.device_id)[0];
  requireValue(!access?.revoked,'access_revoked',401,'この参加登録は停止されています。管理者に確認してください。');
  requireValue(row.role==='ADMIN'||!this.venueConfig()||this.venueConfig().privateTraining||!access||this.now()-access.last_seen<=7*86400000,'session_expired',401,'接続の有効期限が切れました。復元コードを使うか、管理者に確認してください。');
  if(!access||this.now()-access.last_seen>=3600000){this.s.run('INSERT INTO device_access(device_id,last_seen) VALUES (?,?) ON CONFLICT(device_id) DO UPDATE SET last_seen=excluded.last_seen',row.device_id,this.now());await this.s.flush();}
  return {deviceId:row.device_id,memberId:row.member_id,role:row.role};
 }
 snapshot(){
  this.mustExist();const history=this.s.all('SELECT revision,body FROM roster_history ORDER BY revision').map(r=>({revision:r.revision,roster:JSON.parse(r.body)})),last=history.at(-1);
  return {groupId:this.meta('groupId'),groupName:this.meta('groupName'),rosterRevision:last.revision,roster:last.roster,rosterHistory:history,venue:this.venueSnapshot(),municipality:JSON.parse(this.meta('municipality')??'null')};
 }
 internalRoster(){const s=this.snapshot();return {...s.roster,revision:s.rosterRevision,history:s.rosterHistory.map(h=>({revision:h.revision,members:h.roster.members}))};}
 async create(input){
  requireValue(input&&uuid(input.groupId)&&uuid(input.deviceId)&&text(input.activityName,60)&&text(input.groupName,80)&&/^[a-f0-9]{64}$/.test(input.registrationToken));
  const token=input.registrationToken,tokenHash=await hashSecret(token),memberId=crypto.randomUUID();
  if(this.meta('groupId'))return this.registrationReplay(input.deviceId,token,tokenHash);
  const credentials=input.password===undefined?null:await participationCredentials(input.password);
  this.s.transaction(()=>{
   requireValue(!this.meta('groupId'),'group_exists',409);
   this.s.run('INSERT INTO metadata(key,value) VALUES (?,?)','groupId',input.groupId);
   this.s.run('INSERT INTO metadata(key,value) VALUES (?,?)','groupName',input.groupName.trim());
   if(credentials)this.s.run('INSERT INTO metadata(key,value) VALUES (?,?)','participationCredentials',JSON.stringify(credentials));
   this.addMember(input.deviceId,memberId,input.activityName,'ADMIN',tokenHash);
  });await this.s.flush();return {...this.snapshot(),deviceId:input.deviceId,memberId,role:'ADMIN',token};
 }
 async registrationReplay(deviceId,token,tokenHash){
  const row=this.s.all('SELECT device_id,member_id,role FROM devices WHERE device_id=? AND token_hash=?',deviceId,tokenHash)[0];
  requireValue(row,'device_exists',409);await this.identity(token);await this.s.flush();return {...this.snapshot(),deviceId:row.device_id,memberId:row.member_id,role:row.role,token};
 }
 addMember(deviceId,memberId,activityName,role,tokenHash){
  requireValue(!this.s.all('SELECT device_id FROM devices WHERE device_id=?',deviceId).length,'device_exists',409);
  const old=this.s.all('SELECT revision,body FROM roster_history ORDER BY revision DESC LIMIT 1')[0];
  const roster=old?JSON.parse(old.body):{version:1,groupId:this.meta('groupId'),members:[]};
  roster.members.push({memberId,activityName:activityName.trim(),role,deviceIds:[deviceId]});
  this.s.run('INSERT INTO devices(device_id,member_id,role,token_hash) VALUES (?,?,?,?)',deviceId,memberId,role,tokenHash);
  this.s.run('INSERT INTO device_access(device_id,last_seen) VALUES (?,?)',deviceId,this.now());
  this.s.run('INSERT INTO roster_history(revision,body) VALUES (?,?)',(old?.revision??0)+1,canonical(roster));
  if(this.venueConfig()&&!this.venueSnapshot().closed)this.assignVenue(deviceId,memberId,activityName.trim());
 }
 async invite(token){
  const actor=await this.identity(token);requireValue(actor.role==='ADMIN','admin_required',403);
  const code=secret(),hash=await hashSecret(code),expiresAt=this.now()+86400000;
  this.s.run('INSERT INTO invitations(code_hash,expires_at) VALUES (?,?)',hash,expiresAt);await this.s.flush();return {code,expiresAt};
 }
 async join(input){
  this.mustExist();requireValue(input&&uuid(input.deviceId)&&text(input.activityName,60)&&typeof input.code==='string'&&/^[a-f0-9]{64}$/.test(input.registrationToken));
  const token=input.registrationToken,tokenHash=await hashSecret(token);
  const existing=this.s.all('SELECT member_id FROM devices WHERE device_id=?',input.deviceId)[0];
  if(existing&&!this.meta('member-left:'+existing.member_id))return this.registrationReplay(input.deviceId,token,tokenHash);
  requireValue(!this.venueConfig()||this.venueConfig().privateTraining||!this.venueSnapshot().closed,'venue_closed',409,'この会場デモは終了しています。');
  requireValue(/^[a-f0-9]{64}$/.test(input.code),'invalid_invitation',403);
  const codeHash=await hashSecret(input.code),memberId=crypto.randomUUID();
  this.s.transaction(()=>{
   const invite=this.s.all('SELECT expires_at FROM invitations WHERE code_hash=?',codeHash)[0];requireValue(invite&&invite.expires_at>this.now(),'invalid_invitation',403);
   if(!this.rejoinMember(input.deviceId,tokenHash,input.activityName))this.addMember(input.deviceId,memberId,input.activityName,'MEMBER',tokenHash);
  });return this.registrationReplay(input.deviceId,token,tokenHash);
 }
 async roster(token){await this.identity(token);return this.snapshot();}
 async setMemberPermission(token,input){
  const actor=await this.identity(token);requireValue(actor.role==='ADMIN','admin_required',403);
  requireValue(uuid(input?.memberId)&&typeof input?.canTriage==='boolean');
  this.s.transaction(()=>{const snapshot=this.snapshot(),roster=structuredClone(snapshot.roster),member=roster.members.find(m=>m.memberId===input.memberId);
   requireValue(member&&member.role!=='ADMIN','invalid_member',400);
   if((member.canTriage??false)===input.canTriage)return;
   member.canTriage=input.canTriage;this.s.run('INSERT INTO roster_history(revision,body) VALUES (?,?)',snapshot.rosterRevision+1,canonical(roster));
  });await this.s.flush();return this.snapshot();
 }
 async setMunicipality(token,input){
  const actor=await this.identity(token);requireValue(actor.role==='ADMIN','admin_required',403);
  const city=municipalityByCode(input?.code);requireValue(city,'unsupported_municipality',400);
  const value={...city,updatedAt:this.now(),updatedBy:actor.deviceId};
  this.s.run('INSERT INTO metadata(key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value','municipality',canonical(value));
  await this.s.flush();return this.snapshot();
 }
 async nearby(token){
  await this.identity(token);
  // Stable across offline sessions and server restarts; never included in public snapshots.
  this.s.transaction(()=>{
   if(!this.meta('nearbyKey'))this.s.run('INSERT INTO metadata(key,value) VALUES (?,?)','nearbyKey',secret());
  });
  await this.s.flush();return {key:this.meta('nearbyKey')};
 }
 history(collection){return this.s.all('SELECT body FROM journal WHERE collection=? ORDER BY cursor',collection).map(r=>JSON.parse(r.body));}
 currentMember(deviceId){
  const member=this.internalRoster().members.find(m=>m.deviceIds.includes(deviceId));
  requireValue(member,'member_required',403);return {deviceId,memberId:member.memberId,role:member.role};
 }
 async relayKeys(token){
  const actor=await this.identity(token);this.currentMember(actor.deviceId);
  return {keys:this.s.all('SELECT device_id,key_id,public_key_spki FROM relay_keys ORDER BY device_id').map(r=>({deviceId:r.device_id,keyId:r.key_id,publicKeySpki:r.public_key_spki}))};
 }
 async registerRelayKey(token,input){
  const actor=await this.identity(token);this.currentMember(actor.deviceId);await importRelayKey(input);
  this.s.transaction(()=>{
   const existing=this.s.all('SELECT device_id,key_id,public_key_spki FROM relay_keys WHERE device_id=? OR key_id=?',actor.deviceId,input.keyId);
   requireValue(!existing.length||existing.length===1&&existing[0].device_id===actor.deviceId&&existing[0].key_id===input.keyId&&existing[0].public_key_spki===input.publicKeySpki,'relay_key_conflict',409);
   if(!existing.length)this.s.run('INSERT INTO relay_keys(device_id,key_id,public_key_spki) VALUES (?,?,?)',actor.deviceId,input.keyId,input.publicKeySpki);
  });
  await this.s.flush();return {deviceId:actor.deviceId,keyId:input.keyId,publicKeySpki:input.publicKeySpki};
 }
 async relayAuthor(actor,item){
  if(item?.relayProof===undefined)return actor;
  const def=collections[item?.collection];requireValue(def&&item.record&&uuid(item.record[def.actor]));
  const author=this.currentMember(item.record[def.actor]);
  requireValue(!this.s.all('SELECT revoked FROM device_access WHERE device_id=?',author.deviceId)[0]?.revoked,'access_revoked',403);
  const key=this.s.all('SELECT key_id,public_key_spki FROM relay_keys WHERE device_id=?',author.deviceId)[0];
  requireValue(key,'invalid_relay_proof',403);
  await verifyRelayProof(item,this.meta('groupId'),{keyId:key.key_id,publicKeySpki:key.public_key_spki});return author;
 }
 async push(token,input){
  const actor=await this.identity(token);requireValue(input&&Array.isArray(input.records)&&input.records.length>0&&input.records.length<=100);
  this.currentMember(actor.deviceId);
  // Crypto is asynchronous; complete every proof before the synchronous atomic write.
  const authors=await Promise.all(input.records.map(item=>this.relayAuthor(actor,item)));
  const acks=this.s.transaction(()=>input.records.map((item,index)=>this.append(authors[index],item)));
  // In production flush maps to storage.sync(); an output is not acknowledged before persistence.
  await this.s.flush();return {acks,rosterRevision:this.snapshot().rosterRevision};
 }
 append(actor,item){
  requireValue(item&&typeof item.collection==='string');const {collection}=item;
  const roster=this.internalRoster();let record=validateShape(collection,item.record,roster);
  requireValue(record.groupId===roster.groupId,'wrong_group',403);
  const def=collections[collection],key=record[def.key],revision=def.revision?record.revision:0;
  const old=this.s.all('SELECT cursor,roster_revision,body FROM journal WHERE collection=? AND record_key=? AND revision=?',collection,key,revision)[0];
  // Normalize managed defaults consistently before comparing a retransmission, but do not re-run changing context.
  if(collection==='quakes')record={targetId:null,reportIds:[],title:'',area:'',endedIncidentIds:[],...record,...(record.action==='EEW'?{eew:{startsNewPeriod:false,...record.eew}}:{})};
  if(collection==='responses')record={replaces:[],...record};
  if(old){requireValue(old.body===canonical(record),'record_conflict',409);return {collection,key,cursor:old.cursor,status:'duplicate',rosterRevision:old.roster_revision};}
  requireValue(record[def.actor]===actor.deviceId,'original_author_required',403);
  if(['responses','places','incidents'].includes(collection)){
   // Match receiver merge constraints: response/place sequence is per device in the group;
   // incident sequence additionally belongs to the original (unmerged) incident ID.
   const conflict=this.history(collection).some(p=>p[def.actor]===record[def.actor]&&p.sequence===record.sequence&&(collection!=='incidents'||p.incidentId===record.incidentId));
   requireValue(!conflict,'sequence_conflict',409);
  }
  let rosterRevision=item.rosterRevision??roster.revision;
  if(collection==='quakes'&&record.action==='FORM'){
   requireValue(Number.isSafeInteger(record.rosterRevision)&&record.rosterRevision>0,'roster_revision_required',409);
   requireValue(item.rosterRevision===undefined||item.rosterRevision===record.rosterRevision,'roster_revision_mismatch',409);rosterRevision=record.rosterRevision;
  }
  requireValue(Number.isSafeInteger(rosterRevision)&&roster.history.some(h=>h.revision===rosterRevision),'unknown_roster_revision',409);
  if(collection==='quakes'&&record.action==='FORM')requireValue(roster.history.find(h=>h.revision===rosterRevision).members.some(m=>m.deviceIds.includes(actor.deviceId)),'author_not_in_snapshot',403);
  if(collection==='memos'){
   const previous=this.s.all('SELECT body FROM journal WHERE collection=? AND record_key=? LIMIT 1',collection,key)[0];
   if(previous){const p=JSON.parse(previous.body);requireValue(p.authorId===record.authorId&&p.incidentId===record.incidentId,'record_conflict',409);}
  }
  record=validateContext(collection,record,actor,roster,c=>this.history(c));
  const row=this.s.all('INSERT INTO journal(collection,record_key,revision,roster_revision,body) VALUES (?,?,?,?,?) RETURNING cursor',collection,key,revision,rosterRevision,canonical(record))[0];
  return {collection,key,cursor:row.cursor,status:'stored',rosterRevision};
 }
 async pull(token,after=0,limit=100){
  const actor=await this.identity(token);requireValue(Number.isSafeInteger(after)&&after>=0&&Number.isSafeInteger(limit)&&limit>=1&&limit<=100);
  if(repairSeededBlockedRoad(this,actor))await this.s.flush();
  const rows=this.s.all('SELECT cursor,collection,body,roster_revision FROM journal WHERE cursor>? ORDER BY cursor LIMIT ?',after,limit+1),hasMore=rows.length>limit;
  const records=rows.slice(0,limit).map(r=>({cursor:r.cursor,collection:r.collection,record:JSON.parse(r.body),rosterRevision:r.roster_revision}));
  return {records,nextCursor:records.at(-1)?.cursor??after,hasMore,rosterRevision:this.snapshot().rosterRevision};
 }
}
Object.assign(Journal.prototype,venueMethods);
Object.assign(Journal.prototype,demoAuthMethods);
