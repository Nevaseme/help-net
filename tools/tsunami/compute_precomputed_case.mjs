import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {dirname} from 'node:path';
import {createHash} from 'node:crypto';
import {calibrateHeightHinted} from './hinted-calibration.mjs';
import {loadSameDomainHint} from './hinted-records.mjs';
const [packPath,heightText,outputPath]=process.argv.slice(2),height=Number(heightText);
if(!outputPath||!Number.isInteger(height)||height<0||height>35)throw Error('Usage: compute_precomputed_case.mjs domain.json HEIGHT_0_TO_35 NEW-output.json');
const hash=b=>createHash('sha256').update(b).digest('hex');
const bytes=await readFile(packPath),pack=JSON.parse(bytes),implementation=await readFile(new URL('../../lab/web/tsunami-solver.mjs',import.meta.url));
if(hash(implementation)!==pack.solver.implementation_sha256)throw Error('Source pack does not match actual solver implementation');
const source={domain_sha256:hash(bytes),solver_sha256:hash(implementation)};
const controlFiles={controller_sha256:new URL('./hinted-calibration.mjs',import.meta.url),hint_loader_sha256:new URL('./hinted-records.mjs',import.meta.url),runner_sha256:new URL('./compute_precomputed_case.mjs',import.meta.url)};
for(const [key,path] of Object.entries(controlFiles))source[key]=hash(await readFile(path));
try{
 const prior=JSON.parse(await readFile(outputPath,'utf8'));
 if(prior.domain_id!==pack.id||prior.height!==height||prior.source?.domain_sha256!==source.domain_sha256||prior.source?.solver_sha256!==source.solver_sha256)throw Error('Existing output belongs to another input; refusing overwrite');
 console.log(JSON.stringify({height,domain:pack.id,reused:true,status:prior.status,reason:prior.reason}));process.exitCode=prior.status==='accepted'?0:2;
}catch(error){
 if(error.code!=='ENOENT')throw error;
 let hint,records=[],hintLoadError;
 try{({hint,records}=await loadSameDomainHint(dirname(outputPath),pack,source.domain_sha256,height));}catch(error){hintLoadError=String(error);}
 const started=Date.now(),cpuStarted=process.cpuUsage();
 const output=calibrateHeightHinted(pack.config,height,{hint}),cpu=process.cpuUsage(cpuStarted);
 const record={domain_id:pack.id,height,source,...output,wall_s:(Date.now()-started)/1000,cpu_s:(cpu.user+cpu.system)/1e6};
 record.controller={...record.controller,implementation_sha256:source.controller_sha256,hint_loader_sha256:source.hint_loader_sha256,runner_sha256:source.runner_sha256,historical_records:records,hint_load_error:hintLoadError??null};
 record.result={...output.result,depth:Array.from(output.result.depth),maxDepth:Array.from(output.result.maxDepth),xMomentum:undefined,yMomentum:undefined};
 if(hash(await readFile(packPath))!==source.domain_sha256||hash(await readFile(new URL('../../lab/web/tsunami-solver.mjs',import.meta.url)))!==source.solver_sha256)throw Error('Source changed during calculation');
 for(const [key,path]of Object.entries(controlFiles))if(hash(await readFile(path))!==source[key])throw Error('Controller changed during calculation');
 await mkdir(dirname(outputPath),{recursive:true});await writeFile(outputPath,JSON.stringify(record),{flag:'wx'});
 console.log(JSON.stringify({height,domain:pack.id,status:output.status,reason:output.reason,achieved:output.result.actualGaugeHeight,wall_s:record.wall_s,cpu_s:record.cpu_s,controller:record.controller.strategy,fresh_simulations:record.controller.total_fresh_simulations}));
 if(output.status!=='accepted')process.exitCode=2;
}
