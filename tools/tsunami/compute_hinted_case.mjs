import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import {resolve,dirname,join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
import {calibrateHeightHinted,selectHistoricalGuess,HINTED_CONTROLLER_VERSION} from './hinted-calibration.mjs';
import {validateSourcePack,validateSourceRecord} from './build_precomputed.mjs';
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');

export async function loadHistoricalHint(paths,pack,rawSha,pair,side,target){
  validateSourcePack(pair,pack,rawSha,side);
  const points=[],records=[];
  for(const path of [...paths].sort()){
    const bytes=await readFile(path),record=JSON.parse(bytes);
    if(record.height===target)continue; // Pilot must not borrow its own known answer.
    validateSourceRecord(pair,record,record.height,side);
    const proof={path:resolve(path),sha256:hash(bytes),record_target_height_m:record.height,domain_id:record.domain_id,
      source:record.source};
    if(!Array.isArray(record.calibration.runs))throw Error('Historical record lacks trial summaries');
    record.calibration.runs.forEach((run,index)=>{
      if(!Number.isFinite(run.forcing_amplitude_m)||!Number.isFinite(run.achieved_height_m)||!Number.isFinite(run.steps)||run.steps<1)throw Error('Invalid historical trial summary');
      points.push({forcing_amplitude_m:run.forcing_amplitude_m,achieved_height_m:run.achieved_height_m,
        proof:{...proof,run_index:index,evidence_kind:index===record.calibration.runs.length-1?'validated-final-result':'historical-trial-summary'}});
    });
    records.push(proof);
  }
  return {hint:selectHistoricalGuess(points,target,pack.config.calibration.maximum_forcing_m,pack.config.calibration.tolerance_m),records};
}

async function main(){
  const [pairPath,packPath,side,hintDirectory,heightText,outputPath]=process.argv.slice(2),height=Number(heightText);
  if(!outputPath||!['primary','secondary'].includes(side)||!Number.isInteger(height)||height<0||height>35)throw Error('Usage: compute_hinted_case.mjs reviewed-pair.json domain.json primary|secondary HINT_DIRECTORY HEIGHT NEW-output.json');
  try{await readFile(outputPath);throw Error('Output already exists; refusing overwrite');}catch(error){if(error.code!=='ENOENT')throw error;}
  const pair=JSON.parse(await readFile(pairPath)),bytes=await readFile(packPath),pack=JSON.parse(bytes),rawSha=hash(bytes);
  const solverHash=hash(await readFile(new URL('../../lab/web/tsunami-solver.mjs',import.meta.url)));
  if(solverHash!==pack.solver.implementation_sha256)throw Error('Actual solver does not match source pack');
  validateSourcePack(pair,pack,rawSha,side);
  const controllerHash=hash(await readFile(new URL('./hinted-calibration.mjs',import.meta.url))),runnerHash=hash(await readFile(new URL('./compute_hinted_case.mjs',import.meta.url)));
  const source={domain_sha256:rawSha,solver_sha256:solverHash,controller_sha256:controllerHash,runner_sha256:runnerHash};
  const paths=(await readdir(hintDirectory)).filter(name=>/^height-\d+\.json$/.test(name)).map(name=>join(hintDirectory,name));
  let hint,records=[],hintLoadError;
  try{({hint,records}=await loadHistoricalHint(paths,pack,rawSha,pair,side,height));}catch(error){hintLoadError=String(error);}
  console.log(JSON.stringify({height,domain:pack.id,phase:'starting',controller:HINTED_CONTROLLER_VERSION,hint,hintLoadError}));
  const started=Date.now(),cpuStart=process.cpuUsage();
  const output=calibrateHeightHinted(pack.config,height,{hint,onProgress:p=>{if(p.run_complete)console.log(JSON.stringify({height,domain:pack.id,...p}));}});
  const cpu=process.cpuUsage(cpuStart),record={domain_id:pack.id,height,source,...output,wall_s:(Date.now()-started)/1000,cpu_s:(cpu.user+cpu.system)/1e6};
  record.controller={...record.controller,implementation_sha256:controllerHash,runner_sha256:runnerHash,historical_records:records,hint_load_error:hintLoadError??null};
  record.result={...output.result,depth:Array.from(output.result.depth),maxDepth:Array.from(output.result.maxDepth),xMomentum:undefined,yMomentum:undefined};
  if(record.status==='accepted')validateSourceRecord(pair,record,height,side);
  // Protect provenance from edits made while a long pilot was running.
  if(hash(await readFile(packPath))!==rawSha||hash(await readFile(new URL('../../lab/web/tsunami-solver.mjs',import.meta.url)))!==solverHash||hash(await readFile(new URL('./hinted-calibration.mjs',import.meta.url)))!==controllerHash||hash(await readFile(new URL('./compute_hinted_case.mjs',import.meta.url)))!==runnerHash)throw Error('Implementation or input changed during calculation');
  await mkdir(dirname(outputPath),{recursive:true});await writeFile(outputPath,JSON.stringify(record),{flag:'wx'});
  console.log(JSON.stringify({height,domain:pack.id,status:record.status,reason:record.reason,achieved:record.result.actualGaugeHeight,wall_s:record.wall_s,cpu_s:record.cpu_s,fresh_simulations:record.controller.total_fresh_simulations}));
  if(record.status!=='accepted')process.exitCode=2;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)await main();
