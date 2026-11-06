import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {chromium} from '../../tools/browser-runtime.mjs';
import {startVenueServer} from './venue-server.mjs';
const out=process.env.GROUP_START_OUTPUT||'output/group-start-pwa';await mkdir(out,{recursive:true});
const server=await startVenueServer({cityFixture:true,publicAssets:process.env.VENUE_PUBLIC_ASSETS||null});
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'}),errors=[],starts=[];
const newPage=async()=>{const c=await browser.newContext({viewport:{width:390,height:844},serviceWorkers:'block'});await c.addInitScript(()=>{navigator.serviceWorker.register=async()=>({waiting:null,update:async()=>{}});});const p=await c.newPage();p.on('pageerror',e=>errors.push(e.message));p.setDefaultTimeout(30000);await p.goto(server.base);return p;};
const groupName='Group start verification',password='JoinOnly123';let admin,member;
const audit=async p=>assert.doesNotMatch(await p.locator('body').innerText(),/[\u3040-\u30ff\u3400-\u9fff]/);
try{
 admin=await newPage();await admin.locator('#group-create summary').click();
 await admin.locator('#group-create-name').fill(groupName);await admin.locator('#group-create-person').fill('Organizer');await admin.locator('#group-create-password').fill(password);
 await audit(admin);await admin.screenshot({path:out+'/create-group.png'});await admin.locator('#group-create-form button[type=submit]').click();
 await admin.waitForFunction(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');const g=await s.read('meta','group'),session=await s.read('meta','cloud-session:'+g);s.close();return session?.role==='ADMIN';});
 await admin.locator('#menu-open').click();await admin.locator('[data-menu="incidents"]').click();
 await admin.locator('#venue-start').waitFor();assert.equal(await admin.locator('#venue-admin input[type=password]').count(),0);assert.equal(await admin.locator('#venue-participation').count(),0);
 await admin.screenshot({path:out+'/start-drill.png'});
 member=await newPage();await member.locator('#group-join-identity').fill(groupName);await member.locator('#group-join-password').fill('wrong');await member.locator('#group-next').click();await member.locator('#group-join-feedback').filter({hasText:/password/i}).waitFor();
 await member.locator('#group-join-password').fill(password);await member.locator('#group-next').click();await member.locator('#group-join-name').fill('Participant');await member.locator('#group-join').click();
 await member.waitForFunction(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');const g=await s.read('meta','group'),session=await s.read('meta','cloud-session:'+g);s.close();return session?.role==='MEMBER';});
 const snapshot=p=>p.evaluate(async()=>{const {Store}=await import('/sync.mjs');const s=await Store.open('tasukeai-cloud');const v=await s.read('meta','venue:'+await s.read('meta','group'));s.close();return v;});
 const waitVenue=async(p,predicate)=>{for(let i=0;i<175;i++){const v=await snapshot(p);if(predicate(v))return v;await p.waitForTimeout(200);}throw Error('Expected incident was not synchronized');};
 assert.ok((await snapshot(member))==null);
 for(const mode of ['DRILL','EARTHQUAKE']){
  if(mode==='EARTHQUAKE'){
   await admin.evaluate(async()=>{const {Store}=await import('/sync.mjs'),{CloudClient}=await import('/cloud-client.mjs');const s=await Store.open('tasukeai-cloud'),c=new CloudClient(s,await s.identity());await c.endVenue(await s.read('meta','group'));s.close();});
   await waitVenue(admin,v=>v?.closed===true);
   await admin.locator('#menu-open').click();await admin.locator('[data-menu="incidents"]').click();
  }
  await admin.locator('#venue-presentation-mode').selectOption(mode);await audit(admin);await admin.screenshot({path:out+'/start-'+mode.toLowerCase()+'.png'});
  const requests=[];const listener=r=>{if(r.method()==='POST'){console.log(JSON.stringify({requestPath:new URL(r.url()).pathname}));if(/\/groups\/[^/]+\/venue$/.test(r.url()))requests.push(r.postDataJSON());}};admin.on('request',listener);
  await admin.locator('#venue-start').click();const venue=await waitVenue(admin,v=>v?.sessionId&&!v.closed);admin.off('request',listener);
  console.log(JSON.stringify({mode,requests:requests.length,venueId:(await snapshot(admin))?.sessionId}));assert.equal(requests.length,1);assert.ok(!requests[0].password);
  const began=Date.now();await waitVenue(member,v=>v?.sessionId===venue.sessionId);
  await member.waitForFunction(()=>document.body.dataset.page==='safety');
  assert.equal(await member.locator('body').getAttribute('data-page'),'safety');await member.locator('#status-update').waitFor();
  assert.equal((await snapshot(member)).placements.length,2);assert.equal(await member.locator('#group-access').isVisible(),false);
  await audit(member);await member.screenshot({path:out+'/member-'+mode.toLowerCase()+'.png'});starts.push({mode,automaticSyncMs:Date.now()-began});
  await member.locator('#safe').click();await member.locator('#status-update').click();await member.locator('#venue-location-notice').waitFor({state:'visible'});await member.getByRole('button',{name:'Continue',exact:true}).click();
 }
 assert.deepEqual(errors,[]);await writeFile(out+'/result.json',JSON.stringify({starts,errors},null,2));console.log(JSON.stringify({out,starts,errors}));
}catch(e){if(admin){await admin.screenshot({path:out+'/failure-admin.png'});console.log(await admin.locator('body').innerText());}throw e;}finally{await browser.close();await server.close();}
