import test from 'node:test';
import assert from 'node:assert/strict';
import * as municipalities from '../web/municipalities.mjs';

test('earthquake area follows the selected group area without a second entry',()=>{
 assert.equal(typeof municipalities.incidentAreaForGroup,'function');
 assert.equal(municipalities.incidentAreaForGroup({municipality:{name:'京都市'},groupName:'北班'}),'京都市');
 assert.equal(municipalities.incidentAreaForGroup({municipality:{name:'港区'},groupName:'北班',fictional:true}),'京都市');
 assert.equal(municipalities.incidentAreaForGroup({groupName:'北班'}),'北班');
});
