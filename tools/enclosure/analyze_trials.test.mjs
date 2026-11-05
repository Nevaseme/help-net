import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizeChannel,analyzeTrials} from './analyze_trials.mjs';
const light=samples=>({channel:'light',state:'registered',unit:'lux',sensor:{reportingMode:1},samples:samples.map(([elapsedNanos,x])=>({elapsedNanos,values:[x]}))});
test('on-change lux uses held durations rather than event counts',()=>{
 const r=summarizeChannel(light([[0,0],[9e9,100]]),8e9,12e9);assert.equal(r.observed.mean,75);assert.equal(r.covered_fraction,1);assert.equal(r.changes,1);
});
test('zero lux, missing data and a partly unobserved window remain distinct',()=>{
 assert.equal(summarizeChannel(light([[0,0]]),8e9,12e9).observed.mean,0);
 assert.equal(summarizeChannel(light([]),8e9,12e9).observed,null);
 const r=summarizeChannel(light([[10e9,50]]),8e9,12e9);assert.equal(r.covered_fraction,.5);assert.equal(r.observed.mean,50);
});
test('continuous motion excludes setup/removal and records direction changes',()=>{
 const channel={channel:'acceleration',state:'registered',sensor:{reportingMode:0},samples:[{elapsedNanos:1e9,values:[100,0,0]},{elapsedNanos:9e9,values:[1,0,0]},{elapsedNanos:10e9,values:[-1,0,0]}]};
 const r=summarizeChannel(channel,8e9,12e9);assert.equal(r.observed.mean,1);assert.equal(r.centered_vector_rms,1);assert.equal(r.maximum_gap_seconds,1);
 assert.equal(analyzeTrials({trials:[]}).classification,'not-configured');
});
