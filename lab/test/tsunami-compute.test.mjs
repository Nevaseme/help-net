import test from 'node:test';
import assert from 'node:assert/strict';
import {parseTsunamiHeight,validateTerrainPack,mapComputedResult,createTsunamiComputer,compareBoundarySensitivity} from '../web/tsunami-compute.mjs';
function fixture(){return {schema:'pland-tsunami-browser-domain-1',id:'synthetic-test',solver:{version:'test-solver',implementation_sha256:'b'.repeat(64)},config:{nx:2,ny:1,dx:1,dy:1,bed:[-1,1],baseline_m:0,reference_index:0,calibration:{tolerance_m:.01}},nodes_lonlat:[[139,35],[139.001,35],[139.002,35],[139,35.001],[139.001,35.001],[139.002,35.001]],reference_point:[139.0005,35.0005],provenance:{vertical_datum_status:'verified',source_sha256:'a'.repeat(64),sources:[{url:'https://example.test/fixture'}]},review:{status:'experimental-checked'}};}
const output=(height=2)=>({status:'accepted',result:{actualGaugeHeight:height,maxDepth:new Float64Array([3,.5]),diagnostics:{completed:true,solver:'test-solver'},boundaryAudit:{inundation_reaches_closed_cut_boundary:false,supercritical_open_boundary:false}},calibration:{target_height_m:height}});
function pairedFixture(){const p=fixture();p.config.boundaries={top:{type:'transmissive'}};p.reporting_window=[0,0,2,1];p.comparison={config:{...structuredClone(p.config),ny:2,bed:[-1,1,-1,1]},primary_to_secondary:[0,1]};return p;}
function secondaryOutput(height=2){const o=output(height);o.result.maxDepth=new Float64Array([3,.5,3,.5]);return o;}
test('decimal heights remain exact; invalid inputs never become a nearest-height result',()=>{
 assert.equal(parseTsunamiHeight('12.5'),12.5);assert.equal(parseTsunamiHeight('0'),0);
 for(const value of [true,null,undefined,'', ' ',NaN,Infinity,-1,'10m'])assert.throws(()=>parseTsunamiHeight(value));
});
test('unreviewed geography and missing samples are rejected before calculation',()=>{
 const p=fixture();p.review.status='unreviewed';assert.throws(()=>validateTerrainPack(p),/検証中/);
 const q=fixture();q.config.bed[0]=null;assert.throws(()=>validateTerrainPack(q),/欠損/);
});
test('only computed initially-land cells become polygons and true water depth is preserved',()=>{
 const result=mapComputedResult(fixture(),output(2),2);
 assert.equal(result.geojson.features.length,1);assert.equal(result.geojson.features[0].properties.max_depth_m,.5);
 assert.equal(result.heightM,2);assert.equal(result.metadata.inundatedAreaM2,1);
 assert.equal(result.metadata.realWorldValidated,false);
 assert.throws(()=>mapComputedResult(fixture(),{...output(),status:'rejected'},2));
 assert.throws(()=>mapComputedResult(fixture(),output(3),2),/高さ/);
});
test('worker receives exact height, stale messages are ignored and successful worker is released',async()=>{
 let sent,terminated=false;
 const worker={postMessage:m=>{sent=m;queueMicrotask(()=>{worker.onmessage({data:{type:'result',id:'old',...output(3)}});worker.onmessage({data:{type:'result',id:m.id,...output(12.5)}});});},terminate:()=>{terminated=true;}};
 const compute=createTsunamiComputer({loadPack:async()=>fixture(),createWorker:()=>worker});
 const result=await compute(12.5);assert.equal(sent.targetHeight,12.5);assert.equal(result.heightM,12.5);assert.equal(terminated,true);
});
test('cancel terminates computation and does not return late results',async()=>{
 const control=new AbortController();let worker,terminated=false;
 const compute=createTsunamiComputer({loadPack:async()=>fixture(),createWorker:()=>worker={postMessage:m=>queueMicrotask(()=>control.abort()),terminate:()=>{terminated=true;}}});
 await assert.rejects(compute(2,{signal:control.signal}),{name:'AbortError'});assert.equal(terminated,true);
});
test('cancel during a slow terrain download returns immediately without creating a worker',async()=>{
 const control=new AbortController();let complete,created=false;
 const compute=createTsunamiComputer({loadPack:()=>new Promise(resolve=>{complete=resolve;}),createWorker:()=>{created=true;throw Error('must not create worker');}});
 const result=compute(2,{signal:control.signal});await Promise.resolve();control.abort();
 await assert.rejects(result,{name:'AbortError'});assert.equal(created,false);complete(fixture());
});
test('outflow domains require matched-terrain comparison and validated output from both domains',()=>{
 const p=pairedFixture();assert.equal(validateTerrainPack(p),p);
 const missing=structuredClone(p);delete missing.comparison;assert.throws(()=>validateTerrainPack(missing),/照合/);
 const mismatched=structuredClone(p);mismatched.comparison.config.bed[1]+=.01;assert.throws(()=>validateTerrainPack(mismatched),/地形/);
 assert.throws(()=>mapComputedResult(p,output(),2),/条件/);
 const first=output(),second=secondaryOutput();second.result.maxDepth[1]=.52;
 const result=mapComputedResult(p,first,2,second);assert.equal(result.metadata.boundaryComparison.footprintIoU,1);
 assert.ok(result.metadata.boundaryComparison.meanWetDepthDifferenceM<.021);
 second.result.maxDepth[1]=.7;assert.throws(()=>compareBoundarySensitivity(p,first,second,2),/大きく変わる/);
 second.result.maxDepth[1]=.09;assert.throws(()=>compareBoundarySensitivity(p,first,second,2),/大きく変わる/);
});
test('paired calculation runs exact input twice and returns only within reporting area',async()=>{
 const sent=[];let terminated=0;
 const p=pairedFixture();p.reporting_window=[0,0,1,1];
 const compute=createTsunamiComputer({loadPack:async()=>p,createWorker:()=>{
  const worker={postMessage:m=>{sent.push(m);queueMicrotask(()=>worker.onmessage({data:{type:'result',id:m.id,...(m.config.ny===2?secondaryOutput(12.5):output(12.5))}}));},terminate:()=>{terminated++;}};return worker;
 }});
 const result=await compute(12.5);assert.deepEqual(sent.map(x=>x.targetHeight),[12.5,12.5]);assert.equal(terminated,2);
 assert.equal(result.geojson.features.length,0);assert.equal(result.bounds[1][0],139.001);
});
test('paired map does not display shallow footprint excluded from its sensitivity screen',()=>{
 const p=pairedFixture(),a=output(),b=secondaryOutput();a.result.maxDepth[1]=.09;b.result.maxDepth[1]=0;
 const r=mapComputedResult(p,a,2,b);assert.equal(r.geojson.features.length,0);assert.equal(r.metadata.displayDepthThresholdM,.1);
 const identical=pairedFixture();identical.comparison.config=structuredClone(identical.config);assert.throws(()=>validateTerrainPack(identical),/違う範囲/);
});
test('incomplete or wrong solver outputs never become map polygons',()=>{
 for(const mutate of [r=>r.result.diagnostics.completed=false,r=>r.result.diagnostics.solver='another',r=>r.result.boundaryAudit.inundation_reaches_closed_cut_boundary=true,r=>r.result.maxDepth[1]=NaN]){
  const r=output();mutate(r);assert.throws(()=>mapComputedResult(fixture(),r,2));
 }
});
