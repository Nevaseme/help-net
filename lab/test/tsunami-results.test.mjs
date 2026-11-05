import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import * as adapter from '../web/tsunami-results.mjs';

// Entirely synthetic projected fixture; these coordinates/data are not Atami results.
function fixture(height=1) {
 const scenario={schema:'pland-tsunami-scenario-1',id:'synthetic-coast',title:'Synthetic adapter fixture',kind:'benchmark',forcing_source:'Synthetic pulse',source_reference:'https://anuga.readthedocs.io/en/stable/background.html',grid:{crs:'EPSG:6676'},terrain:{kind:'synthetic',source:'Hand-built test bed',vertical_datum:'synthetic zero'},duration_s:100,manning_n:0.025,arrival_depth_m:0.01,boundaries:{right:{type:'flather_stage'}},initial:{stage_m:0},coastal_control:{schema:'pland-coastal-control-1',definition:'model coastal crest rise above initial still-water baseline',target_height_m:height,achieved_height_m:height+0.002,residual_m:0.002,tolerance_m:0.01,baseline_m:0,pulse_duration_s:30,pulse_delay_s:5,boundary:'right',reference_triangle:0,reference_point_m:[0,0],realised_reference_point_m:[0,0],forcing_amplitude_m:0.8,pulse_shape:'single positive raised cosine',real_world_validated:false}};
 const mesh={crs:'EPSG:6676',nodes_lonlat:[[139,35],[139.001,35],[139.001,35.001],[139,35.001],[138.999,35]],triangles:[[0,1,2],[0,2,3],[0,3,4]],bed_elevation_m:[-2,1,2]};
 const summary={max_depth_m:[height+2.002,0.25,0],initially_dry:[false,true,true],arrival_time_s:[0,12,null]};
 const manifest={schema:'pland-tsunami-result-1',id:scenario.id,title:scenario.title,kind:scenario.kind,solver:{name:'ANUGA',version:'3.3.10',algorithm:'DE1'},units:{depth:'m',momentum:'m2/s',time:'s',coordinates:'m'},arrival_depth_m:0.01,validation:{status:'experimental-unvalidated',real_world_validated:false},limitations:['Synthetic fixture; no real coastal validation.']};
 return {scenario,mesh,summary,manifest};
}
function encoded(f=fixture()) {
 const input={manifest:structuredClone(f.manifest)};
 for(const [key,file,slot] of [['scenario','scenario.json','scenario'],['mesh','mesh.json','mesh'],['summary','summary.json','summary_json']]) {
  input[key]=JSON.stringify(f[key]);input.manifest[slot]={file,bytes:Buffer.byteLength(input[key]),sha256:createHash('sha256').update(input[key]).digest('hex')};
 }
 return input;
}

test('synthetic bundle preserves measured zero separately from positive land inundation and sea',async()=>{
 assert.equal(typeof adapter.parseTsunamiResult,'function');
 const input=encoded(),result=await adapter.parseTsunamiResult(input);
 assert.equal(result.land.features.length,2);assert.equal(result.inundation.features.length,1);
 assert.deepEqual(result.land.features.map(f=>[f.id,f.properties.max_depth_m]),[[1,0.25],[2,0]]);
 assert.deepEqual(result.inundation.features[0].geometry.coordinates,[[[139,35],[139.001,35.001],[139,35.001],[139,35]]]);
 assert.equal(result.metadata.coastal_control.target_height_m,1);assert.equal(result.metadata.coastal_control.achieved_height_m,1.002);
 assert.equal(result.metadata.coastal_control.residual_m,0.002);assert.equal(result.metadata.terrain.source,'Hand-built test bed');
 assert.equal(result.metadata.kind,'benchmark');assert.equal(result.metadata.validation.real_world_validated,false);
 assert.deepEqual(input,encoded());
});

