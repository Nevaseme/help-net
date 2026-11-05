import {readFile,readdir} from 'node:fs/promises';
import {join,resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {validateCalculationProof,PROOF_CALIBRATION_FIELDS,PROOF_DIAGNOSTIC_FIELDS,PROOF_BOUNDARY_FIELDS} from '../../lab/web/tsunami-precomputed.mjs';
import {selectHistoricalGuess} from './hinted-calibration.mjs';
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const pick=(value,keys)=>Object.fromEntries(keys.map(k=>[k,value?.[k]]));
/** Current batch already validates its reviewed pair. This standalone loader binds
 * hints to the exact same raw source pack, so existing batch arguments stay intact. */
export async function loadSameDomainHint(directory,pack,rawSha,target){
 const c=pack.config,contract={id:pack.id,sha256:rawSha,duration_s:c.duration_s,tolerance_m:c.calibration.tolerance_m,
  maximum_forcing_m:c.calibration.maximum_forcing_m,reference_index:c.reference_index,reference_bed_m:c.bed[c.reference_index],baseline_m:c.baseline_m};
 let names;try{names=await readdir(directory);}catch(error){if(error.code==='ENOENT')return {hint:null,records:[]};throw error;}
 const points=[],records=[];
 for(const name of names.filter(x=>/^height-\d+\.json$/.test(x)).sort()){
  const path=join(directory,name),bytes=await readFile(path),r=JSON.parse(bytes);
  if(r.height===target)continue;
  if(r.status!=='accepted'||!Number.isInteger(r.height)||r.height<0||r.height>35)throw Error('Invalid historical source record');
  validateCalculationProof({domain_id:r.domain_id,source:r.source,achieved_height_m:r.result?.actualGaugeHeight,
   reference_depth_m:r.result?.maxDepth?.[c.reference_index],calibration:pick(r.calibration,PROOF_CALIBRATION_FIELDS),
   diagnostics:pick(r.result?.diagnostics,PROOF_DIAGNOSTIC_FIELDS),boundary_audit:pick(r.result?.boundaryAudit,PROOF_BOUNDARY_FIELDS)},contract,r.height,pack.solver);
  const proof={path:resolve(path),sha256:hash(bytes),record_target_height_m:r.height,domain_id:r.domain_id,source:r.source};
  if(!Array.isArray(r.calibration.runs))throw Error('Historical trial summaries are missing');
  r.calibration.runs.forEach((run,index)=>{
   if(!Number.isFinite(run.forcing_amplitude_m)||!Number.isFinite(run.achieved_height_m)||!Number.isInteger(run.steps)||run.steps<1)throw Error('Invalid historical trial summary');
   points.push({forcing_amplitude_m:run.forcing_amplitude_m,achieved_height_m:run.achieved_height_m,
    proof:{...proof,run_index:index,evidence_kind:index===r.calibration.runs.length-1?'validated-final-result':'historical-trial-summary'}});
  });records.push(proof);
 }
 return {hint:selectHistoricalGuess(points,target,c.calibration.maximum_forcing_m,c.calibration.tolerance_m),records};
}
