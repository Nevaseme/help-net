import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {Journal} from '../src/journal.mjs';
const token=()=>crypto.randomUUID().replaceAll('-','')+crypto.randomUUID().replaceAll('-','');
test('only the group administrator chooses the city; members receive it after restart',async t=>{
 const db=new DatabaseSync(':memory:');t.after(()=>db.close());
 const s={all:(q,...p)=>db.prepare(q).all(...p),run:(q,...p)=>db.prepare(q).run(...p),transaction:fn=>fn(),flush:async()=>{}};
 let j=new Journal(s);const a=await j.create({groupId:crypto.randomUUID(),deviceId:crypto.randomUUID(),activityName:'管理者',groupName:'市域試験',registrationToken:token()});
 const invitation=await j.invite(a.token),b=await j.join({deviceId:crypto.randomUUID(),activityName:'メンバー',code:invitation.code,registrationToken:token()});
 assert.equal(j.snapshot().municipality,null);
 await assert.rejects(()=>j.setMunicipality(b.token,{code:'26100'}),{code:'admin_required'});
 await assert.rejects(()=>j.setMunicipality(a.token,{code:'00000'}),{code:'unsupported_municipality'});
 const changed=await j.setMunicipality(a.token,{code:'26100'});
 assert.equal(changed.municipality.code,'26100');assert.equal(changed.municipality.name,'京都市');
 assert.equal(changed.rosterRevision,a.rosterRevision+1); // Joining changed membership; city changes do not.
 j=new Journal(s);assert.deepEqual((await j.roster(b.token)).municipality,changed.municipality);
 await assert.rejects(()=>j.setMunicipality(token(),{code:'22205'}),{code:'unauthorized'});
 await j.setMunicipality(a.token,{code:'22205'});assert.equal((await j.roster(b.token)).municipality.name,'熱海市');
});