test('rejects missing cell values, nonfinite/negative depths, nonboolean masks and broken geometry',async()=>{
 for(const change of [f=>f.summary.max_depth_m.pop(),f=>f.summary.max_depth_m[1]=null,f=>f.summary.max_depth_m[1]=-1,f=>f.summary.initially_dry[1]=1,f=>delete f.mesh.nodes_lonlat,f=>f.mesh.triangles[1][0]=99,f=>f.mesh.triangles[1]=[0,0,1],f=>f.mesh.nodes_lonlat[1][1]=91]) {
  const f=fixture();change(f);await assert.rejects(()=>adapter.parseTsunamiResult(encoded(f)));
 }
});

test('rejects cross-bundle content, missing provenance, inconsistent calibration and unsupported schema',async()=>{
 const corrupt=encoded();corrupt.summary=corrupt.summary.replace('0.25','0.26');await assert.rejects(()=>adapter.parseTsunamiResult(corrupt),/hash|sha256/i);
 for(const change of [f=>f.scenario.id='other',f=>delete f.scenario.terrain.source,f=>f.manifest.schema='future',f=>f.manifest.units.depth='ft',f=>f.scenario.coastal_control.residual_m=3,f=>f.scenario.coastal_control.achieved_height_m=5,f=>f.scenario.coastal_control.reference_triangle=1]) {
  const f=fixture();change(f);await assert.rejects(()=>adapter.parseTsunamiResult(encoded(f)));
 }
});

test('exact computed height selection never interpolates, substitutes nearest or chooses between conditions',async()=>{
 const one=await adapter.parseTsunamiResult(encoded()),three=await adapter.parseTsunamiResult(encoded(fixture(3)));
 assert.equal(adapter.selectTsunamiResult([one,three],1).result,one);
 assert.deepEqual(adapter.selectTsunamiResult([one,three],2),{status:'unavailable',reason:'not_computed',requested_height_m:2,available_heights_m:[1,3]});
 assert.equal(adapter.selectTsunamiResult([one,structuredClone(one)],1).reason,'ambiguous_conditions');
 assert.throws(()=>adapter.selectTsunamiResult([one],NaN));assert.throws(()=>adapter.selectTsunamiResult([one],'1'));
});

test('site metadata requires declared datum verification and original terrain URLs/hashes',async()=>{
 const f=fixture();f.scenario.kind=f.manifest.kind='site-scenario';f.scenario.terrain={...f.scenario.terrain,kind:'measured',vertical_datum_status:'unverified',source_sha256:'a'.repeat(64),sources:[{url:'https://example.invalid/synthetic-fixture-only.zip',sha256:'b'.repeat(64)}]};
 await assert.rejects(()=>adapter.parseTsunamiResult(encoded(f)),/provenance/);
 f.scenario.terrain.vertical_datum_status='verified';
 const result=await adapter.parseTsunamiResult(encoded(f));assert.equal(result.metadata.terrain.sources[0].url,'https://example.invalid/synthetic-fixture-only.zip');
 delete f.scenario.terrain.sources[0].sha256;await assert.rejects(()=>adapter.parseTsunamiResult(encoded(f)),/source/);
});

test('a height-matched but boundary-rejected calculation cannot enter the display adapter',async()=>{
 for(const change of [f=>f.scenario.publication_status='rejected_boundary_screen',
  f=>f.scenario.publication_status='failed_not_accepted',
  f=>f.scenario.boundary_audit={inundation_reaches_closed_cut_boundary:true},
  f=>f.scenario.boundary_audit={supercritical_open_boundary_observed_in_frames:true}]){
  const f=fixture();change(f);await assert.rejects(()=>adapter.parseTsunamiResult(encoded(f)),/Rejected|Boundary/);
 }
 const f=fixture();f.scenario.boundary_audit={inundation_reaches_closed_cut_boundary:false,supercritical_open_boundary_observed_in_frames:false};
 const result=await adapter.parseTsunamiResult(encoded(f));assert.equal(result.metadata.validation.real_world_validated,false);
});

