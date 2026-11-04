// Compare new pending domains without turning exploratory results into publication approval.
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {compareBoundarySensitivity} from '../../lab/web/tsunami-compute.mjs';
const [pairPath,largePath,smallPath,largeResultPath,smallResultPath,heightText,outputPath]=process.argv.slice(2);
if(!outputPath)throw Error('Usage: review_diagnostic_pair.mjs PAIR LARGE SMALL LARGE_RESULT SMALL_RESULT HEIGHT NEW_OUTPUT');
const height=Number(heightText),hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const read=async path=>{const bytes=await readFile(path);return {path,sha256:hash(bytes),data:JSON.parse(bytes)};};
const [pair,...inputs]=await Promise.all([pairPath,largePath,smallPath,largeResultPath,smallResultPath].map(read));
const [large,small,a,b]=inputs;
if(pair.data.review.status!=='pending')throw Error('This diagnostic entry point requires a pending pair');
if(pair.data.primary_domain_id!==large.data.id||pair.data.comparison.domain_id!==small.data.id)throw Error('Wrong domains');
for(const [pack,result] of [[large,a],[small,b]]){
  if(result.data.domain_id!==pack.data.id||result.data.height!==height||result.data.source.domain_sha256!==pack.sha256||result.data.source.solver_sha256!==pack.data.solver.implementation_sha256)throw Error('Result identity/hash mismatch');
}
const test=structuredClone(pair.data);test.review.status='experimental-checked';
let comparison;
try {comparison={passed:true,metrics:compareBoundarySensitivity(test,a.data,b.data,height)};}
catch(error){comparison={passed:false,reason:error.message};}
const config=pair.data.config,window=pair.data.reporting_window,mapping=pair.data.comparison.primary_to_secondary;
let wetUnion=0,intersection=0,landCount=0,sumDifference=0,maximumDifference=0;
for(let y=window[1];y<window[3];y++)for(let x=window[0];x<window[2];x++){
  const i=y*config.nx+x,j=mapping[i];
  if(j<0)throw Error('Reporting cell missing from secondary domain');
  if(config.bed[i]<config.baseline_m)continue;
  landCount++;
  const da=a.data.result.maxDepth[i],db=b.data.result.maxDepth[j],difference=Math.abs(da-db);
  if(!Number.isFinite(difference))throw Error('Non-finite depth difference');
  maximumDifference=Math.max(maximumDifference,difference);
  if(da>=.1||db>=.1){wetUnion++;sumDifference+=difference;}
  if(da>=.1&&db>=.1)intersection++;
}
const directMetrics={land_cells:landCount,wet_union_cells:wetUnion,
  mean_wet_depth_difference_m:wetUnion?sumDifference/wetUnion:0,
  maximum_land_depth_difference_m:maximumDifference,land_footprint_iou:wetUnion?intersection/wetUnion:1};
const report={schema:'pland-diagnostic-pair-review-1',height_m:height,pair_id:pair.data.id,
  saved_pair_review_status:'pending',publication_approved:false,comparison,direct_metrics:directMetrics,
  source_hashes:Object.fromEntries([pair,...inputs].map(r=>[r.path,r.sha256])),
  cases:[a,b].map(r=>({domain_id:r.data.domain_id,status:r.data.status,reason:r.data.reason,
    achieved_height_m:r.data.result.actualGaugeHeight,wall_s:r.data.wall_s,cpu_s:r.data.cpu_s,
    calibration:r.data.calibration,baseline:r.data.baseline,diagnostics:r.data.result.diagnostics})),
  note:'The runtime comparison is exercised on an in-memory review clone. Saved domains stay pending. No all-city or actual-tsunami validation is implied.'};
await writeFile(outputPath,JSON.stringify(report,null,2),{flag:'wx'});
console.log(JSON.stringify({height_m:height,comparison,direct_metrics:directMetrics,cases:report.cases.map(({diagnostics,calibration,baseline,...r})=>r)}));
if(!comparison.passed)process.exitCode=2;
