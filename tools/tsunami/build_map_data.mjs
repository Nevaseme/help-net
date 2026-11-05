/** Derive small, shared display data from immutable, hash-checked ANUGA results. */
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {parseTsunamiResult,extractGroundInundation} from '../../lab/web/tsunami-results.mjs';

/** Reviews are keyed to immutable input bytes, never a mutable filename alone. */
export function assertReviewedResultEligible(manifestText,registry){
 if(registry?.schema!=='pland-tsunami-result-reviews-1'||!Array.isArray(registry.reviews))throw Error('Invalid result review registry');
 const hash=createHash('sha256').update(manifestText).digest('hex');
 const rejected=registry.reviews.find(r=>r.manifest_sha256===hash&&r.status==='rejected_boundary_screen');
 if(rejected)throw Error(`Rejected boundary result: ${rejected.reason}`);
 // No review match does not mean validated. Normal source/model checks still apply.
}

export async function buildMapCase(directory){
 const read=file=>readFile(resolve(directory,file),'utf8');
 const manifestText=await read('manifest.json'),manifest=JSON.parse(manifestText);
 const reviews=JSON.parse(await readFile(new URL('./result-reviews.json',import.meta.url),'utf8'));
 assertReviewedResultEligible(manifestText,reviews);
 const input={manifest,scenario:await read('scenario.json'),mesh:await read('mesh.json'),summary:await read('summary.json')};
 const parsed=await parseTsunamiResult(input),display=extractGroundInundation(parsed),mesh=JSON.parse(input.mesh),meta=parsed.metadata,c=meta.coastal_control;
 const coords=mesh.nodes_lonlat;
 // Structured rectangular mesh: locate true projected corners, not a lon/lat bounding-box approximation.
 const xs=mesh.nodes_m.map(p=>p[0]),ys=mesh.nodes_m.map(p=>p[1]),xmin=Math.min(...xs),xmax=Math.max(...xs),ymin=Math.min(...ys),ymax=Math.max(...ys);
 const corners=[[xmin,ymin],[xmax,ymin],[xmax,ymax],[xmin,ymax]].map(([x,y])=>coords[mesh.nodes_m.findIndex(p=>p[0]===x&&p[1]===y)]);
 if(corners.some(p=>!p))throw Error('Missing domain corner');
 const triangle=mesh.triangles[c.reference_triangle];
 const reference=triangle.map(i=>coords[i]).reduce((a,p)=>[a[0]+p[0]/3,a[1]+p[1]/3],[0,0]);
 const area=display.inundation.features.reduce((total,f)=>total+mesh.areas_m2[f.id],0);
 return {id:meta.id,title:`海岸基準高${c.target_height_m}m・${meta.grid.width_m/meta.grid.nx}m格子`,target_height_m:c.target_height_m,achieved_height_m:c.achieved_height_m,residual_m:c.residual_m,grid_spacing_m:meta.grid.width_m/meta.grid.nx,duration_s:meta.duration_s,threshold_m:meta.arrival_depth_m,inundated_area_m2:area,max_ground_depth_m:Math.max(...display.ground.features.map(f=>f.properties.max_depth_m)),coverage:{type:'Polygon',coordinates:[[...corners,corners[0]]]},reference:{type:'Point',coordinates:reference},features:display.inundation,provenance:{manifest_sha256:createHash('sha256').update(manifestText).digest('hex'),scenario:manifest.scenario,mesh:manifest.mesh,summary_json:manifest.summary_json,terrain:meta.terrain},conditions:{baseline_m:c.baseline_m,pulse_duration_s:c.pulse_duration_s,manning_n:meta.manning_n,boundaries:meta.boundaries,forcing_amplitude_m:c.forcing_amplitude_m,solver:meta.solver},limitations:['固定した波形・潮位・方向による条件計算です。実際の津波に対する予測精度は未検証です。','枠の外は未計算です。色がない場所の安全を示すものではありません。','地形の細かさで結果が変わります。建物単位の損傷や防潮堤の効果を保証しません。']};
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const [output,...inputs]=process.argv.slice(2);if(!output||!inputs.length)throw Error('Usage: node build_map_data.mjs output.json bundle-directory...');
 const cases=await Promise.all(inputs.map(buildMapCase));
 const data={schema:'pland-tsunami-map-1',site:'熱海市中心部',default_case_id:cases[0].id,cases};
 await mkdir(dirname(resolve(output)),{recursive:true});await writeFile(output,JSON.stringify(data));
 console.log(JSON.stringify({output,cases:cases.map(c=>({id:c.id,cells:c.features.features.length,area_m2:c.inundated_area_m2})),bytes:Buffer.byteLength(JSON.stringify(data))}));
}
