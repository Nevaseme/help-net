/** Exact-height, prevalidated results. No interpolation or live simulation. */
const check=(ok,message)=>{if(!ok)throw Error(message);};
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const hash=x=>typeof x==='string'&&/^[a-f0-9]{64}$/.test(x);
const coord=p=>Array.isArray(p)&&p.length===2&&p.every(finite)&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;
const close=(a,b)=>finite(a)&&finite(b)&&Math.abs(a-b)<=1e-8;
export const PROOF_CALIBRATION_FIELDS=['target_height_m','achieved_height_m','residual_m','tolerance_m','forcing_amplitude_m','reference_index','baseline_maximum_depth_drift_m'];
export const PROOF_DIAGNOSTIC_FIELDS=['solver','completed','simulated_s','initial_volume_m3','final_volume_m3','net_boundary_volume_m3','roundoff_volume_m3','mass_residual_m3','relative_mass_residual'];
export const PROOF_BOUNDARY_FIELDS=['inundation_reaches_closed_cut_boundary','closed_cut_inundated_length_m','supercritical_open_boundary'];
export function validateCalculationProof(proof,contract,height,solver){
 check(contract&&typeof contract.id==='string'&&hash(contract.sha256)&&finite(contract.duration_s)&&contract.duration_s>0&&finite(contract.tolerance_m)&&contract.tolerance_m>0&&finite(contract.maximum_forcing_m)&&contract.maximum_forcing_m>0&&Number.isSafeInteger(contract.reference_index)&&contract.reference_index>=0&&finite(contract.reference_bed_m)&&finite(contract.baseline_m)&&contract.reference_bed_m<contract.baseline_m,'計算条件の検証記録がありません');
 check(proof?.domain_id===contract.id&&proof.source?.domain_sha256===contract.sha256&&proof.source?.solver_sha256===solver.implementation_sha256,'計算原本と地形・計算処理の出典が一致しません');
 const c=proof.calibration,d=proof.diagnostics,b=proof.boundary_audit;
 const tolerance=Math.min(contract.tolerance_m,height===0?.001:height/4);
 check(c?.target_height_m===height&&c.reference_index===contract.reference_index&&close(c.tolerance_m,tolerance)&&close(proof.achieved_height_m,c.achieved_height_m)&&close(c.residual_m,proof.achieved_height_m-height)&&Math.abs(c.residual_m)<=tolerance+1e-8,'校正記録と指定高が一致しません');
 check(finite(c.forcing_amplitude_m)&&c.forcing_amplitude_m>=0&&c.forcing_amplitude_m<=contract.maximum_forcing_m&&finite(c.baseline_maximum_depth_drift_m)&&c.baseline_maximum_depth_drift_m>=0&&c.baseline_maximum_depth_drift_m<=Math.min(.001,tolerance),'静水と校正条件の検査に合格していません');
 check(finite(proof.reference_depth_m)&&proof.reference_depth_m>=0&&close(proof.reference_depth_m+contract.reference_bed_m-contract.baseline_m,proof.achieved_height_m),'基準点の水深と実現高が一致しません');
 check(d?.completed===true&&d.solver===solver.version&&close(d.simulated_s,contract.duration_s),'計算の完了時刻を確認できません');
 for(const key of PROOF_DIAGNOSTIC_FIELDS.slice(3))check(finite(d[key]),'水量収支の記録が不完全です');
 const mass=d.final_volume_m3-d.initial_volume_m3-d.net_boundary_volume_m3-d.roundoff_volume_m3;
 const relative=Math.abs(mass)/Math.max(1,d.initial_volume_m3,Math.abs(d.net_boundary_volume_m3));
 check(d.initial_volume_m3>=0&&d.final_volume_m3>=0&&close(d.mass_residual_m3,mass)&&d.relative_mass_residual>=0&&d.relative_mass_residual<=1e-8&&relative<=1e-8&&Math.abs(d.relative_mass_residual-relative)<=1e-12,'水量収支の検査に合格していません');
 check(b?.inundation_reaches_closed_cut_boundary===false&&b.closed_cut_inundated_length_m===0&&b.supercritical_open_boundary===false,'計算範囲の境界検査に合格していません');
 return proof;
}
export function parsePrecomputedHeight(input){
 check((typeof input==='number'||typeof input==='string')&&String(input).trim()!=='','津波高を入力してください');
 const h=Number(input);check(Number.isInteger(h)&&h>=0&&h<=35,'津波高は0〜35mの範囲で、1m刻みで入力してください');return h;
}
export function validatePrecomputedBundle(bundle){
 check(bundle?.schema==='pland-tsunami-precomputed-1'&&bundle.review?.status==='experimental-checked','事前計算データの検証を確認できません');
 check(bundle.minimum_height_m===0&&bundle.maximum_height_m===35&&bundle.step_m===1,'事前計算した高さの範囲が一致しません');
 const g=bundle.grid,n=g?.nx*g?.ny;
 check(Number.isSafeInteger(g?.nx)&&g.nx>0&&Number.isSafeInteger(g?.ny)&&g.ny>0&&n<=100000&&finite(g.dx)&&g.dx>0&&finite(g.dy)&&g.dy>0&&finite(g.baseline_m),'表示する地形を確認できません');
 check(Array.isArray(g.bed)&&g.bed.length===n&&g.bed.every(finite),'地形に欠損があります');
 if(g.display_cells!==undefined)check(Array.isArray(g.display_cells)&&g.display_cells.length===n&&g.display_cells.every(x=>typeof x==='boolean'),'表示対象の記録を確認できません');
 check(g.reference_index===null||(Number.isSafeInteger(g.reference_index)&&g.reference_index>=0&&g.reference_index<n),'基準点の位置を確認できません');
 check(Array.isArray(g.nodes_lonlat)&&g.nodes_lonlat.length===(g.nx+1)*(g.ny+1)&&g.nodes_lonlat.every(coord)&&coord(bundle.reference_point),'地図の座標を確認できません');
 check(hash(bundle.solver?.implementation_sha256)&&typeof bundle.solver.version==='string'&&bundle.provenance?.vertical_datum_status==='verified'&&hash(bundle.provenance.source_sha256),'計算処理と地形の出典を確認できません');
 check(bundle.validation_contracts?.primary?.baseline_m===g.baseline_m&&bundle.validation_contracts?.secondary?.baseline_m===g.baseline_m,'表示地形と計算の基準水位が一致しません');
 check(Array.isArray(bundle.cases)&&bundle.cases.length===36,'0〜35mの事前計算がそろっていません');
 for(let height=0;height<=35;height++){
  const c=bundle.cases[height],b=c?.boundary_comparison;
  check(c?.height_m===height&&c.calibration?.target_height_m===height&&finite(c.achieved_height_m)&&finite(c.calibration.tolerance_m)&&c.calibration.tolerance_m>0&&Math.abs(c.achieved_height_m-height)<=c.calibration.tolerance_m+1e-8,'指定高と事前計算結果が一致しません');
  check(c.diagnostics?.completed===true&&c.diagnostics.solver===bundle.solver.version&&c.boundary_audit?.inundation_reaches_closed_cut_boundary===false&&c.boundary_audit?.supercritical_open_boundary===false,'不採用の計算結果が含まれています');
  check(b&&b.depthThresholdM===.1&&finite(b.meanWetDepthDifferenceM)&&b.meanWetDepthDifferenceM>=0&&b.meanWetDepthDifferenceM<=.1&&finite(b.maximumDepthDifferenceM)&&b.maximumDepthDifferenceM>=0&&b.maximumDepthDifferenceM<=.5&&finite(b.footprintIoU)&&b.footprintIoU>=.9&&b.footprintIoU<=1,'計算範囲の端の検証を確認できません');
  check(hash(c.source_hashes?.primary)&&hash(c.source_hashes?.secondary),'計算原本を確認できません');
  check(Array.isArray(c.max_depth_m)&&c.max_depth_m.length===n&&c.max_depth_m.every(x=>finite(x)&&x>=0),'事前計算の水深に欠損があります');
  for(const side of ['primary','secondary']){
   const contract=bundle.validation_contracts?.[side],source=bundle.source_domains?.[side];
   check(source&&source.id===contract?.id&&source.sha256===contract.sha256,'原本の出典と検査条件が一致しません');
   validateCalculationProof(c.source_proofs?.[side],contract,height,bundle.solver);
  }
  const primary=c.source_proofs.primary;
  check(close(primary.achieved_height_m,c.achieved_height_m),'実現高の検査記録が一致しません');
  for(const [group,fields] of [['calibration',PROOF_CALIBRATION_FIELDS],['diagnostics',PROOF_DIAGNOSTIC_FIELDS],['boundary_audit',PROOF_BOUNDARY_FIELDS]])for(const key of fields)check(primary[group][key]===c[group][key],'計算結果と検査記録が一致しません');
  if(g.reference_index!==null)check(close(g.bed[g.reference_index],bundle.validation_contracts.primary.reference_bed_m)&&close(c.max_depth_m[g.reference_index],primary.reference_depth_m),'表示水深と基準点の検査記録が一致しません');
 }
 return bundle;
}
export function mapPrecomputedResult(bundle,input){
 validatePrecomputedBundle(bundle);const heightM=parsePrecomputedHeight(input),c=bundle.cases[heightM],g=bundle.grid;
 const node=(x,y)=>g.nodes_lonlat[y*(g.nx+1)+x];
 const polygon=ring=>({type:'Polygon',coordinates:[[...ring,ring[0]]]});
 const features=[];let area=0,maximum=0;
 for(let y=0;y<g.ny;y++)for(let x=0;x<g.nx;x++){
  const i=y*g.nx+x,depth=c.max_depth_m[i];if(g.display_cells?.[i]===false||g.bed[i]<g.baseline_m||depth<.1)continue;
  area+=g.dx*g.dy;maximum=Math.max(maximum,depth);
  features.push({type:'Feature',properties:{cell:i,max_depth_m:depth,bed_elevation_m:g.bed[i]},geometry:polygon([node(x,y),node(x+1,y),node(x+1,y+1),node(x,y+1)])});
 }
 const ring=[node(0,0),node(g.nx,0),node(g.nx,g.ny),node(0,g.ny)];
 const bounds=[[Math.min(...ring.map(p=>p[0])),Math.min(...ring.map(p=>p[1]))],[Math.max(...ring.map(p=>p[0])),Math.max(...ring.map(p=>p[1]))]];
 return {heightM,achievedHeightM:c.achieved_height_m,geojson:{type:'FeatureCollection',features},coverage:{type:'FeatureCollection',features:[{type:'Feature',properties:{},geometry:polygon(ring)}]},referencePoint:bundle.reference_point,bounds,
  metadata:{id:bundle.id+'-h'+heightM,domainId:bundle.domain_id,precomputed:true,calibration:c.calibration,diagnostics:c.diagnostics,boundaryAudit:c.boundary_audit,boundaryComparison:c.boundary_comparison,sourceHashes:c.source_hashes,provenance:bundle.provenance,inundatedAreaM2:area,maximumDepthM:maximum,displayDepthThresholdM:.1,realWorldValidated:false,
   description:'海岸基準地点の水位上昇を指定した、同じ10分間の押し波の事前計算です。広さの違う2領域を照合し、熱海中心部の浸水深10cm以上を表示します。実際の津波の予測ではなく、枠の外にも浸水が続く可能性があります。'}};
}
export function createPrecomputedComputer({loadBundle}){
 let cached;
 return async(input,{signal,onProgress=()=>{}}={})=>{
  const height=parsePrecomputedHeight(input);
  const aborted=()=>{if(signal?.aborted)throw new DOMException('表示を中止しました','AbortError');};
  aborted();onProgress({message:'計算済みの結果を読み込んでいます'});
  const bundle=cached||validatePrecomputedBundle(await loadBundle({signal}));
  aborted();cached=bundle;return mapPrecomputedResult(bundle,height);
 };
}
export const computePrecomputedHeight=createPrecomputedComputer({loadBundle:async({signal})=>{
 const response=await fetch(new URL('./tsunami-precomputed-atami.json',import.meta.url),{signal});
 check(response.ok,'事前計算データを取得できません。通信できる状態で開き直してください');
 return response.json();
}});
