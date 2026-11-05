/** Repackage existing full-domain calculations after an exhaustive, fixed-limit screen. */
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {validateTerrainPack,compareBoundarySensitivity} from '../../lab/web/tsunami-compute.mjs';
import {validatePrecomputedBundle,mapPrecomputedResult} from '../../lab/web/tsunami-precomputed.mjs';
import {buildPrecomputedBundle,validateSourcePack,validateSourceRecord} from './build_precomputed.mjs';
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');

export function findReportingWindow(pack,pairs,{bufferCells=1}={}){
 if(!Number.isSafeInteger(bufferCells)||bufferCells<1)throw Error('Boundary buffer must be a positive integer');
 const {nx,ny,bed,baseline_m}=pack.config,mapping=pack.comparison.primary_to_secondary,old=pack.reporting_window,n=nx*ny;
 let west=nx,south=ny,east=0,north=0;
 for(let i=0;i<n;i++)if(mapping[i]>=0){const x=i%nx,y=Math.floor(i/nx);west=Math.min(west,x);east=Math.max(east,x+1);south=Math.min(south,y);north=Math.max(north,y+1);}
 // Any holes in the common domain are vetoed as well as excessive pointwise errors.
 const bad=Float64Array.from(mapping,j=>j<0?1:0),planes=[];
 for(const {primary,secondary} of pairs){
  const mean=new Float64Array(n),iou=new Float64Array(n),a=primary.result.maxDepth,b=secondary.result.maxDepth;
  for(let i=0;i<n;i++){
   if(mapping[i]<0||bed[i]<baseline_m)continue;
   const da=a[i],db=b[mapping[i]],difference=Math.abs(da-db),union=da>=.1||db>=.1,intersection=da>=.1&&db>=.1;
   if(difference>.5)bad[i]=1;
   // Neither dry land nor marine cells enter these denominators.
   mean[i]=union?difference-.1:0;iou[i]=.9*Number(union)-Number(intersection);
  }
  planes.push(mean,iou);
 }
 planes.unshift(bad);
 const stride=nx+1,tables=planes.map(plane=>{
  const table=new Float64Array((nx+1)*(ny+1));
  for(let y=0;y<ny;y++){let row=0;for(let x=0;x<nx;x++){row+=plane[y*nx+x];table[(y+1)*stride+x+1]=table[y*stride+x+1]+row;}}
  return table;
 });
 let best=null,candidates=0,passing=0;
 for(let x0=west+bufferCells;x0<=old[0];x0++)for(let x1=old[2];x1<=east-bufferCells;x1++)
 for(let y0=south+bufferCells;y0<=old[1];y0++)for(let y1=old[3];y1<=north-bufferCells;y1++){
  candidates++;let ok=true;
  for(const table of tables){const sum=table[y1*stride+x1]-table[y0*stride+x1]-table[y1*stride+x0]+table[y0*stride+x0];if(sum>1e-9){ok=false;break;}}
  if(!ok)continue;passing++;
  const cells=(x1-x0)*(y1-y0);if(!best||cells>best.cells)best={cells,window:[x0,y0,x1,y1]};
 }
 if(!best)throw Error('No passing reporting window contains the original coverage');
 return {...best,candidate_rectangles:candidates,passing_rectangles:passing,common_window:[west,south,east,north],minimum_boundary_buffer_cells:bufferCells,
  selection:'Maximum area among grid-aligned rectangles containing the entire original window, inside the common domain with the stated buffer. Fixed criteria across every supplied height; post-hoc coverage selection, not independent forecast validation.'};
}

