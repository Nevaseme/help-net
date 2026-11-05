import test from 'node:test';
import assert from 'node:assert/strict';
import {parsePrecomputedHeight,validatePrecomputedBundle,mapPrecomputedResult,createPrecomputedComputer} from '../web/tsunami-precomputed.mjs';
import {buildPrecomputedBundle,validateSourcePack} from '../../tools/tsunami/build_precomputed.mjs';
function fixture(){
 const config={nx:2,ny:1,dx:1,dy:1,bed:[-1,1],baseline_m:0,reference_index:0,duration_s:900,manning_n:.03,boundaries:{top:{type:'transmissive'}},calibration:{tolerance_m:.05,maximum_forcing_m:60,boundary:'right',pulse_duration_s:600,pulse_delay_s:0}};
 const pack={schema:'pland-tsunami-browser-domain-1',id:'paired-test',primary_domain_id:'primary-test',config,reporting_window:[0,0,2,1],comparison:{domain_id:'secondary-test',config:{...structuredClone(config),ny:2,bed:[-1,1,-1,1]},primary_to_secondary:[0,1]},solver:{version:'synthetic-test',implementation_sha256:'b'.repeat(64)},nodes_lonlat:[[139,35],[139.001,35],[139.002,35],[139,35.001],[139.001,35.001],[139.002,35.001]],reference_point:[139.0005,35.0005],provenance:{vertical_datum_status:'verified',source_sha256:'a'.repeat(64),sources:[{url:'https://example.test/synthetic-fixture'}]},review:{status:'experimental-checked',real_world_validated:false}};
 pack.source_domains={primary:{id:'primary-test',sha256:'1'.repeat(64)},secondary:{id:'secondary-test',sha256:'2'.repeat(64)}};
 pack.comparison.provenance=structuredClone(pack.provenance);
 const result=(height,secondary)=>({domain_id:secondary?'secondary-test':'primary-test',height,status:'accepted',source:{domain_sha256:(secondary?'2':'1').repeat(64),solver_sha256:pack.solver.implementation_sha256},result:{actualGaugeHeight:height,maxDepth:secondary?[1+height,height/10,1,0]:[1+height,height/10],diagnostics:{completed:true,solver:'synthetic-test',simulated_s:900,initial_volume_m3:secondary?2:1,final_volume_m3:(secondary?2:1)+height,net_boundary_volume_m3:height,roundoff_volume_m3:0,mass_residual_m3:0,relative_mass_residual:0},boundaryAudit:{inundation_reaches_closed_cut_boundary:false,closed_cut_inundated_length_m:0,supercritical_open_boundary:false}},calibration:{target_height_m:height,achieved_height_m:height,residual_m:0,tolerance_m:height===0?.001:.05,forcing_amplitude_m:height,reference_index:0,baseline_maximum_depth_drift_m:0}});
 const pairs=Array.from({length:36},(_,height)=>({primary:result(height,false),secondary:result(height,true),sourceHashes:{primary:'c'.repeat(64),secondary:'d'.repeat(64)}}));
 return {pack,pairs};
}
test('only explicitly selected 0 to 35 integer heights are accepted, never rounded',()=>{
 for(let h=0;h<=35;h++)assert.equal(parsePrecomputedHeight(String(h)),h);
 for(const h of [-1,36,.5,12.5,NaN,Infinity,'',null,true])assert.throws(()=>parsePrecomputedHeight(h));
});
test('builder requires all 36 reviewed pairs and rejects wrong-source or failed comparison',()=>{
 const {pack,pairs}=fixture();assert.equal(buildPrecomputedBundle(pack,pairs).cases.length,36);
 assert.throws(()=>buildPrecomputedBundle(pack,pairs.slice(0,35)),/36/);
 const wrong=structuredClone(pairs);wrong[12].primary.domain_id='old-domain';assert.throws(()=>buildPrecomputedBundle(pack,wrong),/source/);
 const mismatch=structuredClone(pairs);mismatch[12].secondary.result.maxDepth[1]+=1;assert.throws(()=>buildPrecomputedBundle(pack,mismatch),/大きく変わる/);
});

test('source records must match reviewed raw-domain and actual solver hashes',()=>{
 const {pack,pairs}=fixture();
 for(const side of ['primary','secondary'])for(const key of ['domain_sha256','solver_sha256']){
  const wrong=structuredClone(pairs);wrong[12][side].source[key]='0'.repeat(64);
  assert.throws(()=>buildPrecomputedBundle(pack,wrong),/出典/);
 }
 const missing=structuredClone(pack);delete missing.source_domains;assert.throws(()=>buildPrecomputedBundle(missing,pairs),/source_domains/);
});

test('batch inputs bind raw hashes and embedded config, solver and provenance',()=>{
 const {pack}=fixture();
 for(const side of ['primary','secondary']){
  const source={id:pack.source_domains[side].id,config:structuredClone(side==='primary'?pack.config:pack.comparison.config),solver:structuredClone(pack.solver),provenance:structuredClone(pack.provenance)};
  assert.equal(validateSourcePack(pack,source,pack.source_domains[side].sha256,side),source);
  assert.throws(()=>validateSourcePack(pack,source,'0'.repeat(64),side),/raw hash/);
  for(const mutate of [s=>s.config.manning_n=.04,s=>s.config.bed[1]=2,s=>s.config.calibration.tolerance_m=.5,s=>s.solver.implementation_sha256='0'.repeat(64),s=>s.provenance.source_sha256='0'.repeat(64)]){
   const wrong=structuredClone(source);mutate(wrong);assert.throws(()=>validateSourcePack(pack,wrong,pack.source_domains[side].sha256,side),/contents/);
  }
 }
});

