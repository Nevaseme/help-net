import test from 'node:test';
import assert from 'node:assert/strict';
import * as municipalities from '../web/municipalities.mjs';
import {placeMapLabel} from '../web/map-label-layout.mjs';

test('earthquake area follows the selected group area without a second entry',()=>{
 assert.equal(typeof municipalities.incidentAreaForGroup,'function');
 assert.equal(municipalities.incidentAreaForGroup({municipality:{name:'京都市'},groupName:'北班'}),'京都市');
 assert.equal(municipalities.incidentAreaForGroup({municipality:{name:'港区'},groupName:'北班',fictional:true}),'京都市');
 assert.equal(municipalities.incidentAreaForGroup({groupName:'北班'}),'北班');
});
test('neighborhood placement keeps the previous free position while panning',()=>{
 const previous={x:100,y:200,w:136,h:112};
 assert.deepEqual(placeMapLabel({x:120,y:230},{x:390,y:600},[],136,112,previous),previous);
});
test('neighborhood placement avoids icons when space is available',()=>{
 const occupied=[{x:100,y:200,w:136,h:112}];
 const rect=placeMapLabel({x:120,y:230},{x:390,y:600},occupied,136,112,occupied[0]);
 const o=occupied[0];
 assert.equal(Math.max(0,Math.min(rect.x+rect.w,o.x+o.w)-Math.max(rect.x,o.x))*Math.max(0,Math.min(rect.y+rect.h,o.y+o.h)-Math.max(rect.y,o.y)),0);
});