async function main(){
 const [pairPath,primaryPath,secondaryPath,manifestPath,oldBundlePath,outPath]=process.argv.slice(2);
 if(!outPath)throw Error('Usage: expand_reporting_window.mjs reviewed-pair.json primary.json secondary.json source-manifest.json original-bundle.json NEW_OUTPUT_DIRECTORY');
 const read=async path=>{const bytes=await readFile(path);return {bytes,value:JSON.parse(bytes),sha256:sha(bytes)};};
 const original=await read(pairPath),oldFile=await read(oldBundlePath),manifestFile=await read(manifestPath);
 const pack=validateTerrainPack(original.value),oldBundle=validatePrecomputedBundle(oldFile.value),pairs=[];
 assert.equal(oldBundle.domain_id,pack.id);assert.equal(manifestFile.value.length,36);
 assert.equal(sha(await readFile(new URL('../../lab/web/tsunami-solver.mjs',import.meta.url))),pack.solver.implementation_sha256);
 for(const [side,path] of [['primary',primaryPath],['secondary',secondaryPath]]){
  const source=await read(path);validateSourcePack(pack,source.value,source.sha256,side);
 }
 const sourceRecords=[];
 for(let height=0;height<36;height++){
  const row=manifestFile.value[height];assert.equal(row.height,height);const pair={sourceHashes:{}};
  for(const side of ['primary','secondary']){
   const source=await read(row[side]);
   assert.equal(source.sha256,oldBundle.cases[height].source_hashes[side],`Previously distributed raw result hash: ${height} ${side}`);
   validateSourceRecord(pack,source.value,height,side);pair[side]=source.value;pair.sourceHashes[side]=source.sha256;
   sourceRecords.push({height,side,path:row[side],sha256:source.sha256});
  }
  compareBoundarySensitivity(pack,pair.primary,pair.secondary,height);pairs.push(pair);
 }
 const search=findReportingWindow(pack,pairs),expanded=structuredClone(pack),oldWindow=pack.reporting_window;
 expanded.reporting_window=search.window;
 const bounds=w=>[pack.conversion.origin_m[0]+w[0]*pack.config.dx,pack.conversion.origin_m[1]+w[1]*pack.config.dy,pack.conversion.origin_m[0]+w[2]*pack.config.dx,pack.conversion.origin_m[1]+w[3]*pack.config.dy];
 const newBounds=bounds(search.window),oldBounds=bounds(oldWindow);
 expanded.study.reporting_bounds_m=newBounds;
 expanded.id='atami-pair-'+sha(JSON.stringify({source_pair_sha256:original.sha256,reporting_window:search.window,selection:search.selection})).slice(0,24);
 const comparisons=pairs.map((pair,h)=>compareBoundarySensitivity(expanded,pair.primary,pair.secondary,h));
 expanded.review={...expanded.review,checked_on:new Date().toISOString().slice(0,10),
  domain_sensitivity_cases:comparisons.map((metrics,height)=>({height_m:height,reporting_bounds_m:newBounds,...metrics})),
  reporting_expansion:{original_pair_id:pack.id,original_bounds_m:oldBounds,reporting_bounds_m:newBounds,search,raw_results_unchanged:true},
  method:'Reverified all 72 original full-domain result hashes against the previously distributed bundle, both measured source domains and solver hash, calibration, baseline, mass balance, boundary audit and unchanged paired-domain screen for all 36 heights. Exhaustive reporting-window search only; no new solve, interpolation, terrain or threshold changes.',
  limitations:pack.review.limitations.map(text=>text.includes('780x580m')?`Only selected ${newBounds[2]-newBounds[0]}x${newBounds[3]-newBounds[1]}m reporting area; inundation may continue outside`:text)};
 const bundle=buildPrecomputedBundle(expanded,pairs),bundleBytes=JSON.stringify(bundle),expandedBytes=JSON.stringify(expanded);
 const cases=bundle.cases.map((c,h)=>{
  const result=mapPrecomputedResult(bundle,h),previous=mapPrecomputedResult(oldBundle,h),coords=result.geojson.features.flatMap(f=>f.geometry.coordinates[0]);
  return {height_m:h,achieved_height_m:c.achieved_height_m,boundary_comparison:c.boundary_comparison,
   inundated_area_m2:result.metadata.inundatedAreaM2,previous_inundated_area_m2:previous.metadata.inundatedAreaM2,maximum_depth_m:result.metadata.maximumDepthM,
   inundation_bounds_lonlat:coords.length?[[Math.min(...coords.map(p=>p[0])),Math.min(...coords.map(p=>p[1]))],[Math.max(...coords.map(p=>p[0])),Math.max(...coords.map(p=>p[1]))]]:null};
 });
 const evidence={schema:'pland-tsunami-reporting-expansion-1',created_on:expanded.review.checked_on,search,
  original:{id:oldBundle.id,sha256:oldFile.sha256,reporting_window:oldWindow,bounds_epsg6676:oldBounds,width_m:oldBounds[2]-oldBounds[0],height_m:oldBounds[3]-oldBounds[1],area_m2:(oldBounds[2]-oldBounds[0])*(oldBounds[3]-oldBounds[1])},
  expanded:{id:bundle.id,sha256:sha(bundleBytes),reviewed_pair_sha256:sha(expandedBytes),reporting_window:search.window,bounds_epsg6676:newBounds,bounds_lonlat:mapPrecomputedResult(bundle,0).bounds,width_m:newBounds[2]-newBounds[0],height_m:newBounds[3]-newBounds[1],area_m2:search.cells*pack.config.dx*pack.config.dy,bytes:Buffer.byteLength(bundleBytes)},
  source_pair_sha256:original.sha256,source_manifest_sha256:manifestFile.sha256,source_domains:pack.source_domains,solver:pack.solver,
  all_72_raw_hashes_match_previously_distributed_bundle:true,all_36_original_and_expanded_screens_pass:true,
  mean_definition:'Sum of absolute maximum-depth differences over initially dry land cells wet >=0.1m in either domain, divided only by their union count. Marine and subthreshold dry cells excluded. Maximum screen covers ALL land cells.',
  scope:'Conditional reporting coverage selected after examining saved results; not full inundation extent, observed-tsunami validation or improved numerical precision. One-cell buffer is a search constraint, not proof of boundary independence.',
  cases,source_records:sourceRecords};
 await mkdir(outPath,{recursive:false});
 for(const [name,bytes] of [['reviewed-pair.json',expandedBytes],['tsunami-precomputed-atami.json',bundleBytes],['bundle-evidence.json',JSON.stringify(evidence,null,2)+'\n']])await writeFile(join(outPath,name),bytes,{flag:'wx'});
 console.log(JSON.stringify({search,original:evidence.original,expanded:evidence.expanded,worst_mean_m:Math.max(...comparisons.map(c=>c.meanWetDepthDifferenceM)),worst_maximum_m:Math.max(...comparisons.map(c=>c.maximumDepthDifferenceM)),minimum_iou:Math.min(...comparisons.map(c=>c.footprintIoU)),selected_cases:cases.filter(c=>[0,3,10,20,35].includes(c.height_m))},null,2));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)await main();
