import {ApiError,requireValue,uuid} from './validation.mjs';

export async function readJson(request){
 requireValue(request.headers.get('content-type')?.split(';')[0].trim()==='application/json','json_required',415);
 requireValue(Number(request.headers.get('content-length')??0)<=262144,'body_too_large',413);
 const reader=request.body?.getReader();requireValue(reader);let size=0;const chunks=[];
 try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>262144){await reader.cancel();throw new ApiError(413,'body_too_large');}chunks.push(value);}}finally{reader.releaseLock();}
 const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
 try{return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));}catch{throw new ApiError(400,'invalid_json');}
}
const headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'};
const needsResponseV2=row=>row?.collection==='responses'&&(row.record?.change?.status==='PLANNED'||['regionIds','cancelled','cancelReason'].some(key=>Object.hasOwn(row.record?.change??{},key)));
const requireResponseV2=(request,rows)=>requireValue(!Array.isArray(rows)||!rows.some(needsResponseV2)||['2','3','4','5','6','7','8','9'].includes(request.headers.get('X-PlanD-Records-Version')),'client_update_required',426,'このグループの新しい対応記録を読むには、アプリを最新版へ更新してください。端末内の記録は保持されています。');
const requireAddressV3=(request,rows)=>requireValue(!Array.isArray(rows)||!rows.some(r=>r.collection==='places'&&r.record?.locationReference)||['3','4','5','6','7','8','9'].includes(request.headers.get('X-PlanD-Records-Version')),'client_update_required',426,'建物の住所情報を共有するため、アプリを最新版へ更新してください。');
const requireDeviceV4=(request,rows)=>requireValue(!Array.isArray(rows)||!rows.some(r=>r.collection==='deviceRecords')||['4','5','6','7','8','9'].includes(request.headers.get('X-PlanD-Records-Version')),'client_update_required',426,'衝撃・デモ住居の記録を共有するため、アプリを最新版へ更新してください。');
const requireHomeV5=(request,rows)=>requireValue(!Array.isArray(rows)||!rows.some(r=>r.record?.locationReference?.searchReference||r.record?.home?.searchReference)||['5','6','7','8','9'].includes(request.headers.get('X-PlanD-Records-Version')),'client_update_required',426,'住居の検索情報を共有するため、アプリを更新してください。');
const requireInjuryV6=(request,rows)=>requireValue(!Array.isArray(rows)||!rows.some(r=>r.collection==='responses'&&r.record?.change?.type==='INJURY')||['6','7','8','9'].includes(request.headers.get('X-PlanD-Records-Version')),'client_update_required',426,'負傷情報を共有するため、アプリを最新版へ更新してください。');
// Every material snapshot requires v7: old readers can discard cancellation or reject changed units.
// Gate legacy-shaped material rows too, without modifying their signed payloads.
const requireMaterialV7=(request,rows)=>requireValue(!Array.isArray(rows)||!rows.some(r=>r.collection==='responses'&&r.record?.change?.type==='MATERIAL')||['7','8','9'].includes(request.headers.get('X-PlanD-Records-Version')),'client_update_required',426,'資材の取消・単位訂正を正しく共有するため、アプリを最新版へ更新してください。端末内の記録は保持されています。');
const requireAdminV8=(request,rows)=>requireValue(!Array.isArray(rows)||!rows.some(r=>r.collection==='quakes'&&r.record?.action==='ADMIN_START')||['8','9'].includes(request.headers.get('X-PlanD-Records-Version')),'client_update_required',426,'管理者による地震開始を共有するため、アプリを最新版へ更新してください。');
export async function handle(request,env){
 const url=new URL(request.url),origin=request.headers.get('origin'),allowed=new Set([url.origin,...(env.ALLOWED_ORIGINS??'').split(',').map(x=>x.trim()).filter(Boolean)]);
 const responseHeaders={...headers,...(origin&&allowed.has(origin)?{'Access-Control-Allow-Origin':origin,'Vary':'Origin'}:{})};
 const json=(body,status=200)=>Response.json(body,{status,headers:responseHeaders});
 try{
  if(!url.pathname.startsWith('/api/'))return env.ASSETS?await env.ASSETS.fetch(request):json({error:{code:'not_found'}},404);
  requireValue(!origin||allowed.has(origin),'origin_not_allowed',403);
  if(request.method==='OPTIONS')return new Response(null,{status:204,headers:{...responseHeaders,'Access-Control-Allow-Methods':'GET, POST, OPTIONS','Access-Control-Allow-Headers':'Authorization, Content-Type, X-PlanD-Records-Version','Access-Control-Max-Age':'600'}});
  if(['/api/v1/health','/api/v1/info'].includes(url.pathname)&&request.method==='GET')return json({service:'tasukeai-sync',version:1});
  const venueAccess=/^\/api\/v1\/venue\/(check|join|recover)$/.exec(url.pathname);
  const venueConfigure=/^\/api\/v1\/groups\/([^/]+)\/venue$/.exec(url.pathname);
  if(venueAccess||venueConfigure){
   requireValue(request.method==='POST','method_not_allowed',405);
   const input=await readJson(request);let operation,args;
   if(venueConfigure){
    const groupId=venueConfigure[1];requireValue(uuid(groupId));
    const token=request.headers.get('authorization')?.match(/^Bearer ([a-f0-9]{64})$/)?.[1];requireValue(token,'unauthorized',401);
    operation='directoryConfigure';args={groupId,token,input};
   }else {operation='directoryAccess';args={input,join:venueAccess[1]==='join',recover:venueAccess[1]==='recover',ip:request.headers.get('CF-Connecting-IP')??'local'};}
   const result=await env.GROUPS.getByName('venue-directory-v1').dispatch(operation,'',args);
   return json(result.body,result.status);
  }
  const create=url.pathname==='/api/v1/groups'&&request.method==='POST';
  const match=/^\/api\/v1\/groups\/([^/]+)\/(join|invitations|roster|records|nearby|relay-keys|municipality|access|venue-clock|member-permission)$/.exec(url.pathname);
  requireValue(create||match,'not_found',404);
  const action=create?'create':match[2];
  requireValue((['roster','nearby'].includes(action)&&request.method==='GET')||(['records','relay-keys'].includes(action)&&['GET','POST'].includes(request.method))||(['create','join','invitations','municipality','access','venue-clock','member-permission'].includes(action)&&request.method==='POST'),'method_not_allowed',405);
  if(['create','join'].includes(action)){
   const result=await env.SIGNUP_LIMITER.limit({key:request.headers.get('CF-Connecting-IP')??'local'});requireValue(result.success,'rate_limited',429);
  }
  const payload=request.method==='POST'?await readJson(request):null;
  const groupId=create?payload?.requestId:match[1];requireValue(uuid(groupId));
  const token=request.headers.get('authorization')?.match(/^Bearer ([a-f0-9]{64})$/)?.[1]??'';
  if(!['create','join'].includes(action))requireValue(token,'unauthorized',401);
  const operation=action==='member-permission'?'setMemberPermission':action==='venue-clock'?'setVenueClock':action==='municipality'?'setMunicipality':action==='records'?(request.method==='GET'?'pull':'push'):action==='relay-keys'?(request.method==='GET'?'relayKeys':'registerRelayKey'):action==='invitations'?'invite':action;
  const args=operation==='create'?{...payload,groupId}:operation==='pull'?{after:Number(url.searchParams.get('after')??0),limit:Number(url.searchParams.get('limit')??100)}:payload;
  if(operation==='push'){requireResponseV2(request,payload?.records);requireAddressV3(request,payload?.records);requireDeviceV4(request,payload?.records);requireHomeV5(request,payload?.records);requireInjuryV6(request,payload?.records);requireMaterialV7(request,payload?.records);requireAdminV8(request,payload?.records);}
  const result=create&&payload.password!==undefined?await env.GROUPS.getByName('venue-directory-v1').dispatch('directoryCreate','',args):await env.GROUPS.getByName(groupId).dispatch(operation,token,args);
  // Never acknowledge a cursor while an older client would lose cancellation or planned status.
  if(operation==='pull'&&result.status===200){requireResponseV2(request,result.body?.records);requireAddressV3(request,result.body?.records);requireDeviceV4(request,result.body?.records);requireHomeV5(request,result.body?.records);requireInjuryV6(request,result.body?.records);requireMaterialV7(request,result.body?.records);requireAdminV8(request,result.body?.records);}
  if(result.status===200&&result.body?.rosterHistory?.some(h=>h.roster.members.some(m=>Object.hasOwn(m,'canTriage'))))requireValue(request.headers.get('X-PlanD-Records-Version')==='9','client_update_required',426,'権限設定を利用するため、アプリを最新版へ更新してください。');
  return json(result.body,result.status);
 }catch(e){if(e instanceof ApiError)return json({error:{code:e.code,message:e.message}},e.status);return json({error:{code:'storage_unavailable',message:'保存を確認できませんでした。同じ記録を再送してください。'}},503);}
}
