import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {translateUI} from '../web/web1617-english.mjs';
globalThis.document={documentElement:{lang:'en'}};
test('all server membership and compatibility messages have English translations',()=>{
 for(const name of ['venue.mjs','demo-auth.mjs','http.mjs','journal.mjs','worker.mjs']){
  const source=readFileSync(new URL('../../cloud-sync/src/'+name,import.meta.url),'utf8');
  for(const m of source.matchAll(/'([^'\n]*[\u3040-\u9fff][^'\n]*)'/g))assert.doesNotMatch(translateUI(m[1]),/[\u3040-\u9fff]/,name+': '+m[1]);
 }
});
test('nested save errors translate while user-entered names stay intact',()=>{
 assert.match(translateUI('保存できませんでした：先に登録してください。入力を確認して再試行してください。'),/Register your information first/);
 assert.equal(translateUI('山田ビル'),'山田ビル');
});
