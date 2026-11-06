import {adbExecutable} from '../../tools/browser-runtime.mjs';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {chromium} from '../../tools/browser-runtime.mjs';
const base=process.env.ADDRESS_PWA_BASE||'https://tasukeai-sync.tomodachi-takumi.workers.dev';
const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
let child,output='';
try{
 const page=await browser.newPage();await page.goto(base);
 const invite=await page.evaluate(async()=>{
  const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');
  const s=await Store.open('address-interop-'+crypto.randomUUID()),id=await s.identity(),client=new CloudClient(s,id);
  const session=await client.register('create',{groupName:'架空住所同期'+Date.now(),activityName:'架空PWA'});
  window.addressQA={s,id,client,group:session.groupId};return session.groupId+':'+(await client.invitation(session.groupId)).code;
 });
 child=spawn(adbExecutable(),['-s',process.env.ADDRESS_ANDROID_SERIAL||'emulator-5554','shell','am','instrument','-w','-r','-e','class','io.github.nevaseme.plandmk1.AddressCloudInteropTest','-e','cloudEndpoint',base,'-e','browserInvite',invite,'io.github.nevaseme.plandmk1.test/androidx.test.runner.AndroidJUnitRunner'],{windowsHide:true});
 child.stdout.on('data',b=>output+=b);child.stderr.on('data',b=>output+=b);const done=new Promise((resolve,reject)=>{child.on('close',resolve);child.on('error',reject);});
 let received=false;
 for(let i=0;i<80;i++){
  received=await page.evaluate(async()=>{const {s,id,client,group}=window.addressQA;await client.sync(group);const {placeBuildings,validHome}=await import('/places.mjs');const rows=await s.places(group),b=placeBuildings(rows).find(x=>x.authorId!==id);if(!b)return false;
   const ref=b.locationReference.searchReference;if(ref.precision!=='block'||ref.matchLevel!=='parcel'||ref.source!=='ISJ')throw Error('Lost provenance');if(b.latitude!==35.01105||validHome(rows,b.authorId)?.buildingId!==b.buildingId)throw Error('Lost home relationship');window.addressQA.ref=ref;return true;
  });if(received)break;await page.waitForTimeout(1000);
 }assert.ok(received,'PWA received Android confirmed home');
 await page.evaluate(async()=>{const {s,id,client,group,ref}=window.addressQA;
  await s.saveProfileWithHome({groupId:group,originId:id,revision:1,updatedAt:Date.now(),name:'架空PWA',latitude:35.0112,longitude:135.7748,floor:'',room:'',safety:'UNREPORTED'},{address:'PWA確認済み住居',searchReference:ref});await client.sync(group);
 });
 assert.equal(await done,0);assert.match(output,/OK \(1 test\)/);console.log('PASS public Android → PWA → Android: confirmed coordinates, source precision, home relationship and Room reopen');console.log(output);
}finally{await browser.close();}
