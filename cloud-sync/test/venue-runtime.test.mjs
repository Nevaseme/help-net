import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';

test('thirty-nine same-IP concurrent joins reach forty total participants survive real Worker directory RPC and SQLite restart',async t=>{
 const dir=await mkdtemp(join(tmpdir(),'pland-venue-'));
 const bundle=await build({entryPoints:[fileURLToPath(new URL('../src/worker.mjs',import.meta.url))],bundle:true,format:'esm',write:false,external:['cloudflare:workers'],platform:'browser'});
 const options={...convertV4MiniflareOptions({name:'venue-test',modules:true,script:bundle.outputFiles[0].text,compatibilityDate:'2026-10-23',compatibilityFlags:['nodejs_compat'],durableObjects:{GROUPS:{className:'GroupJournal',useSQLite:true}},ratelimits:{SIGNUP_LIMITER:{namespace_id:'10302026',simple:{limit:30,period:60}}}}),resourcePersistencePath:dir,telemetry:{enabled:false}};
 let mf=new Miniflare(options);t.after(async()=>{await mf.dispose();await rm(dir,{recursive:true,force:true});});
 const id=()=>crypto.randomUUID(),token=()=>id().replaceAll('-','')+id().replaceAll('-','');
 const call=async(path,body,auth)=>{const response=await mf.dispatchFetch('https://sync.test/api/v1'+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json','CF-Connecting-IP':'192.0.2.5','X-PlanD-Records-Version':'2',...(auth?{Authorization:'Bearer '+auth}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});return {status:response.status,body:await response.json()};};
 const groupName='並行参加の架空会場',password='fictional-runtime-pass';
 const created=await call('/groups',{requestId:id(),deviceId:id(),groupName,password,activityName:'管理者',registrationToken:token()});assert.equal(created.status,200,JSON.stringify(created.body));const admin=created.body;
 assert.equal((await call('/venue/check',{groupName,password})).status,200,'group can be joined before an incident starts');
 const requestId=id();assert.equal((await call(`/groups/${admin.groupId}/venue`,{requestId,areaId:'kyoto-gosho-south'},admin.token)).status,200);
 const checked=await Promise.all(Array.from({length:40},()=>call('/venue/check',{groupName,password})));assert.ok(checked.every(r=>r.status===200),JSON.stringify(checked.filter(r=>r.status!==200)));
 const inputs=Array.from({length:39},(_,n)=>({groupName,password,deviceId:id(),activityName:'参加者'+n,registrationToken:token()}));
 const joined=await Promise.all(inputs.map(input=>call('/venue/join',input)));assert.ok(joined.every(r=>r.status===200),JSON.stringify(joined.filter(r=>r.status!==200)));
 const before=await call(`/groups/${admin.groupId}/roster`,undefined,admin.token);assert.equal(before.body.roster.members.length,40);assert.equal(new Set(before.body.venue.placements.map(p=>p.slotId)).size,40);
 await mf.dispose();mf=new Miniflare(options);
 const replay=await call('/venue/join',inputs[0]);assert.equal(replay.status,200);assert.equal(replay.body.memberId,joined[0].body.memberId);assert.equal(replay.body.venue.incidentId,requestId);
 const after=await call(`/groups/${admin.groupId}/roster`,undefined,admin.token);assert.deepEqual(after.body.venue,before.body.venue);
 const ended=await call(`/groups/${admin.groupId}/records`,{records:[{collection:'quakes',record:{groupId:admin.groupId,eventId:id(),actorDeviceId:admin.deviceId,operatedAt:Date.now(),action:'END',training:true,targetId:requestId}}]},admin.token);assert.equal(ended.status,200,JSON.stringify(ended.body));
 assert.equal((await call('/venue/check',{groupName,password})).status,200);
 assert.equal((await call(`/groups/${admin.groupId}/roster`,undefined,admin.token)).body.venue.closed,true);
 const renamed=await call(`/groups/${admin.groupId}/name`,{groupName:'Archived runtime group'},admin.token);assert.equal(renamed.status,200,JSON.stringify(renamed.body));
 assert.deepEqual(renamed.body.venue,(await call(`/groups/${admin.groupId}/roster`,undefined,admin.token)).body.venue);
 const replacement=await call('/groups',{requestId:id(),deviceId:id(),groupName,password,activityName:'New administrator',registrationToken:token()});assert.equal(replacement.status,200);assert.notEqual(replacement.body.groupId,admin.groupId);
 await mf.dispose();mf=new Miniflare(options);
 assert.equal((await call('/venue/check',{groupName:'Archived runtime group',password})).body.groupId,admin.groupId);
 assert.equal((await call('/venue/check',{groupName,password})).body.groupId,replacement.body.groupId);

});
