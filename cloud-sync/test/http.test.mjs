import {test} from 'node:test';
import assert from 'node:assert/strict';
import {handle,readJson} from '../src/http.mjs';

test('all material snapshots require v7 before upload writes or pull cursor acknowledgement',async()=>{
 const path=`https://sync.test/api/v1/groups/${crypto.randomUUID()}/records`;
 for(const change of [{type:'MATERIAL',unit:'本',needed:5,arranged:1},{type:'MATERIAL',unit:'箱',needed:1,arranged:0},{type:'MATERIAL',cancelled:true,cancelReason:'不要'},{type:'MATERIAL',cancelled:false,cancelReason:''}]){
  const rows=[{cursor:7,collection:'responses',record:{change}}];let writes=0;
  const env={GROUPS:{getByName:()=>({dispatch:async op=>{if(op==='push')writes++;return {status:200,body:{records:rows,nextCursor:7,acks:[{status:'stored'}]}};}})}};
  for(const version of [undefined,'1','2','3','4','5','6']){
   const headers={Authorization:`Bearer ${'a'.repeat(64)}`,'Content-Type':'application/json',...(version?{'X-PlanD-Records-Version':version}:{})};
   for(const method of ['GET','POST']){
    const response=await handle(new Request(path,{method,headers,...(method==='POST'?{body:JSON.stringify({records:rows})}:{})}),env);
    assert.equal(response.status,426);const body=await response.json();assert.equal(body.error.code,'client_update_required');assert.equal(body.nextCursor,undefined);assert.equal(body.acks,undefined);
   }
  }
  assert.equal(writes,0);
  const headers={Authorization:`Bearer ${'a'.repeat(64)}`,'Content-Type':'application/json','X-PlanD-Records-Version':'7'};
  const pull=await handle(new Request(path,{headers}),env);assert.equal(pull.status,200);assert.deepEqual((await pull.json()).records,rows);
  const push=await handle(new Request(path,{headers,method:'POST',body:JSON.stringify({records:rows})}),env);assert.equal(push.status,200);assert.equal(writes,1);
 }
});

test('v7 accepts every earlier capability and v6 still reads non-material records',async()=>{
 const path=`https://sync.test/api/v1/groups/${crypto.randomUUID()}/records`,rows=[
  {collection:'responses',record:{change:{type:'REPORT',cancelled:true,cancelReason:'重複',regionIds:['north']}}},
  {collection:'responses',record:{change:{type:'PROGRESS',status:'PLANNED'}}},
  {collection:'places',record:{locationReference:{searchReference:{source:'ISJ'}}}},
  {collection:'deviceRecords',record:{home:{searchReference:{source:'ISJ'}}}},
  {collection:'responses',record:{change:{type:'INJURY',state:'INJURED'}}}
 ];
 const env={GROUPS:{getByName:()=>({dispatch:async()=>({status:200,body:{records:rows,nextCursor:7}})})}};
 for(const version of ['6','7'])for(const method of ['GET','POST']){
  const headers={Authorization:`Bearer ${'a'.repeat(64)}`,'Content-Type':'application/json','X-PlanD-Records-Version':version};
  assert.equal((await handle(new Request(path,{method,headers,...(method==='POST'?{body:JSON.stringify({records:rows})}:{})}),env)).status,200);
 }
});

test('address references require v3 before read cursor or upload acknowledgement',async()=>{
 const path=`https://sync.test/api/v1/groups/${crypto.randomUUID()}/records`,rows=[{collection:'places',record:{locationReference:{address:'試験町'}}}];let writes=0;
 const env={GROUPS:{getByName:()=>({dispatch:async op=>{if(op==='push')writes++;return {status:200,body:{records:rows,nextCursor:3}};}})}};
 const headers={Authorization:`Bearer ${'a'.repeat(64)}`,'X-PlanD-Records-Version':'2','Content-Type':'application/json'};
 assert.equal((await handle(new Request(path,{headers}),env)).status,426);
 assert.equal((await handle(new Request(path,{headers,method:'POST',body:JSON.stringify({records:rows})}),env)).status,426);assert.equal(writes,0);
 assert.equal((await handle(new Request(path,{headers:{...headers,'X-PlanD-Records-Version':'3'}}),env)).status,200);
});

test('API discovery is public, no-store and same-origin; denied origins never dispatch',async()=>{
 const r=await handle(new Request('https://sync.test/api/v1/info'),{});assert.equal(r.status,200);assert.equal(r.headers.get('cache-control'),'no-store');assert.equal((await r.json()).service,'tasukeai-sync');
 const bad=await handle(new Request('https://sync.test/api/v1/info',{headers:{Origin:'https://evil.test'}}),{});assert.equal(bad.status,403);
 const allowed=await handle(new Request('https://sync.test/api/v1/info',{headers:{Origin:'https://app.test'}}),{ALLOWED_ORIGINS:'https://app.test'});assert.equal(allowed.headers.get('access-control-allow-origin'),'https://app.test');
});
test('bounded JSON input rejects oversize including absent content-length',async()=>{
 await assert.rejects(readJson(new Request('https://sync.test',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({x:'a'.repeat(262144)})})),{code:'body_too_large'});
 await assert.rejects(readJson(new Request('https://sync.test',{method:'POST',headers:{'content-type':'text/plain'},body:'{}'})),{code:'json_required'});
});
test('unknown operations and invalid group IDs do not access durable namespace',async()=>{
 const env={GROUPS:{getByName(){throw Error('must not reach storage');}}};
 assert.equal((await handle(new Request('https://sync.test/api/v1/groups/nope/records'),env)).status,400);
 assert.equal((await handle(new Request('https://sync.test/api/v1/groups/nope/delete',{method:'DELETE'}),env)).status,404);
});

