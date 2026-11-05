import test from 'node:test';
import assert from 'node:assert/strict';
import {validateShape} from '../src/validation.mjs';
const id=()=>crypto.randomUUID();
test('sensor impact can be stored without coordinates or a safety answer',()=>{
 const record={groupId:id(),eventId:id(),authorId:id(),sequence:1,createdAt:100,kind:'IMPACT',observedAt:90,source:'VIVO_DROP'};
 assert.deepEqual(validateShape('deviceRecords',record),record);
 assert.throws(()=>validateShape('deviceRecords',{...record,observedAt:101}));
});
test('demo home record carries one building and address, scoped to one session',()=>{
 const record={groupId:id(),eventId:id(),authorId:id(),sequence:1,createdAt:100,kind:'DEMO_HOME',sessionId:id(),home:{buildingId:id(),address:'京都市中京区試験町',latitude:35.014,longitude:135.76,plateauId:'bldg_test',floor:'2階',room:'寝室'}};
 assert.deepEqual(validateShape('deviceRecords',record),record);
 assert.throws(()=>validateShape('deviceRecords',{...record,sessionId:null}));
});
