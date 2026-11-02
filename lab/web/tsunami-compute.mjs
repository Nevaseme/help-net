/** Browser computation adapter. Every input runs the solver; no nearest-case fallback. */
const check=(condition,message)=>{if(!condition)throw Error(message);};
const finite=value=>typeof value==='number'&&Number.isFinite(value);
const coordinate=p=>Array.isArray(p)&&p.length===2&&p.every(finite)&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;
const abortError=()=>new DOMException('計算を中止しました','AbortError');
function waitForTerrain(promise,signal){
 if(!signal)return promise;
 return new Promise((resolve,reject)=>{
  const abort=()=>{signal.removeEventListener('abort',abort);reject(abortError());};
  signal.addEventListener('abort',abort,{once:true});
  promise.then(value=>{signal.removeEventListener('abort',abort);resolve(value);},error=>{signal.removeEventListener('abort',abort);reject(error);});
  if(signal.aborted)abort();
 });
}
export function parseTsunamiHeight(input){
 check((typeof input==='number'||typeof input==='string')&&String(input).trim()!=='','津波の高さをメートルで入力してください');
 const value=Number(input);
 check(finite(value)&&value>=0,'津波の高さは0以上の数値を入力してください');
 return value;
}
export function validateTerrainPack(pack){
 check(pack?.schema==='pland-tsunami-browser-domain-1','計算用の地形を確認できません');
 const c=pack.config,n=c?.nx*c?.ny;
 check(Number.isSafeInteger(c?.nx)&&c.nx>0&&Number.isSafeInteger(c?.ny)&&c.ny>0&&n<=100000,'計算範囲を確認できません');
 check(finite(c.dx)&&c.dx>0&&finite(c.dy)&&c.dy>0&&finite(c.baseline_m),'地形の単位を確認できません');
 check(Array.isArray(c.bed)&&c.bed.length===n&&c.bed.every(finite),'地形に欠損があります');
 check(Array.isArray(pack.nodes_lonlat)&&pack.nodes_lonlat.length===(c.nx+1)*(c.ny+1)&&pack.nodes_lonlat.every(coordinate),'地図の座標を確認できません');
 check(Number.isSafeInteger(c.reference_index)&&c.reference_index>=0&&c.reference_index<n&&c.bed[c.reference_index]<c.baseline_m,'海岸の基準点を確認できません');
 check(coordinate(pack.reference_point),'海岸の座標を確認できません');
 check(pack.provenance?.vertical_datum_status==='verified'&&/^[a-f0-9]{64}$/.test(pack.provenance.source_sha256)&&Array.isArray(pack.provenance.sources)&&pack.provenance.sources.length>0,'地形の出典を確認できません');
 check(typeof pack.solver?.version==='string'&&/^[a-f0-9]{64}$/.test(pack.solver.implementation_sha256),'計算処理の版を確認できません');
 check(pack.review?.status==='experimental-checked','地形と計算範囲を検証中です。検証前の結果は表示しません');
 if(pack.display_cells!==undefined)check(Array.isArray(pack.display_cells)&&pack.display_cells.length===n&&pack.display_cells.every(x=>typeof x==='boolean'),'表示範囲を確認できません');
 const w=pack.reporting_window;
 if(w)check(Array.isArray(w)&&w.length===4&&w.every(Number.isSafeInteger)&&w[0]>=0&&w[1]>=0&&w[2]<=c.nx&&w[3]<=c.ny&&w[2]>w[0]&&w[3]>w[1],'表示範囲の地形を確認できません');
 if(Object.values(c.boundaries||{}).some(b=>b.type==='transmissive'))check(w&&pack.comparison,'端の影響を照合する計算条件がありません');
 if(pack.comparison){
  const b=pack.comparison.config,mapping=pack.comparison.primary_to_secondary;
  check(w&&Number.isSafeInteger(b?.nx)&&b.nx>0&&Number.isSafeInteger(b.ny)&&b.ny>0&&b.nx*b.ny<=100000,'照合用の範囲を確認できません');
  check(Array.isArray(b.bed)&&b.bed.length===b.nx*b.ny&&b.bed.every(finite)&&Array.isArray(mapping)&&mapping.length===n,'照合用の地形に欠損があります');
  check(b.nx!==c.nx||b.ny!==c.ny||pack.conversion?.origin_m?.some((v,i)=>v!==pack.comparison.conversion?.origin_m?.[i]),'広さや位置の違う範囲との照合が必要です');
  for(const key of ['dx','dy','baseline_m','duration_s','manning_n'])check(b[key]===c[key],'照合する計算条件が一致しません');
  for(const key of ['boundary','pulse_duration_s','pulse_delay_s','tolerance_m'])check(b.calibration?.[key]===c.calibration?.[key],'照合する波の条件が一致しません');
  check(mapping[c.reference_index]===b.reference_index,'照合する海岸基準点が一致しません');
  for(let y=w[1];y<w[3];y++)for(let x=w[0];x<w[2];x++){
   const i=y*c.nx+x,j=mapping[i];check(Number.isSafeInteger(j)&&j>=0&&j<b.bed.length&&Math.abs(c.bed[i]-b.bed[j])<=1e-8,'照合する地形の位置が一致しません');
  }
 }
 return pack;
}
function validateOutput(pack,output,heightM,c=pack.config){
 const rejectionMessages={
  inundation_reaches_closed_cut_boundary:'津波が計算範囲の端に届いたため、浸水範囲を確定できません。結果は表示していません',
  supercritical_open_boundary:'この波では海側の計算条件を満たせません。結果は表示していません',
  target_outside_forcing_bracket:'この高さは現在の地形・波の計算条件で求められる範囲を超えています',
  calibration_iteration_limit:'指定した津波高に計算を合わせられませんでした。結果は表示していません'
 };
 check(output?.status==='accepted',rejectionMessages[output?.reason]||'この高さでは計算範囲や精度の条件を満たせませんでした。結果は表示していません');
 const r=output.result,depths=r?.maxDepth,n=c.nx*c.ny;
 check(r?.diagnostics?.completed===true&&output.calibration?.target_height_m===heightM,'計算の完了と指定した高さを確認できません');
 check(r.diagnostics.solver===pack.solver.version,'地形の検証時と計算処理の版が違います。ページを更新してください');
 check(r.boundaryAudit?.inundation_reaches_closed_cut_boundary===false&&r.boundaryAudit?.supercritical_open_boundary===false,'計算範囲の端の影響を除外できません');
 check((Array.isArray(depths)||ArrayBuffer.isView(depths))&&depths.length===n&&Array.from(depths).every(x=>finite(x)&&x>=0),'計算結果の水深を確認できません');
 const achieved=r.actualGaugeHeight,tolerance=c.calibration?.tolerance_m;
 check(finite(achieved)&&finite(tolerance)&&tolerance>0&&Math.abs(achieved-heightM)<=tolerance+1e-8,'指定した津波の高さに達したことを確認できません');
 return r;
}
/** Fixed, predeclared numerical-boundary sensitivity screen, not forecast accuracy. */
export function compareBoundarySensitivity(pack,first,second,heightM){
 validateTerrainPack(pack);const a=validateOutput(pack,first,heightM),b=validateOutput(pack,second,heightM,pack.comparison.config);
 const c=pack.config,w=pack.reporting_window,mapping=pack.comparison.primary_to_secondary;
 let maximum=0,total=0,union=0,intersection=0;
 for(let y=w[1];y<w[3];y++)for(let x=w[0];x<w[2];x++){
  const i=y*c.nx+x;if(c.bed[i]<c.baseline_m)continue;
  const da=a.maxDepth[i],db=b.maxDepth[mapping[i]],difference=Math.abs(da-db);
  maximum=Math.max(maximum,difference);
  if(da>=.1||db>=.1){union++;total+=difference;if(da>=.1&&db>=.1)intersection++;}
 }
 const metrics={meanWetDepthDifferenceM:union?total/union:0,maximumDepthDifferenceM:maximum,footprintIoU:union?intersection/union:1,depthThresholdM:.1,limits:{meanWetDepthDifferenceM:.1,maximumDepthDifferenceM:.5,footprintIoU:.9}};
 check(metrics.meanWetDepthDifferenceM<=.1&&maximum<=.5&&metrics.footprintIoU>=.9,'計算範囲を変えると結果が大きく変わるため、この高さの浸水範囲は表示できません');
 return metrics;
}
export function mapComputedResult(pack,output,heightM,comparisonOutput){
 validateTerrainPack(pack);heightM=parseTsunamiHeight(heightM);const c=pack.config,r=validateOutput(pack,output,heightM),depths=r.maxDepth,n=c.nx*c.ny,achieved=r.actualGaugeHeight;
 const comparison=pack.comparison?compareBoundarySensitivity(pack,output,comparisonOutput,heightM):null;
 const polygon=ring=>({type:'Polygon',coordinates:[[...ring,ring[0]]]});
 const node=(x,y)=>pack.nodes_lonlat[y*(c.nx+1)+x];
 const features=[];let area=0,maximum=0;
 for(let i=0;i<n;i++){
  if(c.bed[i]<c.baseline_m||pack.display_cells?.[i]===false||depths[i]<(comparison?.depthThresholdM??.01))continue;
  const x=i%c.nx,y=Math.floor(i/c.nx),w=pack.reporting_window;
  if(w&&(x<w[0]||x>=w[2]||y<w[1]||y>=w[3]))continue;
  area+=c.dx*c.dy;maximum=Math.max(maximum,depths[i]);
  features.push({type:'Feature',properties:{cell:i,max_depth_m:depths[i],bed_elevation_m:c.bed[i]},geometry:polygon([node(x,y),node(x+1,y),node(x+1,y+1),node(x,y+1)])});
 }
 const w=pack.reporting_window||[0,0,c.nx,c.ny],ring=[node(w[0],w[1]),node(w[2],w[1]),node(w[2],w[3]),node(w[0],w[3])];
 const coverage={type:'FeatureCollection',features:[{type:'Feature',properties:{},geometry:polygon(ring)}]};
 let west=180,east=-180,south=90,north=-90;
 for(const p of ring){west=Math.min(west,p[0]);east=Math.max(east,p[0]);south=Math.min(south,p[1]);north=Math.max(north,p[1]);}
 return {heightM,achievedHeightM:achieved,geojson:{type:'FeatureCollection',features},coverage,referencePoint:pack.reference_point,bounds:[[west,south],[east,north]],metadata:{id:pack.id,domainId:pack.id,calibration:output.calibration,diagnostics:r.diagnostics,boundaryAudit:r.boundaryAudit,boundaryComparison:comparison,provenance:pack.provenance,inundatedAreaM2:area,maximumDepthM:maximum,displayDepthThresholdM:comparison?.depthThresholdM??.01,realWorldValidated:false,description:'海岸基準地点の水位上昇を指定し、同じ10分間の押し波・初期水位・地形条件で計算しています。実際の津波の予測ではありません。'+(comparison?' 熱海中心部の表示範囲について、広さの違う2つの計算を照合し、浸水深10cm以上を表示しています。範囲外の浸水を否定するものではありません。':''),definition:'Fixed-duration conditional wave; results within declared reporting window only; not global inundation extent.'}};
}
export function createTsunamiComputer({loadPack,createWorker}){
 return async function compute(height,{onProgress=()=>{},signal}={}){
  const heightM=parseTsunamiHeight(height);if(signal?.aborted)throw abortError();
  onProgress({message:'地形を準備しています'});
  const pack=validateTerrainPack(await waitForTerrain(Promise.resolve().then(loadPack),signal));if(signal?.aborted)throw abortError();
  const run=(config,phase)=>new Promise((resolve,reject)=>{
   const worker=createWorker(),id=crypto.randomUUID();let settled=false;
   const finish=(error,result)=>{if(settled)return;settled=true;signal?.removeEventListener('abort',abort);worker.terminate();error?reject(error):resolve(result);};
   const abort=()=>finish(abortError());signal?.addEventListener('abort',abort,{once:true});
   worker.onmessage=({data})=>{
    if(settled||data?.id!==id)return;
    if(data.type==='progress'){onProgress({...data.progress,message:phase});return;}
    if(data.type==='error'){finish(Error('計算を完了できませんでした。入力した高さと計算条件を確認してください'));return;}
    if(data.type==='result'){try{validateOutput(pack,data,heightM,config);finish(null,data);}catch(error){finish(error);}}
   };
   worker.onerror=()=>finish(Error('計算処理を開始できませんでした。ページを開き直してください'));
   if(signal?.aborted){abort();return;}
   onProgress({message:phase});
   try{worker.postMessage({type:'run',id,config,targetHeight:heightM});}catch(error){finish(error);}
  });
  const first=await run(pack.config,'津波の広がりを計算しています');
  const second=pack.comparison?await run(pack.comparison.config,'範囲を変えて、端の影響を確認しています'):undefined;
  if(signal?.aborted)throw abortError();
  return mapComputedResult(pack,first,heightM,second);
 };
}
let cachedPack;
export const computeTsunamiHeight=createTsunamiComputer({
 loadPack:async()=>{
  if(cachedPack)return cachedPack;
  const response=await fetch(new URL('./tsunami-domain-atami.json',import.meta.url));
  check(response.ok,'計算用の地形を取得できません。通信できる状態で開き直してください');
  const pack=validateTerrainPack(await response.json());
  const implementation=await fetch(new URL('./tsunami-solver.mjs',import.meta.url));
  check(implementation.ok,'計算処理を取得できません');
  const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',await implementation.arrayBuffer())),v=>v.toString(16).padStart(2,'0')).join('');
  check(digest===pack.solver.implementation_sha256,'地形の検証時と計算処理が違います。ページを更新してください');
  cachedPack=pack;return pack;
 },
 createWorker:()=>new Worker(new URL('./tsunami-solver-worker.mjs',import.meta.url),{type:'module'})
});
