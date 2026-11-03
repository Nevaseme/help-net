import {requireValue,uuid,canonical} from './validation.mjs';

const hash=async value=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))),b=>b.toString(16).padStart(2,'0')).join('');
const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const normalize=value=>typeof value==='string'?value.normalize('NFKC').toUpperCase().replace(/[\s-]/g,''):'';
const day=86400000;
export function validateDemoPassword(value){
 requireValue(typeof value==='string'&&value.trim().length>0&&value.length<=128,'invalid_venue_password',400,'参加パスワードを1〜128文字で入力してください。');
}
export const demoAuthMethods={
 authInit(){
  this.s.run('CREATE TABLE IF NOT EXISTS device_access (device_id TEXT PRIMARY KEY,revoked INTEGER NOT NULL DEFAULT 0,last_seen INTEGER NOT NULL)');
  this.s.run('CREATE TABLE IF NOT EXISTS recovery_codes (code_hash TEXT PRIMARY KEY,member_id TEXT NOT NULL,expires_at INTEGER NOT NULL,redeemed_device TEXT,redeemed_token TEXT)');
 },
 inactiveMembers(){
  return this.s.all('SELECT DISTINCT member_id FROM devices').filter(r=>!this.s.all('SELECT d.device_id FROM devices d LEFT JOIN device_access a ON a.device_id=d.device_id WHERE d.member_id=? AND COALESCE(a.revoked,0)=0',r.member_id).length).map(r=>r.member_id);
 },
 async access(token,input){
  const actor=await this.identity(token),memberId=input?.memberId??actor.memberId;
  requireValue(uuid(memberId));
  requireValue(actor.memberId===memberId||actor.role==='ADMIN','admin_required',403);
  const member=this.snapshot().roster.members.find(m=>m.memberId===memberId);
  requireValue(member&&member.role==='MEMBER','member_recovery_only',403,'管理者の認証はこの操作では変更できません。');
  requireValue(this.venueConfig()&&!this.venueConfig().privateTraining&&!this.venueSnapshot().closed,'venue_not_started',409,'会場デモの実施中に利用できます。');
  if(input.action==='issue'){
   requireValue(actor.role==='ADMIN'||!this.inactiveMembers().includes(memberId),'member_disabled',409,'停止済みの参加者は管理者に確認してください。');
   const bytes=crypto.getRandomValues(new Uint8Array(16)),code=Array.from(bytes,b=>alphabet[b%alphabet.length]).join(''),codeHash=await hash(code),expiresAt=this.now()+day;
   this.s.transaction(()=>{
    this.s.run('DELETE FROM recovery_codes WHERE member_id=?',memberId);
    this.s.run('INSERT INTO recovery_codes(code_hash,member_id,expires_at) VALUES (?,?,?)',codeHash,memberId,expiresAt);
   });await this.s.flush();return {code:code.match(/.{4}/g).join('-'),expiresAt};
  }
  requireValue(input.action==='revoke'&&actor.role==='ADMIN','admin_required',403);
  this.s.transaction(()=>{
   for(const device of member.deviceIds)this.s.run('INSERT INTO device_access(device_id,revoked,last_seen) VALUES (?,1,?) ON CONFLICT(device_id) DO UPDATE SET revoked=1',device,this.now());
   this.s.run('DELETE FROM recovery_codes WHERE member_id=?',memberId);
  });await this.s.flush();return this.snapshot();
 },
 async recoverVenue(_token,input){
  this.mustExist();const config=this.venueConfig();
  requireValue(config&&!config.privateTraining&&!this.venueSnapshot().closed,'venue_closed',409,'この会場デモは終了しています。');
  requireValue(uuid(input?.deviceId)&&/^[a-f0-9]{64}$/.test(input?.registrationToken),'invalid_request',400);
  const code=normalize(input.code);requireValue(/^[A-Z2-9]{16}$/.test(code),'invalid_recovery',403,'復元コードを確認してください。有効期限は発行から24時間です。');
  const codeHash=await hash(code),tokenHash=await hash(input.registrationToken);
  this.s.transaction(()=>{
   requireValue(this.venueConfig()?.sessionId===config.sessionId&&!this.venueSnapshot().closed,'venue_closed',409);
   const recovery=this.s.all('SELECT * FROM recovery_codes WHERE code_hash=?',codeHash)[0];
   requireValue(recovery&&recovery.expires_at>this.now(),'invalid_recovery',403,'復元コードを確認してください。有効期限は発行から24時間です。');
   if(recovery.redeemed_device){requireValue(recovery.redeemed_device===input.deviceId&&recovery.redeemed_token===tokenHash,'invalid_recovery',403);return;}
   const existing=this.s.all('SELECT member_id FROM devices WHERE device_id=?',input.deviceId)[0];
   requireValue(!existing||existing.member_id===recovery.member_id,'device_exists',409,'この端末は別の参加者として登録済みです。管理者に確認してください。');
   const snapshot=this.snapshot(),roster=snapshot.roster,member=roster.members.find(m=>m.memberId===recovery.member_id);
   requireValue(member?.role==='MEMBER','invalid_recovery',403);
   let placement=snapshot.venue.placements.find(p=>p.memberId===member.memberId);
   if(!placement){const used=new Set(snapshot.venue.placements.map(p=>p.slotId)),slotId=config.slots.findIndex((_,i)=>!used.has(i));requireValue(slotId>=0,'venue_full',409);placement={sessionId:config.sessionId,memberId:member.memberId,name:member.activityName,slotId,...(config.positionMode==='ACTUAL'?{latitude:null,longitude:null,source:'DEVICE_LOCATION'}:{...config.slots[slotId],source:'DEMO_ASSIGNED'})};}
   for(const device of member.deviceIds)this.s.run('INSERT INTO device_access(device_id,revoked,last_seen) VALUES (?,1,?) ON CONFLICT(device_id) DO UPDATE SET revoked=1',device,this.now());
   if(!existing){member.deviceIds.push(input.deviceId);this.s.run('INSERT INTO roster_history(revision,body) VALUES (?,?)',snapshot.rosterRevision+1,canonical(roster));}
   this.s.run('INSERT INTO devices(device_id,member_id,role,token_hash) VALUES (?,?,?,?) ON CONFLICT(device_id) DO UPDATE SET token_hash=excluded.token_hash',input.deviceId,member.memberId,'MEMBER',tokenHash);
   this.s.run('INSERT INTO device_access(device_id,last_seen) VALUES (?,?) ON CONFLICT(device_id) DO UPDATE SET revoked=0,last_seen=excluded.last_seen',input.deviceId,this.now());
   const replacement={...placement,deviceId:input.deviceId};
   this.s.run('INSERT INTO venue_participants(session_id,device_id,body) VALUES (?,?,?) ON CONFLICT(session_id,device_id) DO UPDATE SET body=excluded.body',config.sessionId,input.deviceId,JSON.stringify(replacement));
   this.s.run('UPDATE recovery_codes SET redeemed_device=?,redeemed_token=? WHERE code_hash=?',input.deviceId,tokenHash,codeHash);
  });
  const result=await this.registrationReplay(input.deviceId,input.registrationToken,tokenHash);
  return {...result,activityName:result.roster.members.find(m=>m.memberId===result.memberId).activityName,recovered:true};
 }
};