test('extended response records require a capable client before cursor or write acknowledgement',async()=>{
 const group=crypto.randomUUID(),token='a'.repeat(64),path=`https://sync.test/api/v1/groups/${group}/records`;
 for(const change of [{type:'PROGRESS',status:'PLANNED'},{type:'REPORT',cancelled:true,cancelReason:'重複'},{type:'REPORT',regionIds:['north']}]){
  const rows=[{cursor:3,collection:'responses',record:{change}}];let writes=0;
  const env={GROUPS:{getByName:()=>({dispatch:async op=>{if(op==='push')writes++;return {status:200,body:{records:rows,nextCursor:3,hasMore:false}};}})}};
  const headers={Authorization:`Bearer ${token}`};
  const legacy=await handle(new Request(path,{headers}),env);assert.equal(legacy.status,426);const error=await legacy.json();assert.equal(error.error.code,'client_update_required');assert.equal(error.nextCursor,undefined);
  const updated=await handle(new Request(path,{headers:{...headers,'X-PlanD-Records-Version':'2'}}),env);assert.equal(updated.status,200);assert.deepEqual((await updated.json()).records,rows);
  const upload=await handle(new Request(path,{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({records:rows})}),env);assert.equal(upload.status,426);assert.equal(writes,0);
 }
});

test('unchanged legacy records remain readable and capability header is allowed by CORS',async()=>{
 const group=crypto.randomUUID(),path=`https://sync.test/api/v1/groups/${group}/records`,rows=[{collection:'responses',record:{change:{type:'REPORT',description:'既存'}}}];
 const env={GROUPS:{getByName:()=>({dispatch:async()=>({status:200,body:{records:rows,nextCursor:1}})})}};
 assert.equal((await handle(new Request(path,{headers:{Authorization:`Bearer ${'a'.repeat(64)}`}}),env)).status,200);
 const preflight=await handle(new Request(path,{method:'OPTIONS',headers:{Origin:'https://sync.test'}}),env);assert.match(preflight.headers.get('Access-Control-Allow-Headers'),/X-PlanD-Records-Version/);
});

test('home search provenance requires v5 and never acknowledges an old client',async()=>{
 const path=`https://sync.test/api/v1/groups/${crypto.randomUUID()}/records`;
 for(const rows of [[{collection:'places',record:{locationReference:{searchReference:{source:'ISJ'}}}}],[{collection:'deviceRecords',record:{kind:'DEMO_HOME',home:{searchReference:{source:'ISJ'}}}}]]){
  let writes=0;const env={GROUPS:{getByName:()=>({dispatch:async op=>{if(op==='push')writes++;return {status:200,body:{records:rows,nextCursor:4}};}})}};
  const headers={Authorization:`Bearer ${'a'.repeat(64)}`,'X-PlanD-Records-Version':'4','Content-Type':'application/json'};
  assert.equal((await handle(new Request(path,{headers}),env)).status,426);
  assert.equal((await handle(new Request(path,{headers,method:'POST',body:JSON.stringify({records:rows})}),env)).status,426);assert.equal(writes,0);
  assert.equal((await handle(new Request(path,{headers:{...headers,'X-PlanD-Records-Version':'5'}}),env)).status,200);
 }
});

test('administrator start requires v8 before writes or cursor acknowledgement',async()=>{
 const url=`https://sync.test/api/v1/groups/${crypto.randomUUID()}/records`,rows=[{collection:'quakes',record:{action:'ADMIN_START'}}];let writes=0;
 const env={GROUPS:{getByName:()=>({dispatch:async op=>{if(op==='push')writes++;return {status:200,body:{records:rows,nextCursor:1,acks:[{status:'stored'}]}};}})}};
 for(const version of ['1','7','8','9'])for(const method of ['GET','POST']){
  const r=await handle(new Request(url,{method,headers:{Authorization:`Bearer ${'a'.repeat(64)}`,'Content-Type':'application/json','X-PlanD-Records-Version':version},...(method==='POST'?{body:JSON.stringify({records:rows})}:{})}),env);assert.equal(r.status,['8','9'].includes(version)?200:426);const body=await r.json();if(!['8','9'].includes(version)){assert.equal(body.nextCursor,undefined);assert.equal(body.acks,undefined);}
 }assert.equal(writes,2);
});

test('permission-aware roster requires v9 and preserves older client records',async()=>{
 const path=`https://sync.test/api/v1/groups/${crypto.randomUUID()}/roster`;
 const envelope={rosterHistory:[{roster:{members:[{canTriage:true}]}}]};
 const env={GROUPS:{getByName:()=>({dispatch:async()=>({status:200,body:envelope})})}};
 for(const version of ['8','9']){const response=await handle(new Request(path,{headers:{Authorization:`Bearer ${'a'.repeat(64)}`,'X-PlanD-Records-Version':version}}),env);assert.equal(response.status,version==='9'?200:426);}
});