test('accepted labels cannot hide failed or contradictory numerical records',()=>{
 const {pack,pairs}=fixture();
 const changes=[r=>r.result.diagnostics.relative_mass_residual=.5,r=>r.result.diagnostics.mass_residual_m3=1,r=>r.result.diagnostics.final_volume_m3+=1,r=>r.calibration.baseline_maximum_depth_drift_m=10,r=>r.calibration.achieved_height_m=999,r=>r.calibration.residual_m=1,r=>r.calibration.tolerance_m=100,r=>r.result.maxDepth[0]=1,r=>r.result.diagnostics.simulated_s=10,r=>r.result.boundaryAudit.closed_cut_inundated_length_m=20];
 for(const side of ['primary','secondary'])for(const change of changes){const wrong=structuredClone(pairs);change(wrong[12][side]);assert.throws(()=>buildPrecomputedBundle(pack,wrong));}
});

test('distilled proofs remain consistent with displayed data and immutable input contracts',()=>{
 const {pack,pairs}=fixture(),bundle=buildPrecomputedBundle(pack,pairs);
 for(const change of [b=>b.cases[12].max_depth_m[0]=1,b=>b.cases[12].calibration.achieved_height_m=999,b=>b.cases[12].calibration.tolerance_m=100,b=>b.cases[12].source_proofs.secondary.diagnostics.relative_mass_residual=.5,b=>b.cases[12].source_proofs.primary.source.domain_sha256='0'.repeat(64),b=>b.validation_contracts.primary.duration_s=10,b=>b.grid.baseline_m=1]){
  const wrong=structuredClone(bundle);change(wrong);assert.throws(()=>validatePrecomputedBundle(wrong));
 }
});

test('excluded display cells remain excluded after compaction',()=>{
 const {pack,pairs}=fixture();pack.display_cells=[true,false];
 const bundle=buildPrecomputedBundle(pack,pairs);assert.deepEqual(bundle.grid.display_cells,[true,false]);
 assert.equal(mapPrecomputedResult(bundle,12).geojson.features.length,0);
 const wrong=structuredClone(bundle);wrong.grid.display_cells=[true];assert.throws(()=>validatePrecomputedBundle(wrong));
});
test('saved map selects exact computed depths and excludes sea and subthreshold land',()=>{
 const {pack,pairs}=fixture(),bundle=buildPrecomputedBundle(pack,pairs);
 const zero=mapPrecomputedResult(bundle,0);assert.equal(zero.geojson.features.length,0);assert.equal(zero.metadata.inundatedAreaM2,0);
 const twelve=mapPrecomputedResult(bundle,12);assert.equal(twelve.geojson.features.length,1);assert.equal(twelve.geojson.features[0].properties.max_depth_m,1.2);assert.equal(twelve.metadata.precomputed,true);
 const thirtyFive=mapPrecomputedResult(bundle,35);assert.equal(thirtyFive.geojson.features[0].properties.max_depth_m,3.5);assert.equal(thirtyFive.metadata.realWorldValidated,false);
 assert.throws(()=>mapPrecomputedResult(bundle,12.5),/1m刻み/);
});
test('incomplete or numerically invalid saved data cannot be rendered',()=>{
 const {pack,pairs}=fixture(),bundle=buildPrecomputedBundle(pack,pairs);
 for(const change of [b=>b.cases.pop(),b=>b.cases[20].height_m=19,b=>b.cases[20].max_depth_m[1]=null,b=>b.cases[20].boundary_comparison.maximumDepthDifferenceM=.6,b=>b.cases[20].boundary_audit.inundation_reaches_closed_cut_boundary=true,b=>b.cases[20].diagnostics.completed=false,b=>b.grid.bed[1]=NaN]){
  const copy=structuredClone(bundle);change(copy);assert.throws(()=>validatePrecomputedBundle(copy));
 }
});
test('height switches reuse loaded data without workers or repeated downloads',async()=>{
 const {pack,pairs}=fixture(),bundle=buildPrecomputedBundle(pack,pairs);let loads=0;
 const compute=createPrecomputedComputer({loadBundle:async()=>{loads++;return bundle;}});
 assert.equal((await compute(10)).heightM,10);assert.equal((await compute(20)).heightM,20);assert.equal((await compute(35)).heightM,35);assert.equal(loads,1);
});
test('cancelled load never returns a result and invalid decimals do not fetch data',async()=>{
 const {pack,pairs}=fixture(),bundle=buildPrecomputedBundle(pack,pairs),control=new AbortController();let loads=0;
 const compute=createPrecomputedComputer({loadBundle:async()=>{loads++;control.abort();return bundle;}});
 await assert.rejects(compute(12.5),/1m刻み/);assert.equal(loads,0);
 await assert.rejects(compute(12,{signal:control.signal}),{name:'AbortError'});
});
