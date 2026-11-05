import test from 'node:test';
import assert from 'node:assert/strict';
import {findReportingWindow} from '../../tools/tsunami/expand_reporting_window.mjs';

function fixture(){
 const nx=8,ny=7,n=nx*ny;
 const pack={config:{nx,ny,bed:Array(n).fill(1),baseline_m:0},reporting_window:[3,3,4,4],comparison:{primary_to_secondary:Array.from({length:n},(_,i)=>i)}};
 const pairs=Array.from({length:3},()=>({primary:{result:{maxDepth:Array(n).fill(1)}},secondary:{result:{maxDepth:Array(n).fill(1)}}}));
 return {pack,pairs};
}
// Deliberately independent direct-cell oracle; no summed-area tables.
function brute(pack,pairs){
 const {nx,ny,bed}=pack.config,old=pack.reporting_window;let best=null,passing=0;
 for(let x0=1;x0<=old[0];x0++)for(let x1=old[2];x1<nx;x1++)for(let y0=1;y0<=old[1];y0++)for(let y1=old[3];y1<ny;y1++){
  let ok=true;
  for(const {primary,secondary} of pairs){let sum=0,union=0,intersection=0,max=0;
   for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++){
    const i=y*nx+x;if(bed[i]<0)continue;
    const a=primary.result.maxDepth[i],b=secondary.result.maxDepth[i],d=Math.abs(a-b);max=Math.max(max,d);
    if(a>=.1||b>=.1){sum+=d;union++;if(a>=.1&&b>=.1)intersection++;}
   }
   if(max>.5||(union&&sum/union>.1)||(union&&intersection/union<.9))ok=false;
  }
  if(ok){passing++;const cells=(x1-x0)*(y1-y0);if(!best||cells>best.cells)best={cells,window:[x0,y0,x1,y1]};}
 }
 return {best,passing};
}
test('exhaustive expanded-window search agrees with independent direct-cell screen',()=>{
 for(let seed=1;seed<=16;seed++){
  const {pack,pairs}=fixture();let state=seed;const random=()=>((state=(state*1664525+1013904223)>>>0)/2**32);
  for(let i=0;i<pack.config.bed.length;i++)if(i!==27){
   if(random()<.2)pack.config.bed[i]=-1;
   for(const pair of pairs){pair.primary.result.maxDepth[i]=random()<.2?.05:1;pair.secondary.result.maxDepth[i]=pair.primary.result.maxDepth[i]+(random()<.15?.65:random()*.12);}
  }
  const expected=brute(pack,pairs),actual=findReportingWindow(pack,pairs);
  assert.deepEqual(actual.window,expected.best.window);assert.equal(actual.cells,expected.best.cells);assert.equal(actual.passing_rectangles,expected.passing);
 }
});
test('dry and marine cells cannot dilute the wet-land mean difference',()=>{
 const {pack,pairs}=fixture();
 for(const pair of pairs){pair.primary.result.maxDepth.fill(0);pair.secondary.result.maxDepth.fill(0);pair.primary.result.maxDepth[27]=.2;pair.secondary.result.maxDepth[27]=.32;}
 assert.throws(()=>findReportingWindow(pack,pairs),/No passing/);
});
test('maximum difference veto survives a low mean over many matching wet cells',()=>{
 const {pack,pairs}=fixture();pairs[2].secondary.result.maxDepth[27]=1.6;
 assert.throws(()=>findReportingWindow(pack,pairs),/No passing/);
});
test('unmapped cells are outside the search and the requested edge buffer is retained',()=>{
 const {pack,pairs}=fixture();pack.comparison.primary_to_secondary=pack.comparison.primary_to_secondary.map((i)=>i<8?-1:i);
 assert.deepEqual(findReportingWindow(pack,pairs).window,[1,2,7,6]);
 assert.throws(()=>findReportingWindow(pack,pairs,{bufferCells:0}),/positive/);
});
