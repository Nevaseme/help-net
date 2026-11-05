import test from 'node:test';
import assert from 'node:assert/strict';
import {selectHistoricalGuess,calibrateHeightHinted} from '../../tools/tsunami/hinted-calibration.mjs';
import {simulate,calibrateHeight} from '../web/tsunami-solver.mjs';
import {SOLVER_VERSION} from '../web/tsunami-solver.mjs';
import {loadHistoricalHint} from '../../tools/tsunami/compute_hinted_case.mjs';
import {mkdtemp,writeFile,unlink,rmdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {loadSameDomainHint} from '../../tools/tsunami/hinted-records.mjs';
function config(){return {nx:20,ny:1,dx:50,dy:1000,bed:-10,baseline_m:0,duration_s:160,manning_n:0,reference_index:10,
  boundaries:{left:{type:'flather_stage'},right:{type:'open_still_water'},top:{type:'open_still_water'},bottom:{type:'open_still_water'}},
  calibration:{boundary:'left',maximum_forcing_m:4,tolerance_m:.002,max_iterations:14,pulse_duration_s:80,pulse_delay_s:0}};}
function atForcing(c,amplitude){return simulate({...c,boundaries:{...c.boundaries,left:{...c.boundaries.left,amplitude_m:amplitude,period_s:80,delay_s:0}}});}
test('historical points interpolate a forcing proposal, not a returned field or bracket',()=>{
 const points=[{forcing_amplitude_m:1,achieved_height_m:.8,proof:{id:'a'}},{forcing_amplitude_m:3,achieved_height_m:2.8,proof:{id:'b'}}];
 const hint=selectHistoricalGuess(points,1.8,4,.002);
 assert.equal(hint.forcing_amplitude_m,2);assert.match(hint.use,/NOT|neither/);
 assert.equal(selectHistoricalGuess(points,3,4,.002),null);
 assert.throws(()=>selectHistoricalGuess([{forcing_amplitude_m:1,achieved_height_m:2},{forcing_amplitude_m:2,achieved_height_m:1}],1.5,4,.002),/Nonmonotone/);
 assert.throws(()=>selectHistoricalGuess([{forcing_amplitude_m:1,achieved_height_m:1},{forcing_amplitude_m:1,achieved_height_m:1.01}],1,4,.002),/Conflicting/);
});
test('hinted calibration returns a fresh full field identical to independent simulate at its final forcing',()=>{
 const c=config(),ordinary=calibrateHeight(c,.2);
 const out=calibrateHeightHinted(c,.2,{hint:{forcing_amplitude_m:ordinary.calibration.forcing_amplitude_m*1.1,source_points:[{achieved_height_m:99}]}});
 assert.equal(out.status,'accepted',out.reason);assert.equal(out.controller.strategy,'historical-initial-guess-fresh-bracket');
 assert.ok(Math.abs(out.result.actualGaugeHeight-.2)<=.002);
 assert.equal(out.calibration.runs[0].forcing_amplitude_m,0);
 assert.equal(out.controller.total_fresh_simulations,out.calibration.runs.length);
 assert.equal(out.result.diagnostics.simulated_s,160);
 const direct=atForcing(c,out.calibration.forcing_amplitude_m);
 assert.deepEqual(out.result.maxDepth,direct.maxDepth);assert.deepEqual(out.result.depth,direct.depth);
 assert.deepEqual(out.result.xMomentum,direct.xMomentum);
 assert.deepEqual(out.result.boundaryAudit,direct.boundaryAudit);
});
test('missing or invalid hints and zero target use original calibration with fresh result',()=>{
 for(const [target,hint]of[[.2,undefined],[.2,{forcing_amplitude_m:NaN}],[0,{forcing_amplitude_m:1}]]){
  const c=config(),out=calibrateHeightHinted(c,target,{hint}),ordinary=calibrateHeight(c,target);
  assert.equal(out.controller.strategy,'ordinary-calibrator-fallback');assert.equal(out.status,ordinary.status);
  assert.deepEqual(out.result.maxDepth,ordinary.result.maxDepth);
 }
});
test('a failed hinted iteration falls back without loosening the original iteration/target limit',()=>{
 const c=config();c.calibration.max_iterations=1;
 const out=calibrateHeightHinted(c,.2,{hint:{forcing_amplitude_m:.001}}),ordinary=calibrateHeight(c,.2);
 assert.equal(out.controller.strategy,'ordinary-calibrator-fallback');assert.equal(out.controller.fallback_reason,'calibration_iteration_limit');
 assert.equal(out.status,ordinary.status);assert.equal(out.reason,ordinary.reason);
 assert.deepEqual(out.result.maxDepth,ordinary.result.maxDepth);
 assert.equal(out.controller.total_fresh_simulations,2+ordinary.calibration.runs.length);
});
test('a matched gauge still cannot bypass a closed computational cut',()=>{
 const c=config();c.boundaries.top={type:'reflective'};
 const out=calibrateHeightHinted(c,.2,{hint:{forcing_amplitude_m:.4}});
 assert.equal(out.status,'rejected');assert.equal(out.reason,'inundation_reaches_closed_cut_boundary');
 assert.equal(out.controller.strategy,'ordinary-calibrator-fallback');
});
test('cancellation propagates instead of starting fallback work',()=>{
 assert.throws(()=>calibrateHeightHinted(config(),.2,{hint:{forcing_amplitude_m:.4},isCancelled:()=>true}),/Cancelled/);
 const c=config();c.datum_verified=false;
 assert.throws(()=>calibrateHeightHinted(c,.2,{hint:{forcing_amplitude_m:.4}}),/datum/);
});
test('historical loading binds exact file bytes and rejects wrong-domain provenance before choosing a hint',async()=>{
 const c=config();c.bed=Array(20).fill(-10);
 const solver={version:SOLVER_VERSION,implementation_sha256:'a'.repeat(64)},rawSha='b'.repeat(64),source={id:'primary',config:c,solver,provenance:{}};
 const pair={primary_domain_id:'primary',config:c,solver,provenance:{},comparison:{domain_id:'secondary',config:c,provenance:{}},
  source_domains:{primary:{id:'primary',sha256:rawSha},secondary:{id:'secondary',sha256:'c'.repeat(64)}}};
 const output=calibrateHeight(c,1),record={domain_id:'primary',height:1,source:{domain_sha256:rawSha,solver_sha256:solver.implementation_sha256},...output};
 record.result={...output.result,maxDepth:Array.from(output.result.maxDepth)};
 const dir=await mkdtemp(join(tmpdir(),'pland-hint-proof-')),path=join(dir,'height-1.json');
 try{
  const bytes=JSON.stringify(record);await writeFile(path,bytes);
  const loaded=await loadHistoricalHint([path],source,rawSha,pair,'primary',.5);
  assert.ok(loaded.hint);assert.equal(loaded.records[0].sha256,createHash('sha256').update(bytes).digest('hex'));
  assert.ok(loaded.hint.source_points.every(p=>p.proof.sha256===loaded.records[0].sha256));
  const same=await loadSameDomainHint(dir,source,rawSha,.5);assert.equal(same.hint.forcing_amplitude_m,loaded.hint.forcing_amplitude_m);
  record.source.domain_sha256='d'.repeat(64);await writeFile(path,JSON.stringify(record));
  await assert.rejects(()=>loadHistoricalHint([path],source,rawSha,pair,'primary',.5));
  await assert.rejects(()=>loadSameDomainHint(dir,source,rawSha,.5));
 }finally{await unlink(path);await rmdir(dir);}
});
test('future-child helper uses source-bound hints, saves controller proof and reuses prior output unchanged',async()=>{
 const c=config();c.bed=Array(20).fill(-10);c.calibration.maximum_forcing_m=8;
 const solverHash=createHash('sha256').update(await readFile(new URL('../web/tsunami-solver.mjs',import.meta.url))).digest('hex');
 const pack={id:'integration-domain',config:c,solver:{version:SOLVER_VERSION,implementation_sha256:solverHash}},bytes=JSON.stringify(pack);
 const rawSha=createHash('sha256').update(bytes).digest('hex'),old=calibrateHeight(c,2);
 assert.equal(old.status,'accepted',old.reason);
 const record={domain_id:pack.id,height:2,source:{domain_sha256:rawSha,solver_sha256:solverHash},...old};record.result={...old.result,maxDepth:Array.from(old.result.maxDepth)};
 const dir=await mkdtemp(join(tmpdir(),'pland-hint-integration-')),packPath=join(dir,'pack.json'),oldPath=join(dir,'height-2.json'),outputPath=join(dir,'height-1.json');
 try{
  await writeFile(packPath,bytes);await writeFile(oldPath,JSON.stringify(record));
  const {fileURLToPath}=await import('node:url');
  const args=[fileURLToPath(new URL('../../tools/tsunami/compute_precomputed_case.mjs',import.meta.url)),packPath,'1',outputPath];
  const first=spawnSync(process.execPath,args,{encoding:'utf8',windowsHide:true});assert.equal(first.status,0,first.stderr);
  const output=JSON.parse(await readFile(outputPath,'utf8'));assert.equal(output.status,'accepted');
  assert.equal(output.controller.strategy,'historical-initial-guess-fresh-bracket');assert.match(output.source.hint_loader_sha256,/^[0-9a-f]{64}$/);
  assert.equal(output.controller.historical_records[0].source.domain_sha256,rawSha);
  const original=await readFile(outputPath,'utf8'),second=spawnSync(process.execPath,args,{encoding:'utf8',windowsHide:true});
  assert.equal(second.status,0,second.stderr);assert.match(second.stdout,/"reused":true/);assert.equal(await readFile(outputPath,'utf8'),original);
 }finally{for(const path of[packPath,oldPath,outputPath])await unlink(path).catch(e=>{if(e.code!=='ENOENT')throw e;});await rmdir(dir);}
});
