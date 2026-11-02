import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import {mapComputedResult,validateTerrainPack} from '../../lab/web/tsunami-compute.mjs';
import {validatePrecomputedBundle,validateCalculationProof,PROOF_CALIBRATION_FIELDS,PROOF_DIAGNOSTIC_FIELDS,PROOF_BOUNDARY_FIELDS} from '../../lab/web/tsunami-precomputed.mjs';
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const pick=(object,keys)=>Object.fromEntries(keys.map(key=>[key,object?.[key]]));
export function validationContracts(pack){
 const contracts={};
 for(const side of ['primary','secondary']){
  const config=side==='primary'?pack.config:pack.comparison.config,source=pack.source_domains?.[side];
  const id=side==='primary'?pack.primary_domain_id:pack.comparison.domain_id;
  if(source?.id!==id||!/^[a-f0-9]{64}$/.test(source?.sha256||''))throw Error('Reviewed source_domains raw-file hashes required');
  contracts[side]={...source,duration_s:config.duration_s,tolerance_m:config.calibration.tolerance_m,maximum_forcing_m:config.calibration.maximum_forcing_m,reference_index:config.reference_index,reference_bed_m:config.bed[config.reference_index],baseline_m:config.baseline_m};
 }
 return contracts;
}
export function validateSourcePack(pack,source,rawSha,side){
 const expected=validationContracts(pack)[side],config=side==='primary'?pack.config:pack.comparison.config,provenance=side==='primary'?pack.provenance:pack.comparison.provenance;
 if(source.id!==expected.id||rawSha!==expected.sha256||!isDeepStrictEqual(source.config,config)||!isDeepStrictEqual(source.solver,pack.solver)||!isDeepStrictEqual(source.provenance,provenance))throw Error('Source pack contents or raw hash differ from reviewed paired input');
 return source;
}
export function validateSourceRecord(pack,record,height,side){
 const contract=validationContracts(pack)[side],config=side==='primary'?pack.config:pack.comparison.config;
 if(record?.status!=='accepted'||record.height!==height||record.domain_id!==contract.id)throw Error('Wrong or rejected source domain or height');
 const r=record.result;
 if(!r||!(Array.isArray(r.maxDepth)||ArrayBuffer.isView(r.maxDepth))||r.maxDepth.length!==config.nx*config.ny)throw Error('Incomplete source water-depth field');
 const proof={domain_id:record.domain_id,source:record.source,achieved_height_m:r.actualGaugeHeight,reference_depth_m:r.maxDepth[config.reference_index],calibration:pick(record.calibration,PROOF_CALIBRATION_FIELDS),diagnostics:pick(r.diagnostics,PROOF_DIAGNOSTIC_FIELDS),boundary_audit:pick(r.boundaryAudit,PROOF_BOUNDARY_FIELDS)};
 return validateCalculationProof(proof,contract,height,pack.solver);
}
export function buildPrecomputedBundle(pack,pairs){
 validateTerrainPack(pack);if(!pack.comparison||!pack.primary_domain_id)throw Error('Reviewed paired domain required');
 const contracts=validationContracts(pack);
 if(pairs.length!==36)throw Error('Exactly 36 source pairs required');
 const w=pack.reporting_window,c=pack.config,indices=[],nodes=[];
 for(let y=w[1];y<w[3];y++)for(let x=w[0];x<w[2];x++)indices.push(y*c.nx+x);
 for(let y=w[1];y<=w[3];y++)for(let x=w[0];x<=w[2];x++)nodes.push(pack.nodes_lonlat[y*(c.nx+1)+x]);
 const cases=pairs.map(({primary,secondary,sourceHashes},height)=>{
  if(primary.domain_id!==pack.primary_domain_id||secondary.domain_id!==pack.comparison.domain_id||primary.height!==height||secondary.height!==height)throw Error('Wrong source domain or height');
  const source_proofs={primary:validateSourceRecord(pack,primary,height,'primary'),secondary:validateSourceRecord(pack,secondary,height,'secondary')};
  const mapped=mapComputedResult(pack,primary,height,secondary);
  return {height_m:height,achieved_height_m:mapped.achievedHeightM,max_depth_m:indices.map(i=>primary.result.maxDepth[i]),calibration:primary.calibration,diagnostics:primary.result.diagnostics,boundary_audit:primary.result.boundaryAudit,boundary_comparison:mapped.metadata.boundaryComparison,source_hashes:sourceHashes,source_proofs};
 });
 const bundle={schema:'pland-tsunami-precomputed-1',id:pack.id+'-0-to-35',domain_id:pack.id,minimum_height_m:0,maximum_height_m:35,step_m:1,
  grid:{nx:w[2]-w[0],ny:w[3]-w[1],dx:c.dx,dy:c.dy,baseline_m:c.baseline_m,bed:indices.map(i=>c.bed[i]),nodes_lonlat:nodes,reference_index:indices.includes(c.reference_index)?indices.indexOf(c.reference_index):null,...(pack.display_cells?{display_cells:indices.map(i=>pack.display_cells[i])}:{})},reference_point:pack.reference_point,provenance:pack.provenance,solver:pack.solver,review:pack.review,source_domains:pack.source_domains,validation_contracts:contracts,cases};
 return validatePrecomputedBundle(bundle);
}
async function main(){
 const [packPath,manifestPath,outputPath]=process.argv.slice(2);if(!outputPath)throw Error('Usage: build_precomputed.mjs reviewed-pair.json source-manifest.json NEW-output.json');
 const pack=JSON.parse(await readFile(packPath,'utf8')),manifest=JSON.parse(await readFile(manifestPath,'utf8')),pairs=[];
 for(const row of manifest){const a=await readFile(row.primary),b=await readFile(row.secondary);pairs.push({primary:JSON.parse(a),secondary:JSON.parse(b),sourceHashes:{primary:sha(a),secondary:sha(b)}});}
 const bundle=buildPrecomputedBundle(pack,pairs);await writeFile(outputPath,JSON.stringify(bundle),{flag:'wx'});console.log(JSON.stringify({id:bundle.id,cases:bundle.cases.length,bytes:Buffer.byteLength(JSON.stringify(bundle))}));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)await main();
