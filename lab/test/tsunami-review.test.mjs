import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {assertReviewedResultEligible} from '../../tools/tsunami/build_map_data.mjs';

test('rejected immutable result cannot be rebuilt even after directory renaming',()=>{
 const manifest='{"id":"synthetic-rejected-case"}',hash=createHash('sha256').update(manifest).digest('hex');
 const registry={schema:'pland-tsunami-result-reviews-1',reviews:[{manifest_sha256:hash,status:'rejected_boundary_screen',reason:'Synthetic fixture reaches a cut edge'}]};
 assert.throws(()=>assertReviewedResultEligible(manifest,registry),/cut edge/);
 assert.doesNotThrow(()=>assertReviewedResultEligible('{"id":"different-unreviewed-case"}',registry));
 assert.throws(()=>assertReviewedResultEligible(manifest,{reviews:[]}),/registry/);
});
