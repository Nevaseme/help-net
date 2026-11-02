// Resumable numerical batch. A failed individual or paired screen stops new jobs.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {validateTerrainPack,compareBoundarySensitivity} from '../../lab/web/tsunami-compute.mjs';
import {validateSourcePack,validateSourceRecord} from './build_precomputed.mjs';
const [pairPath,largePath,smallPath,outPath,workersText='4']=process.argv.slice(2);
if(!outPath)throw Error('Usage: run_precompute_batch.mjs reviewed-pair.json primary.json secondary.json OUT_DIR [WORKERS]');
const workers=Number(workersText);if(!Number.isInteger(workers)||workers<1||workers>8)throw Error('Use 1 to 8 workers');
const pair=validateTerrainPack(JSON.parse(await readFile(pairPath,'utf8'))),sources=[];
for(const path of [largePath,smallPath]){const bytes=await readFile(path);sources.push({path:resolve(path),pack:JSON.parse(bytes),sha:createHash('sha256').update(bytes).digest('hex')});}
if(pair.primary_domain_id!==sources[0].pack.id||pair.comparison.domain_id!==sources[1].pack.id)throw Error('Mismatched paired sources');
sources.forEach((source,index)=>validateSourcePack(pair,source.pack,source.sha,index?'secondary':'primary'));
const out=resolve(outPath);await mkdir(out,{recursive:true});
const manifest=Array.from({length:36},(_,height)=>({height,primary:join(out,sources[0].pack.id,`height-${height}.json`),secondary:join(out,sources[1].pack.id,`height-${height}.json`)}));
const jobs=manifest.flatMap(row=>sources.map((source,index)=>({height:row.height,source,output:index?row.secondary:row.primary,index})));
const finished=new Map(),paired=[],failures=[];let next=0,stopping=false,saving=Promise.resolve();
const executable=fileURLToPath(new URL('./compute_precomputed_case.mjs',import.meta.url));
function save(){const body=JSON.stringify({completedPairs:paired.length,totalPairs:36,paired:[...paired].sort((a,b)=>a.height-b.height),failures,stopped:stopping},null,2);saving=saving.then(()=>writeFile(join(out,'progress.json'),body));return saving;}
async function run(){while(!stopping&&next<jobs.length){
 const job=jobs[next++];
 const code=await new Promise((resolve,reject)=>{const child=spawn(process.execPath,[executable,job.source.path,String(job.height),job.output],{stdio:['ignore','inherit','inherit'],windowsHide:true});child.once('error',reject);child.once('exit',resolve);});
 if(code!==0){stopping=true;failures.push({height:job.height,domain:job.source.pack.id,reason:'Individual calculation rejected or failed',exitCode:code});await save();continue;}
 const record=JSON.parse(await readFile(job.output,'utf8'));
 if(record.source?.domain_sha256!==job.source.sha||record.source?.solver_sha256!==pair.solver.implementation_sha256)throw Error('Output provenance mismatch');
 validateSourceRecord(pair,record,job.height,job.index?'secondary':'primary');
 const done=finished.get(job.height)||[];done[job.index]=record;finished.set(job.height,done);
 if(done[0]&&done[1]){
  try{const comparison=compareBoundarySensitivity(pair,done[0],done[1],job.height);paired.push({height:job.height,comparison});console.log(JSON.stringify({height:job.height,pairedScreen:'passed',completed:paired.length,total:36}));}
  catch(error){stopping=true;failures.push({height:job.height,reason:error.message});}
  await save();
 }
}}
await Promise.all(Array.from({length:workers},()=>run().catch(error=>{stopping=true;failures.push({reason:String(error)});return save();})));
await save();
if(failures.length||paired.length!==36){process.exitCode=2;console.error('Batch incomplete; valid earlier outputs retained, no publishable manifest emitted.');}
else{await writeFile(join(out,'source-manifest.json'),JSON.stringify(manifest,null,2));console.log('All 36 paired calculations passed. Source manifest saved.');}
