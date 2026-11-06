import {compactSlots} from './venue-compact.mjs';
import {createVenueScenario,seedVenueScenario} from './venue-scenario.mjs';
import {ApiError,requireValue,uuid} from './validation.mjs';
import {validateDemoPassword} from './demo-auth.mjs';
import catalogue from './venue-buildings.json' with {type:'json'};
import {latestDemoHome} from '../../lab/web/device-records.mjs';
import {withDemoObservations,withUnknownDemoResidents} from '../../lab/web/venue-observations.mjs';

import {demoCommunityAt,demoPhonePoint} from './venue-layout.mjs';
const enc=new TextEncoder();
const hex=bytes=>Array.from(new Uint8Array(bytes),v=>v.toString(16).padStart(2,'0')).join('');
const sha=async text=>hex(await crypto.subtle.digest('SHA-256',enc.encode(text)));
const normalize=name=>typeof name==='string'?name.normalize('NFKC').trim().toLowerCase():'';
async function passwordHash(password,salt){
 validateDemoPassword(password);
 const key=await crypto.subtle.importKey('raw',enc.encode(password),'PBKDF2',false,['deriveBits']);
 return hex(await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',iterations:100000,salt:enc.encode(salt)},key,256));
}
const area={id:'kyoto-gosho-south',name:'御所南〜烏丸御池',south:35.011,north:35.018,west:135.758,east:135.767};
export async function participationCredentials(password){const salt=hex(crypto.getRandomValues(new Uint8Array(16)));return {salt,passwordHash:await passwordHash(password,salt)};}
function slots(){return compactSlots();}

export const venueMethods={
 groupParticipationCredentials(){
  const saved=this.meta('participationCredentials');if(saved)return JSON.parse(saved);
  const old=this.venueConfig();return old?.passwordHash?{salt:old.salt,passwordHash:old.passwordHash}:null;
 },
 venueInit(){
  this.s.run('CREATE TABLE IF NOT EXISTS venue_names (name TEXT PRIMARY KEY,group_id TEXT NOT NULL)');
  this.s.run('CREATE TABLE IF NOT EXISTS venue_attempts (key TEXT PRIMARY KEY,bucket INTEGER NOT NULL,count INTEGER NOT NULL)');
  this.s.run('CREATE TABLE IF NOT EXISTS venue_participants (session_id TEXT NOT NULL,device_id TEXT NOT NULL,body TEXT NOT NULL,PRIMARY KEY(session_id,device_id))');
 },
 venueConfig(){const text=this.meta('venue');return text?JSON.parse(text):null;},
 venueSnapshot(){
  const config=this.venueConfig();if(!config)return null;
  const closed=this.history('quakes').some(e=>e.action==='END'&&(e.targetId===config.sessionId||e.endedIncidentIds?.includes(config.sessionId)));
  return {version:1,mode:'VENUE_DEMO',positionMode:config.positionMode??'KYOTO',presentationMode:config.presentationMode??'DRILL',startedAt:config.startedAt??0,simulationStartedAt:config.simulationStartedAt??null,groupId:this.meta('groupId'),sessionId:config.sessionId,incidentId:config.sessionId,areaId:config.areaId,areaName:config.positionMode==='ACTUAL'?'参加者の現在地':area.name,closed,
   scenario:withDemoObservations(withUnknownDemoResidents(config.scenario))??null,recoveryAvailable:!config.privateTraining,inactiveMemberIds:this.inactiveMembers(),placements:this.s.all('SELECT p.body FROM venue_participants p LEFT JOIN device_access a ON a.device_id=p.device_id WHERE p.session_id=? AND COALESCE(a.revoked,0)=0 ORDER BY p.device_id',config.sessionId).map(r=>{const p=JSON.parse(r.body);if(config.positionMode==='ACTUAL')return p;const home=latestDemoHome(this.history('deviceRecords'),this.meta('groupId'),config.sessionId,p.deviceId)??p.home??config.slots[p.slotId]?.home??catalogue.homes[p.slotId];return {...p,home};})};
 },
 assignVenue(deviceId,memberId,name){
  const config=this.venueConfig();requireValue(config,'venue_not_started',409);
  const existing=this.s.all('SELECT body FROM venue_participants WHERE session_id=? AND device_id=?',config.sessionId,deviceId)[0];if(existing)return JSON.parse(existing.body);
  const used=new Set(this.venueSnapshot().placements.map(p=>p.slotId));const count=config.slots.findIndex((_,i)=>!used.has(i));
  requireValue(count>=0,'venue_full',409,'この会場デモの定員に達しています。');
  const placement={sessionId:config.sessionId,deviceId,memberId,name,slotId:count,...(config.positionMode==='ACTUAL'?{latitude:null,longitude:null,source:'DEVICE_LOCATION'}:{...config.slots[count],source:'DEMO_ASSIGNED'})};
  this.s.run('INSERT INTO venue_participants(session_id,device_id,body) VALUES (?,?,?)',config.sessionId,deviceId,JSON.stringify(placement));return placement;
 },
 async setVenueClock(token,input){
  const actor=await this.identity(token);requireValue(actor.role==='ADMIN','admin_required',403);
  requireValue(Number.isInteger(input?.minutes)&&input.minutes>=0&&input.minutes<=1440,'invalid_demo_minutes',400,'0〜1440分で指定してください。');
  const config=this.venueConfig();requireValue(config&&!this.venueSnapshot().closed&&input.sessionId===config.sessionId,'venue_changed',409);
  config.simulationStartedAt=this.now()-input.minutes*60000;
  this.s.run('UPDATE metadata SET value=? WHERE key=?',JSON.stringify(config),'venue');await this.s.flush();return this.snapshot();
 },
 async authorizeVenue(token){const actor=await this.identity(token);requireValue(actor.role==='ADMIN','admin_required',403);return {groupName:this.meta('groupName')};},
 async configureVenue(token,input){
  const actor=await this.identity(token);requireValue(actor.role==='ADMIN','admin_required',403);
  requireValue(input&&uuid(input.requestId)&&input.areaId===area.id);
  const privateTraining=input.privateTraining===true;
  const presentationMode=input.presentationMode??'DRILL';requireValue(['DRILL','EARTHQUAKE'].includes(presentationMode),'invalid_presentation_mode',400);
  const elapsedMinutes=input.elapsedMinutes??(privateTraining?0:3);requireValue(Number.isInteger(elapsedMinutes)&&elapsedMinutes>=0&&elapsedMinutes<=1440,'invalid_demo_minutes',400);
  requireValue(input.positionMode===undefined||['KYOTO','ACTUAL'].includes(input.positionMode),'invalid_position_mode',400);
  const previous=this.venueConfig(),credentials=this.groupParticipationCredentials(),settingPassword=!credentials&&!privateTraining&&input.password!==undefined&&input.password!=='';
  if(settingPassword)validateDemoPassword(input.password);
  if(privateTraining)requireValue(typeof input.title==='string'&&input.title.trim().length>0&&input.title.length<=80&&typeof input.area==='string'&&input.area.trim().length>0&&input.area.length<=120);
  const salt=settingPassword?hex(crypto.getRandomValues(new Uint8Array(16))):credentials?.salt??null;
  const hash=settingPassword?await passwordHash(input.password,salt):credentials?.passwordHash??null;
  this.s.transaction(()=>{
   const previous=this.venueConfig();if(previous?.sessionId===input.requestId)return;
   requireValue(!previous||this.venueSnapshot().closed,'venue_already_active',409,'現在の会場デモを終了してから、新しいデモを開始してください。');
   if(hash)this.s.run('INSERT INTO metadata(key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value','participationCredentials',JSON.stringify({salt,passwordHash:hash}));
   const config={sessionId:input.requestId,areaId:input.areaId,privateTraining,presentationMode,positionMode:input.positionMode??'KYOTO',startedAt:this.now(),simulationStartedAt:this.now()-elapsedMinutes*60000,salt,passwordHash:hash,slots:slots(),scenario:privateTraining?null:createVenueScenario(input.requestId,this.now())};
   this.s.run('INSERT INTO metadata(key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value','venue',JSON.stringify(config));
   this.append(actor,{collection:'quakes',record:{groupId:this.meta('groupId'),eventId:input.requestId,actorDeviceId:actor.deviceId,operatedAt:this.now(),action:'TRAINING_START',training:true,title:privateTraining?input.title.trim():presentationMode==='EARTHQUAKE'?'地震の安否確認':'安否確認の訓練',area:privateTraining?input.area.trim():config.positionMode==='ACTUAL'?'参加者の現在地':area.name}});
   if(config.scenario)seedVenueScenario(this,actor,config);
   for(const member of this.snapshot().roster.members)for(const device of member.deviceIds)if(!this.s.all('SELECT revoked FROM device_access WHERE device_id=?',device)[0]?.revoked)this.assignVenue(device,member.memberId,member.activityName);
  });await this.s.flush();return this.snapshot();
 },
 async checkVenue(_token,input){
  this.mustExist();const config=this.venueConfig(),credentials=this.groupParticipationCredentials();
  requireValue(credentials&&normalize(input?.groupName)===normalize(this.meta('groupName')),'invalid_venue_credentials',403,'グループ名またはパスワードを確認してください。');
  let valid=false;try {valid=(await passwordHash(input.password,credentials.salt))===credentials.passwordHash;}catch{}
  requireValue(valid,'invalid_venue_credentials',403,'グループ名またはパスワードを確認してください。');
  return {groupId:this.meta('groupId'),groupName:this.meta('groupName'),sessionId:config&&!this.venueSnapshot().closed?config.sessionId:null,positionMode:config?.positionMode??'KYOTO'};
 },
 async joinVenue(_token,input){
  const checked=await this.checkVenue(null,input);
  requireValue(uuid(input.deviceId)&&typeof input.activityName==='string'&&input.activityName.trim().length>0&&input.activityName.length<=60&&/^[a-f0-9]{64}$/.test(input.registrationToken));
  const hash=await sha(input.registrationToken),memberId=crypto.randomUUID();
  this.s.transaction(()=>{
   const active=this.venueConfig()&&!this.venueSnapshot().closed;
   const existing=this.s.all('SELECT member_id,token_hash FROM devices WHERE device_id=?',input.deviceId)[0];
   if(existing){requireValue(existing.token_hash===hash,'device_exists',409);if(active)this.assignVenue(input.deviceId,existing.member_id,this.snapshot().roster.members.find(m=>m.memberId===existing.member_id).activityName);}
   else {this.addMember(input.deviceId,memberId,input.activityName,'MEMBER',hash);if(active)this.assignVenue(input.deviceId,memberId,input.activityName.trim());}
  });
  return this.registrationReplay(input.deviceId,input.registrationToken,hash);
 },
 async directoryCreate(args,call){
  const name=normalize(args.groupName);requireValue(name.length>0&&name.length<=80);validateDemoPassword(args.password);
  const current=this.s.all('SELECT group_id FROM venue_names WHERE name=?',name)[0];requireValue(!current||current.group_id===args.groupId,'venue_name_exists',409,'同じ名前のグループがあります。別の名前を使用してください。');
  const result=await call(args.groupId,'create',args,null);
  this.s.run('INSERT INTO venue_names(name,group_id) VALUES (?,?) ON CONFLICT(name) DO NOTHING',name,args.groupId);await this.s.flush();return result;
 },
 async directoryConfigure(args,call){
  const authorized=await call(args.groupId,'authorizeVenue',null,args.token),name=normalize(authorized.groupName);
  requireValue(name.length>0&&name.length<=80);
  if(args.input?.privateTraining===true||args.input?.password===undefined||args.input?.password==='')return call(args.groupId,'configureVenue',args.input,args.token);
  this.s.transaction(()=>{
   const current=this.s.all('SELECT group_id FROM venue_names WHERE name=?',name)[0];
   requireValue(!current||current.group_id===args.groupId,'venue_name_exists',409,'同じ名前の会場デモがあります。別の名前のグループを使用してください。');
   if(!current)this.s.run('INSERT INTO venue_names(name,group_id) VALUES (?,?)',name,args.groupId);
  });await this.s.flush();return call(args.groupId,'configureVenue',args.input,args.token);
 },
 async directoryAccess(args,call){
  const key=await sha(args.ip??'unknown'),bucket=Math.floor(this.now()/60000);
  this.s.transaction(()=>{
   this.s.run('DELETE FROM venue_attempts WHERE bucket<?',bucket-1);
   const current=this.s.all('SELECT bucket,count FROM venue_attempts WHERE key=?',key)[0];
   const count=current?.bucket===bucket?current.count:0;
   requireValue(count<120,'rate_limited',429,'入力が集中しています。1分ほど待って再試行してください。');
   this.s.run('INSERT INTO venue_attempts(key,bucket,count) VALUES (?,?,?) ON CONFLICT(key) DO UPDATE SET bucket=excluded.bucket,count=excluded.count',key,bucket,count+1);
  });await this.s.flush();
  const name=normalize(args.input?.groupName);requireValue(name.length>0&&name.length<=80);
  const row=this.s.all('SELECT group_id FROM venue_names WHERE name=?',name)[0];
  requireValue(row,'invalid_venue_credentials',403,'グループ名またはパスワードを確認してください。');
  return call(row.group_id,args.recover?'recoverVenue':args.join?'joinVenue':'checkVenue',args.input,null);
 }
};
