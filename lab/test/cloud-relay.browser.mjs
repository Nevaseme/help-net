// Companion to Android RelayHttpsTest: private fixture input, public PWA receiver.
// Never log the fixture, its token, or the invitation. Never click manual sync.
import assert from 'node:assert/strict';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
assert.ok(process.env.RELAY_FIXTURE,'RELAY_FIXTURE must name the private Android test fixture');
const fixture=JSON.parse(await readFile(process.env.RELAY_FIXTURE,'utf8'));
for(const key of ['endpoint','groupId','token','authorId','answerId','reportId','incidentId'])assert.ok(fixture[key],`Missing fixture field: ${key}`);
const base=fixture.endpoint.replace(/\/$/,''),out=resolve(process.env.PWA_TEST_OUTPUT||process.env.TEMP+'/pland-relay-pwa-proof');await mkdir(out,{recursive:true});
import {chromium} from '../../tools/browser-runtime.mjs';
async function api(path,body){const response=await fetch(base+'/api/v1/groups/'+fixture.groupId+path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+fixture.token,'Content-Type':'application/json','X-PlanD-Records-Version':'2'},...(body===undefined?{}:{body:JSON.stringify(body)})});assert.equal(response.status,200,'Fictional relay group API request must succeed');return response.json();}
const remote=[];let cursor=0;
for(let i=0;i<200;i++){const page=await api('/records?after='+cursor+'&limit=100');remote.push(...page.records);cursor=page.nextCursor;if(!page.hasMore)break;}
const remoteAnswers=remote.filter(r=>r.collection==='incidents'&&r.record.eventId===fixture.answerId),remoteReports=remote.filter(r=>r.collection==='responses'&&r.record.eventId===fixture.reportId);
assert.equal(remoteAnswers.length,1);assert.equal(remoteReports.length,1);
const expectedAnswer=remoteAnswers[0].record,expectedReport=remoteReports[0].record;
assert.equal(expectedAnswer.authorId,fixture.authorId);assert.equal(expectedReport.actorDeviceId,fixture.authorId);assert.equal(expectedAnswer.kind,'ANSWER');assert.ok(['SAFE','SOS'].includes(expectedAnswer.safety));assert.equal(expectedReport.change.type,'REPORT');
const invitation=await api('/invitations',{});
const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
const context=await browser.newContext({viewport:{width:400,height:860}}),page=await context.newPage(),checks=[],errors=[];
page.setDefaultTimeout(45000);page.on('pageerror',e=>errors.push(e.message));
const note=s=>{checks.push(s);console.log(s);};
async function snapshot(){return page.evaluate(async()=>{const {Store}=await import('/sync.mjs');const store=await Store.open('tasukeai-cloud');try{return {id:await store.identity(),incidents:await store.read('incidents'),responses:await store.read('responses'),residents:await store.read('residents')};}finally{store.close();}});}
try{
 await page.goto(base);await page.locator('#cloud-create').waitFor();assert.equal(await page.title(),'Tasukeai — 安否と対応の共有');
 await page.locator('#cloud-mode').click();await page.locator('#cloud-invite-input').fill(fixture.groupId+':'+invitation.code);await page.locator('#cloud-member-name').fill('架空受信者C');await page.locator('#cloud-join button').click();
 await page.waitForFunction(()=>document.querySelector('#cloud-group-state').textContent.includes('架空受信者C'));note('Independent PWA C joins the Android relay test group through the public UI');
 let rows;const deadline=Date.now()+45000;
 while(Date.now()<deadline){rows=await snapshot();if(rows.incidents.some(r=>r.eventId===fixture.answerId)&&rows.responses.some(r=>r.eventId===fixture.reportId))break;await delay(100);}
 const answers=rows.incidents.filter(r=>r.eventId===fixture.answerId),reports=rows.responses.filter(r=>r.eventId===fixture.reportId);
 assert.equal(answers.length,1,'Original A answer must arrive exactly once');assert.equal(reports.length,1,'Original A report must arrive exactly once');assert.notEqual(rows.id,fixture.authorId);assert.notEqual(rows.id,fixture.deviceId);
 assert.equal(answers[0].authorId,fixture.authorId);assert.equal(answers[0].incidentId,fixture.incidentId);assert.equal(answers[0].safety,expectedAnswer.safety);
 assert.equal(reports[0].actorDeviceId,fixture.authorId);assert.equal(reports[0].incidentId,fixture.incidentId);assert.deepEqual(reports[0].change,expectedReport.change);
 assert.equal(rows.responses.filter(r=>r.change.reportId===expectedReport.change.reportId).length,1);
 assert.equal(rows.responses.filter(r=>r.incidentId===fixture.incidentId&&r.change.type==='REPORT'&&r.change.description===expectedReport.change.description).length,1,'Relaying must not mint a replacement report');
 assert.equal(rows.incidents.filter(r=>r.kind==='ANSWER'&&r.incidentId===fixture.incidentId).length,1,'The fictional fixture contains only A’s original answer');
 assert.equal(rows.incidents.filter(r=>r.kind==='ANSWER'&&r.incidentId===fixture.incidentId&&r.authorId===rows.id).length,0);
 note('Automatic pull durably stores the original A safety and report IDs/authors exactly once, without creating a C answer');
 await page.locator('[data-tab="residents"]').click();const person=page.locator(`#list [data-person="${fixture.authorId}"]`);await person.waitFor();
 const label=expectedAnswer.safety==='SAFE'?'無事':'SOS';await page.waitForFunction(({authorId,label})=>document.querySelector(`#list [data-person="${authorId}"]`)?.textContent.includes(label),{authorId:fixture.authorId,label});await page.screenshot({path:out+'/received-safety.png'});note('Public PWA renders A’s relayed '+expectedAnswer.safety+' safety in the member list');
 await page.locator('[data-tab="response"]').click();const report=page.locator('#response-list [data-case]').filter({hasText:expectedReport.change.category});await report.waitFor();await report.click();await page.waitForFunction(description=>document.querySelector('#case-sheet').textContent.includes(description),expectedReport.change.description);await page.screenshot({path:out+'/received-report.png'});note('Public PWA opens the original A report and displays its relayed description');
 assert.deepEqual(errors,[]);await writeFile(out+'/verification.json',JSON.stringify({passed:true,base,checks,errors,safety:expectedAnswer.safety,syncButtonClicks:0,originalAuthorPreserved:true,answerCount:answers.length,reportCount:reports.length},null,2));
}catch(error){await page.screenshot({path:out+'/failure.png'}).catch(()=>{});await writeFile(out+'/verification.json',JSON.stringify({passed:false,base,checks,errors,error:String(error)},null,2));throw error;}
finally{await browser.close();}