test('zero target, tiny positive inundation and unreached arrival are distinct values',async()=>{
 const f=fixture(0);f.scenario.coastal_control.achieved_height_m=0;f.scenario.coastal_control.residual_m=0;f.summary.max_depth_m=[2,0.000001,0];f.summary.arrival_time_s=[0,null,null];
 const result=await adapter.parseTsunamiResult(encoded(f));
 assert.equal(adapter.selectTsunamiResult([result],0).status,'available');assert.equal(result.inundation.features[0].properties.max_depth_m,0.000001);assert.equal(result.inundation.features[0].properties.arrival_time_s,null);
 assert.equal(adapter.selectTsunamiResult([result],0.000001).status,'unavailable');
 const input=encoded(f);input.manifest.summary_json.bytes++;await assert.rejects(()=>adapter.parseTsunamiResult(input),/bytes/);
});

test('display extraction excludes shallow initial water and subthreshold depth without changing parsed cells',async()=>{
 const f=fixture();
 f.mesh.nodes_lonlat=[];f.mesh.triangles=[];
 for(let i=0;i<6;i++){const x=139+i*0.001;f.mesh.nodes_lonlat.push([x,35],[x+0.0005,35],[x,35.0005]);f.mesh.triangles.push([3*i,3*i+1,3*i+2]);}
 f.mesh.bed_elevation_m=[-2,-0.0005,1,0,2,1];
 f.summary={max_depth_m:[3.002,0.25,0.000001,0.01,0,0.25],initially_dry:[false,true,true,true,true,true],arrival_time_s:[0,10,null,12,null,15]};
 const parsed=await adapter.parseTsunamiResult(encoded(f));
 assert.deepEqual(parsed.land.features.map(f=>f.properties.bed_elevation_m),[-0.0005,1,0,2,1]);
 const before=structuredClone(parsed),display=adapter.extractGroundInundation(parsed);
 assert.deepEqual(display.ground.features.map(f=>f.id),[2,3,4,5]);
 assert.deepEqual(display.inundation.features.map(f=>[f.id,f.properties.max_depth_m]),[[3,0.01],[5,0.25]]);
 assert.equal(display.metadata.initial_water_level_m,0);assert.equal(display.metadata.minimum_max_depth_m,0.01);
 assert.equal(display.metadata.outside_domain,'unknown');assert.match(display.metadata.definition,/model cells/);assert.equal(display.metadata.surveyed_coastline,false);
 assert.deepEqual(parsed,before);assert.deepEqual(parsed.inundation.features.map(f=>f.id),[1,2,3,5]);
 f.manifest.arrival_depth_m=f.scenario.arrival_depth_m=0.2;
 assert.deepEqual(adapter.extractGroundInundation(await adapter.parseTsunamiResult(encoded(f))).inundation.features.map(f=>f.id),[5]);
});

test('display extraction uses declared tide and threshold, preserves zero and rejects missing values',async()=>{
 const f=fixture();f.scenario.coastal_control.baseline_m=1;f.scenario.initial.stage_m=1;f.summary.max_depth_m[0]=4.002;
 f.manifest.arrival_depth_m=f.scenario.arrival_depth_m=0.2;f.mesh.bed_elevation_m=[-2,0.999,1];
 const parsed=await adapter.parseTsunamiResult(encoded(f)),display=adapter.extractGroundInundation(parsed);
 assert.deepEqual(display.ground.features.map(f=>[f.id,f.properties.max_depth_m]),[[2,0]]);assert.equal(display.inundation.features.length,0);assert.equal(display.metadata.initial_water_level_m,1);assert.equal(display.metadata.minimum_max_depth_m,0.2);
 for(const property of ['bed_elevation_m','max_depth_m']){const incomplete=structuredClone(parsed);delete incomplete.land.features[0].properties[property];assert.throws(()=>adapter.extractGroundInundation(incomplete));}
});
