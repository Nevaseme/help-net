import {translateUI as t,bindStaticUiLanguage} from './web1617-english.mjs';
import {scenarioAnswers} from './venue-demo.mjs';
import {attachProfileHome} from './profile-home.mjs';
import './ui-accessibility.mjs';
import {attachCityOffline} from './city-offline.mjs';
import {wireSheetHandle} from './sheet-handle.mjs';
import {setupAppUpdates} from './app-update.mjs';
import {Store,SyncEngine,ACTIONS,currentEvents,incidentView,incidentOptions} from './sync.mjs';
import {SaveFeedback,ConnectionFeedback} from './feedback.mjs';
import {appendScopedIncident} from './incidents.mjs';
import {createPlaceUi} from './place-ui.mjs';
import {managedIncidentOptions,canonicalIncidentId,memberFor,quakeStates,requiredReporters} from './managed-records.mjs';
import {attachCloudUi} from './cloud-ui.mjs';
import {createTsunamiMap} from './tsunami-map.mjs';
import {createWeb1617Ui} from './web1617-ui.mjs';
import {activeVenue,venueResidents,venueWithHomes,venuePlaces,demoHomeFromPoint} from './venue-demo.mjs';
import {latestImpact} from './device-records.mjs';
import {placeBuildings,validHome,currentBuildingCare,BUILDING_ACTIONS} from './places.mjs';
const $=id=>document.getElementById(id), esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const labels={UNREPORTED:'未報告',SAFE:'無事',SOS:'SOS'},time=x=>x?new Date(x).toLocaleString('ja-JP'):'今回未回答';
const profile=new URL(location.href).searchParams.get('profile')==='C'?'C':'B';
let store,id,group='demo',records=[],events=[],selected=null,socket=null,engine=null,generation=0,queue=Promise.resolve(),queued=0,receipts=0,dirty=false;
let syncError='';
let incidentRows=[],incidentId='',view=null,refreshRevision=0;
let placeRows=[],roster=null;
let cloudUi=null,cloudMode=false,profileHome=null,lastRenderKey=null;
let activePage='buildings',responseFilter='all',startupNavigationChosen=false;
let designUi=null,venue=null,lastVenueSession=null;
let venueWatch=null,venueWatchSession=null,venueLocationWrite=Promise.resolve(),venueLocated=null;
function updateVenueLocation(){
 const actual=venue?.positionMode==='ACTUAL';const key=actual?venue.sessionId:null;
 if(key===venueWatchSession)return;
 if(venueWatch!==null)navigator.geolocation?.clearWatch(venueWatch);venueWatch=null;venueWatchSession=key;
 let notice=$('venue-location-note');if(!notice){notice=document.createElement('p');notice.id='venue-location-note';notice.className='muted';$('own').after(notice);}
 notice.hidden=!actual;if(!actual)return;
 const g=group,v=key;
 notice.textContent='現在地を取得しています。取得できるまでは位置不明で表示されます。';
 const failed=()=>{notice.textContent='現在地を取得できません。位置不明のまま安否を回答できます。';const retry=document.createElement('button');retry.type='button';retry.className='text-button';retry.textContent='位置情報を再取得';retry.onclick=()=>{venueWatchSession=null;updateVenueLocation();};notice.append(retry);};
 if(!navigator.geolocation){failed();return;}
 venueWatch=navigator.geolocation.watchPosition(position=>{
  venueLocationWrite=venueLocationWrite.catch(()=>{}).then(async()=>{if(group!==g||venueWatchSession!==v)return;const p=position.coords,old=(await store.positions(g)).find(r=>r.originId===id),acquiredAt=Math.round(position.timestamp);
   if(acquiredAt<(venue?.startedAt??0)||old&&acquiredAt<=old.acquiredAt)return;
   await store.savePosition({groupId:g,originId:id,revision:(old?.revision??0)+1,acquiredAt,latitude:p.latitude,longitude:p.longitude,accuracyMeters:p.accuracy});notice.hidden=true;
   if(venueLocated!==v){venueLocated=v;tsunamiMap.setHome({latitude:p.latitude,longitude:p.longitude});tsunamiMap.focusHome();}
   await refresh();cloudUi?.changed();
  }).catch(failed);
 },failed,{enableHighAccuracy:true,maximumAge:0,timeout:20000});
}
const tsunamiMap=createTsunamiMap();
const unknownIcon=document.createElement('img');unknownIcon.src='/nav-user.svg';unknownIcon.alt='';$('map-unknown').prepend(unknownIcon);
$('filter').add(new Option('無事以外','NONSAFE'));
// The approved map is the default destination; configuration is reached from the menu.
$('settings').querySelector('h2').after(document.querySelector('.incident-context'));
const trainingForm=document.createElement('form');trainingForm.id='training-form';trainingForm.hidden=true;
trainingForm.innerHTML='<h3>訓練を開始</h3><label>訓練名<input id="training-title" value="安否確認の訓練" required maxlength="80"></label><label>対象地域<input id="training-area" required maxlength="120"></label><label class="training-location-option"><input id="training-fictional" type="checkbox">京都に架空の位置を割り当てる</label><p id="training-fictional-note" class="muted" hidden>管理者を含む参加者を御所南〜烏丸御池に分散表示します。住居や現在地は変更しません。</p><button id="training-start" type="submit">訓練を開始する</button>';
document.querySelector('.incident-tools').append(trainingForm);
$('training-fictional').onchange=()=>{$('training-fictional-note').hidden=!$('training-fictional').checked;if($('training-fictional').checked&&!$('training-area').value.trim())$('training-area').value='御所南〜烏丸御池';};
document.querySelector('.incident-context').after(document.querySelector('.incident-tools'));
const trainingPage=document.createElement('div');trainingPage.id='training-page';
trainingPage.innerHTML='<h3>訓練を始める</h3><p id="training-access" class="muted"></p><button id="training-group" type="button" class="secondary">グループに参加</button>';
$('settings').append(trainingPage);trainingPage.append(trainingForm,document.querySelector('.incident-context'),document.querySelector('.incident-tools'));
document.querySelector('.incident-tools>summary').textContent='実際の地震を報告する';
trainingPage.append($('incident-end'),$('incident-feedback'));
// Training is a separate administrator action, never a checkbox on an earthquake report.
$('incident-training').checked=false;$('incident-training').closest('label').hidden=true;
const settingsBack=document.createElement('button');settingsBack.type='button';settingsBack.className='settings-back';settingsBack.textContent='メニューに戻る';$('settings').prepend(settingsBack);
settingsBack.onclick=()=>$('menu-open').click();
const propertyBack=document.createElement('button');propertyBack.type='button';propertyBack.className='settings-back';propertyBack.textContent='自分の登録情報に戻る';$('property').prepend(propertyBack);
propertyBack.onclick=()=>openSettings('profile');
$('profile-building').onclick=()=>{tab('property');$('building-registry').open=true;};
$('training-group').onclick=()=>openSettings('group');
async function afterJoining(){const own=await store.get(group,id);if(venue||own){if(own&&!venue)tsunamiMap.focusHome?.();tab(canAnswer()&&!view?.answers[id]?'safety':'buildings');}else openSettings('profile');}
function openSettings(name){tab('settings');document.body.dataset.settingsView=name;$('settings').querySelector('h2').textContent=t(({profile:'自分の登録情報',group:'グループ',incidents:'訓練・地震'})[name]||'自分の登録情報');if(name==='profile')$('profile-section').open=true;}

// Registration credentials and a resident profile are distinct records.
// Explain the next step beside the disabled controls, including after offline reopen.
for(const section of ['safety','residents','buildings']){
 const host=section==='buildings'?$('building-registry'):$(section),panel=document.createElement('div');
 panel.className='setup-guidance';panel.id=section+'-setup';panel.hidden=true;
 const text=document.createElement('p'),button=document.createElement('button');button.type='button';button.className='secondary';
 button.textContent='自分の登録情報を開く';button.onclick=()=>{openSettings('profile');$('profile-section').open=true;$('profile-section').scrollIntoView({block:'start'});$('name').focus({preventScroll:true});};
 panel.append(text,button);host.insertBefore(panel,host.querySelector('h2').nextSibling);
}
bindStaticUiLanguage(settingsBack);bindStaticUiLanguage(propertyBack);
const startGuide=document.createElement('button');startGuide.id='safety-start-guide';startGuide.type='button';startGuide.className='secondary';startGuide.textContent='災害を選ぶ／地震・訓練を報告';startGuide.hidden=true;
startGuide.textContent='訓練・地震を開く';startGuide.onclick=()=>openSettings('incidents');
$('own').after(startGuide);
const canEnd=()=>roster?memberFor(roster,id).role==='ADMIN':view?.start.authorId===id;
const canAnswer=()=>!!view&&!view.closed;
const careKey=()=>`care:${group}:${incidentId}:${selected}`;
const incidentScope=()=>({groupId:group,incidentId,authorId:id});
const saves=new SaveFeedback(renderFeedback);
const link=new ConnectionFeedback(renderFeedback,()=>closeTransport());
const placeUI=createPlaceUi({$,esc,time,store:()=>store,identity:()=>id,snapshot:()=>({group,incidentId,view,residents:records,rows:placeRows,venue}),saves,changed,visibleResidents:()=>designUi?.buildingResidents()||[],selectResident:person=>{selected=person;tab('residents');renderDetail();renderFeedback();}});
function renderFeedback(){
 const s=link.state,own=records.some(r=>r.originId===id),busy=saves.busy;
 for(const key of ['profile','safety','care','incident']){const f=saves.get(key==='care'?careKey():key);const el=$(key+'-feedback');el.textContent=f.text;el.dataset.status=f.status;el.setAttribute('aria-busy',String(f.status==='pending'));}
 for(const form of ['profile','care-form'])for(const el of $(form).querySelectorAll('input,textarea,select,button'))el.disabled=busy;
 $('safe').disabled=$('sos').disabled=busy||!own||!canAnswer();
 $('incident-select').disabled=busy;$('incident-open').disabled=busy||!own;
 for(const el of $('incident-form').querySelectorAll('input,button'))el.disabled=busy||!own;
 $('incident-end').disabled=busy||!canAnswer()||!canEnd();
 $('incident-end').hidden=!canAnswer()||!canEnd();
 const admin=!!roster&&memberFor(roster,id)?.role==='ADMIN';
 trainingForm.hidden=!admin;
 const running=[...$('incident-select').options].some(o=>o.value&&o.textContent.startsWith('訓練：')&&!o.textContent.includes('（終了）'));
 $('training-access').textContent=!roster?'訓練を始めるには、グループに参加してください。':!admin?'訓練はグループの管理者が開始します。開始されると安否を回答できます。':running?'訓練を実施中です。安否画面から回答できます。':'';
 $('training-group').hidden=!!roster;
 for(const el of trainingForm.querySelectorAll('input,button'))el.disabled=busy||running;
 $('training-start').textContent=running?'訓練を実施中':'訓練を開始する';
 $('care-save').disabled=busy||!canAnswer();
 $('profile-save').textContent=saves.get('profile').status==='pending'?'保存しています…':'登録情報を保存';
 $('care-save').textContent=saves.get(careKey()).status==='pending'?'保存しています…':'対応記録を保存';
 if(!cloudMode){$('network').textContent=s.text;$('sharing').textContent=s.text;}
 $('connection-state').textContent=s.text;
 $('connection-error').textContent=s.error||'';
 $('sync-error').textContent=syncError;
 $('connect-start').disabled=link.active||busy||!own;
 $('connect-start').textContent=s.status==='connecting'?'接続しています…':link.active?'接続済み':'実験通信を開始';
 if(!cloudMode)for(const el of document.querySelectorAll('[data-stop]'))el.disabled=!link.active;
 $('token').disabled=$('role').disabled=link.active||busy;
 $('route').textContent=s.pendingPhase?`${s.pendingPhase==='AB'?'A ↔ B':'B ↔ C'}へ切替中…（確認済み：${s.phase}）`:s.phase?`${link.active?'現在の経路':'最後に確認した経路'}：${s.phase==='AB'?'A ↔ B':'B ↔ C'}`:'経路は接続後に確認します';
 for(const phase of ['AB','BC']){const b=$('phase-'+phase.toLowerCase());b.disabled=$('role').value!=='B'||!['waiting','connected'].includes(s.status)||!!s.pendingPhase||s.phase===phase;b.setAttribute('aria-pressed',String(s.phase===phase));}
 $('route-help').textContent=$('role').value==='B'?'B ↔ Cへ切り替える前に、Android Aの実験通信を停止してください。':'経路の切替はiPhoneなど役割Bの端末から行います。';
 for(const el of $('list').querySelectorAll('button'))el.disabled=busy;
 placeUI.feedback();
}
const message=text=>{$('message').textContent=text;};
function task(fn){return Promise.resolve().then(fn).catch(e=>message(e.message||'操作できませんでした'));}
function tab(name){if(name==='settings'){document.body.dataset.settingsView='profile';$('settings').querySelector('h2').textContent=t('自分の登録情報');}designUi?.closeSheets();tsunamiMap.cancelPick?.();activePage=name;document.body.dataset.page=name;for(const x of ['safety','residents','buildings','response','settings','property'])$(x).hidden=x!==name;document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tab===name)));$('app-menu').close();$('menu-open').setAttribute('aria-expanded','false');window.scrollTo({top:0,behavior:'instant'});if(name==='buildings')tsunamiMap.show();if(name==='response')renderResponse();}
document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>{startupNavigationChosen=true;tab(b.dataset.tab);});
wireSheetHandle($('app-menu'));
$('menu-open').onclick=()=>{if($('app-menu').open){$('app-menu').close();return;}designUi?.closeSheets();$('app-menu').show();$('menu-close').focus({preventScroll:true});$('menu-open').setAttribute('aria-expanded','true');};
$('menu-close').onclick=()=>$('app-menu').close();
$('app-menu').addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();$('app-menu').close();}});
$('app-menu').addEventListener('close',()=>$('menu-open').setAttribute('aria-expanded','false'));
$('app-menu').addEventListener('click',e=>{if(e.target===$('app-menu')){const r=e.target.getBoundingClientRect();if(e.clientY<r.top||e.clientX<r.left||e.clientX>r.right)$('app-menu').close();}});
for(const button of document.querySelectorAll('[data-menu]'))button.onclick=()=>openSettings(button.dataset.menu);
$('map-unknown').onclick=()=>{$('filter').value=$('map-show-safe').checked?'ALL':'NONSAFE';tab('residents');task(refresh);};
$('map-show-safe').onchange=()=>{$('map-unknown-count').textContent=`${records.filter(r=>$('map-show-safe').checked||r.safety!=='SAFE').length}人`;};
$('response-residents').onclick=()=>tab('residents');$('response-buildings').onclick=()=>{tab('property');$('building-registry').open=true;};
for(const button of document.querySelectorAll('[data-response-filter]'))button.onclick=()=>{responseFilter=button.dataset.responseFilter;renderResponse();};
function renderResponse(){
 if(designUi){designUi.renderResponse();return;}
 const buildingNames=new Map(placeBuildings(placeRows).map(b=>[b.buildingId,b.label]));
 const rows=[...currentEvents(events).map(e=>({...e,targetName:records.find(r=>r.originId===e.subjectId)?.name||'人物',actionLabel:ACTIONS[e.action]})),...currentBuildingCare(placeRows.filter(e=>e.kind==='CARE'&&e.incidentId===incidentId)).map(e=>({...e,targetName:buildingNames.get(e.buildingId)||'建物',actionLabel:BUILDING_ACTIONS[e.action]}))].filter(e=>responseFilter!=='mine'||e.authorId===id).sort((a,b)=>b.createdAt-a.createdAt);
 $('response-context').textContent=view?`${view.start.training?'訓練':'地震'}：${view.start.title}`:'災害・訓練を選択すると、その対応記録を表示します。';
 document.querySelectorAll('[data-response-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.responseFilter===responseFilter)));
 $('response-list').innerHTML=rows.length?rows.map(e=>`<button class="resident" data-response-person="${esc(e.subjectId||'')}" data-response-building="${esc(e.buildingId||'')}"><strong>${esc(e.targetName)}</strong><span class="building-status">${esc(e.actionLabel)}</span><small>${esc(e.note||e.scope||'')}</small><small>${esc(e.authorName)} · ${esc(time(e.createdAt))}</small></button>`).join(''):'<p class="empty-state">対応の記録はありません。</p>';
 for(const b of $('response-list').querySelectorAll('button'))b.onclick=()=>{if(b.dataset.responsePerson){selected=b.dataset.responsePerson;tab('residents');renderDetail();$('detail').scrollIntoView({block:'start'});}else{tab('property');$('building-registry').open=true;const target=[...$('building-list').querySelectorAll('button')].find(x=>x.dataset.building===b.dataset.responseBuilding);target?.click();}};
}
function badge(r){return `<span class="badge ${r.safety}">${labels[r.safety]}</span>`;}
function statusFor(subject){const active=currentEvents(events.filter(e=>e.subjectId===subject));return active.length?active.map(e=>ACTIONS[e.action]).join(' / '):'対応記録なし';}
async function refresh({background=false}={}){
 const epoch=++refreshRevision,g=group;const [raw,legacy,journal,savedSelection,places,participants,quakes,venueData]=await Promise.all([store.all(g),store.events(g),store.incidents(g),store.resolveIncidentSelection(g),store.places(g),store.roster(g),store.quakeEvents(g),store.read('meta','venue:'+g)]);if(epoch!==refreshRevision||g!==group)return;
 const extras=await Promise.all([store.positions(g),store.memos(g),store.responseEvents(g),store.deviceRecords(g)]);if(epoch!==refreshRevision||g!==group)return;
 const renderKey=JSON.stringify([g,raw,legacy,journal,savedSelection,places,participants,quakes,venueData,extras]);if(background&&lastRenderKey===renderKey)return;lastRenderKey=renderKey;
 placeRows=places.map(e=>({...e,incidentId:canonicalIncidentId(participants,quakes,e.incidentId)??null}));roster=participants;const selection=canonicalIncidentId(roster,quakes,savedSelection);
 incidentRows=journal;const options=managedIncidentOptions(journal,roster,quakes),active=options.filter(v=>!v.closed);
 const next=selection==='legacy'||options.some(v=>v.start.eventId===selection)?selection:'';
 if(next!==incidentId){incidentId=next;selected=null;$('note').value='';$('action').value='CHECKING';$('supersedes').value='';saves.clearOtherResults();}
 view=options.find(v=>v.start.eventId===incidentId)||null;
 venue=venueWithHomes(activeVenue(venueData,g,incidentId),extras[3]);const synthetic=venuePlaces(venue);if(synthetic.length){const ids=new Set(synthetic.map(r=>r.eventId));placeRows=[...placeRows.filter(r=>(venue.positionMode==='ACTUAL'||r.kind!=='HOME')&&!ids.has(r.eventId)),...synthetic];}
 if(venue&&view){const answers={...view.answers};for(const a of scenarioAnswers(venue))if(!answers[a.authorId])answers[a.authorId]=a;view={...view,answers};}
 const displayRows=venue?venueResidents(venue):raw;
 records=incidentId==='legacy'?raw:displayRows.map(r=>({...r,safety:view?.answers[r.originId]?.safety||'UNREPORTED',updatedAt:view?.answers[r.originId]?.createdAt||0,impactAt:r.fictional?r.impactAt:latestImpact(extras[3],g,r.originId)}));
 events=incidentId==='legacy'?legacy:view?.care||[];
 $('incident-select').innerHTML='<option value="">災害を選択してください</option>'+options.map(v=>`<option value="${v.start.eventId}">${esc(v.start.training?'訓練':'地震')}：${esc(v.start.title)}・${esc(v.start.area)}${v.closed?'（終了）':''} [${v.start.eventId.slice(0,8)}]</option>`).join('')+'<option value="legacy">過去の情報（災害未分類・閲覧のみ）</option>';$('incident-select').value=incidentId;
 $('incident-state').textContent=view?`${view.start.training?'訓練':'地震'}・${view.closed?'終了・閲覧のみ':'安否確認中'}：${view.start.title}／${view.start.area}。開始：${time(view.start.createdAt)}、${view.start.authorName}${!view.closed&&!canEnd()?(roster?'。終了は管理者が行います。':'。終了できるのは開始した人だけです。'):''}`:incidentId==='legacy'?'以前の安否・対応記録です。今回の地震への回答ではありません。':active.length>1?'複数の安否確認があります。対象を選択してください。':'安否確認は始まっていません。「地震発生」から開始できます。';
 const own=records.find(r=>r.originId===id);
 if(roster){const needed=requiredReporters(roster.members.length);$('incident-form').querySelector('p').textContent=`異なるメンバー${needed}人の地震報告がそろうと安否確認が始まります。以前の安否は引き継ぎません。管理者は別の「訓練を開始する」操作で訓練を開始できます。`;$('incident-form').querySelector('button').textContent='地震発生を報告する';if(!view&&incidentId!=='legacy'&&active.length===0)$('incident-state').textContent=`安否確認はまだ始まっていません。異なるメンバー${needed}人の地震報告が必要です。`;}
 $('map-unknown-count').textContent=`${records.filter(r=>$('map-show-safe').checked||r.safety!=='SAFE').length}人`;
 $('header-date').textContent=view?time(view.start.createdAt):'安否と対応の共有';
 document.querySelector('header h1').textContent=view?(view.start.training?'訓練':'地震'):'';
 if(!cloudMode)$('header-group').textContent=roster?.groupName||group;
 const mapHome=venue?.positionMode==='ACTUAL'?extras[0].find(p=>p.originId===id&&p.acquiredAt>=(venue.startedAt??0)):own;
 tsunamiMap.setHome?.(mapHome&&Number.isFinite(mapHome.latitude)&&Number.isFinite(mapHome.longitude)?{latitude:mapHome.latitude,longitude:mapHome.longitude}:null);
 if(venue&&lastVenueSession!==venue.sessionId){lastVenueSession=venue.sessionId;$('map-show-safe').checked=true;tsunamiMap.focusVenue?.(venue.positionMode==='ACTUAL');if(!view?.answers[id])tab('safety');}
 updateVenueLocation();
 if(activePage==='response')renderResponse();
 for(const section of ['safety','residents','buildings']){const panel=$(section+'-setup');panel.hidden=!!own;panel.querySelector('p').textContent=section==='residents'?'グループへの参加は保存済みです。この一覧には「自分の登録情報」を登録した人が表示されます。':'安否回答と建物登録には、活動名に加えて「自分の登録情報」の登録が必要です。';}
 startGuide.hidden=!own||canAnswer();
 let startNote=$('safety-start-note');if(!startNote){startNote=document.createElement('p');startNote.id='safety-start-note';startNote.className='muted';startGuide.after(startNote);}startNote.hidden=startGuide.hidden;startNote.textContent=$('incident-state').textContent;
 document.querySelector('.incident-context > summary').textContent=view?`${view.start.training?'訓練':'地震'}：${view.start.title}${view.closed?'（終了）':''}`:'災害・訓練を選ぶ';
 $('own').innerHTML=own?`<strong>${esc(own.name)}</strong> ${badge(own)}<p>${own.updatedAt?'安否更新 '+esc(time(own.updatedAt)):view?'今回の回答はまだありません':'安否確認は始まっていません'}</p>`:'安否を記録するには「設定」で自分の登録情報を開くしてください。';
 for(const [key,value] of [['safe','SAFE'],['sos','SOS']])$(key).setAttribute('aria-pressed',String(own?.safety===value));
 const savedOwn=venue?.positionMode!=='ACTUAL'&&venue&&own?{...own,latitude:own.homeLatitude??own.latitude,longitude:own.homeLongitude??own.longitude}:raw.find(r=>r.originId===id);if(savedOwn&&!dirty){$('name').value=savedOwn.name;$('lat').value=savedOwn.latitude;$('lon').value=savedOwn.longitude;$('floor').value=savedOwn.floor;$('room').value=savedOwn.room;profileHome?.setHome(venue?.placements.find(p=>p.deviceId===id)?.home??placeBuildings(placeRows).find(b=>b.buildingId===validHome(placeRows,id)?.buildingId)?.locationReference??null);}
 profileHome?.render();
 $('count').textContent=`${records.length}人`;
 const shown=records.filter(r=>$('filter').value==='ALL'||($('filter').value==='NONSAFE'?r.safety!=='SAFE':r.safety===$('filter').value)).sort((a,b)=>(a.safety==='SOS'?-1:0)-(b.safety==='SOS'?-1:0)||a.name.localeCompare(b.name,'ja'));
 $('list').innerHTML=shown.length?shown.map(r=>`<button class="resident" data-person="${esc(r.originId)}"><strong>${esc(r.name)}${r.originId===id?'（自分）':''}</strong> ${badge(r)}<small>登録住居 · ${esc(r.floor||'階数未登録')}</small><small>${esc(statusFor(r.originId))}</small><small>安否更新 ${esc(time(r.updatedAt))}</small></button>`).join(''):'<p>該当する住民情報がありません。</p>';
 for(const button of $('list').querySelectorAll('[data-person]'))button.onclick=()=>{selected=button.dataset.person;renderDetail();renderFeedback();$('detail').scrollIntoView({block:'start'});};
 renderDetail();placeUI.render();renderFeedback();if(designUi)await designUi.render();
}
function renderDetail(){if(designUi)return designUi.renderPerson(selected);const r=records.find(r=>r.originId===selected);$('detail').hidden=!r;if(!r)return;
 $('detail-name').textContent=r.name;$('detail-info').textContent=`安否：${labels[r.safety]}\n登録住居：${r.latitude}, ${r.longitude}\n${placeUI.homeLabel(placeRows,r.originId)}\n階数：${r.floor||'未登録'}\n寝る部屋：${r.room||'未登録'}\n安否更新：${time(r.updatedAt)}`;
 const history=events.filter(e=>e.subjectId===selected).sort((a,b)=>b.createdAt-a.createdAt||a.eventId.localeCompare(b.eventId)),heads=currentEvents(history),active=new Set(heads.map(e=>e.eventId));
 $('history').innerHTML=history.length?history.map(e=>`<div class="event"><strong>${esc(ACTIONS[e.action])}${active.has(e.eventId)?'':'（訂正前）'}</strong><p>${esc(e.note||'詳細なし')}</p><small>${esc(e.authorName)} · ${esc(time(e.createdAt))}${e.supersedes?' · 訂正記録':''}<br>記録ID ${esc(e.eventId)}</small></div>`).join(''):'<p>まだ対応記録はありません。</p>';
 if(saves.busy)return;
 const old=$('supersedes').value;$('supersedes').innerHTML='<option value="">新しい記録</option>'+heads.filter(e=>e.authorId===id).map(e=>`<option value="${e.eventId}">${esc(time(e.createdAt))} ${esc(ACTIONS[e.action])} を訂正</option>`).join('');
 if([...$('supersedes').options].some(o=>o.value===old))$('supersedes').value=old;
 $('care-form').hidden=!records.some(x=>x.originId===id)||!canAnswer();
}
function closeTransport(){
 generation++;const old=socket;socket=null;engine=null;queue=Promise.resolve();queued=0;old?.close();
}
function stop(reason='共有停止中'){closeTransport();link.stop(reason);}
function connect(){
 if(link.active||saves.busy)return;
 if(!records.some(r=>r.originId===id))throw Error('先に自分の情報を登録してください');
 closeTransport();syncError='';const gen=generation,epoch=link.start();receipts=0;$('receipts').textContent='今回の接続中に相手が保存を確認：0件';
 let ws;try{ws=new WebSocket(`${location.origin.replace('https:','wss:')}/socket`);}catch{link.fail(epoch,'接続を開始できません。設定を確認して再試行してください。');return;}socket=ws;
 const token=$('token').value.trim();sessionStorage.setItem('pland-token',token);
 const sync=new SyncEngine(group,id,store,(to,body)=>{if(gen!==generation||ws.readyState!==WebSocket.OPEN)throw Error('接続が終了しました');ws.send(JSON.stringify({kind:'data',to,body}));},error=>{if(gen===generation){syncError=`通信エラー：${error}。保存済み情報は残ります。通信を停止して再接続してください。`;renderFeedback();}},()=>{if(gen===generation)$('receipts').textContent=`今回の接続中に相手が保存を確認：${++receipts}件`;});engine=sync;
 ws.onopen=()=>{if(gen===generation)ws.send(JSON.stringify({kind:'join',role:$('role').value,group,token}));};
 ws.onmessage=e=>{if(gen!==generation)return;if(++queued>256){stop('受信が混み合ったため停止');message('再接続してください');return;}
  queue=queue.then(async()=>{if(gen!==generation)return;const m=JSON.parse(e.data);
   if(m.kind==='peer-up')await sync.connected(m.peer);
   if(m.kind==='peer-down')sync.disconnected(m.peer);
   if(m.kind==='data')await sync.receive(m.from,m.body);
   if(m.kind==='error')link.rejectPhase(epoch,m.message);
   if(m.kind==='joined')link.joined(epoch,m.phase);
   if(m.kind==='phase')link.confirmPhase(epoch,m.phase);
   if(gen===generation){link.peers(epoch,sync.peers.size);await refresh();}
  }).catch(e=>{if(gen===generation){syncError=e.message;renderFeedback();}}).finally(()=>{if(gen===generation)queued--;});
 };
 ws.onclose=()=>{if(gen===generation){closeTransport();link.fail(epoch,'接続が終了しました。接続コード・役割・サーバーを確認して再接続してください。');}};
 ws.onerror=()=>{if(gen===generation){closeTransport();link.fail(epoch,'接続できません。証明書、同じWi-Fi、PCの実験サーバーを確認して再試行してください。');}};
}
async function changed(){await refresh();if(engine)await engine.changed();if(cloudUi)await cloudUi.changed();}
$('filter').onchange=()=>task(refresh);
$('profile').oninput=()=>{dirty=true;};
$('profile').onsubmit=e=>{e.preventDefault();if(saves.busy)return;task(async()=>{
 await saves.run('profile',async()=>{
 if(!$('lat').value||!$('lon').value)throw Error('住居の位置を地図から選んでください。');
 const nextGroup=$('group').value.trim();const previous=await store.get(nextGroup,id);
 const candidate={groupId:nextGroup,originId:id,revision:1,updatedAt:0,name:$('name').value.trim(),latitude:Number($('lat').value),longitude:Number($('lon').value),floor:$('floor').value.trim(),room:$('room').value.trim(),safety:previous?.safety||'UNREPORTED'};
 if(venue&&venue.positionMode!=='ACTUAL'&&venue.groupId===nextGroup){const old=venue.placements.find(p=>p.deviceId===id)?.home;const home=demoHomeFromPoint({latitude:candidate.latitude,longitude:candidate.longitude,...profileHome.metadata()},old,{address:profileHome.address(),floor:candidate.floor,room:candidate.room,displayName:candidate.name});await store.appendDeviceRecord({groupId:nextGroup,authorId:id,kind:'DEMO_HOME',sessionId:venue.sessionId,home});}else await store.saveProfileWithHome(candidate,{address:profileHome.address(),...profileHome.metadata()});if(group!==nextGroup){stop('グループを変更したため停止');saves.clearOtherResults();}group=nextGroup;selected=null;dirty=false;
 },changed,'登録情報');await refresh();if(saves.get('profile').status==='success'){tsunamiMap.focusHome?.();if(canAnswer())tab('safety');}
 });};
for(const [button,safety] of [['safe','SAFE'],['sos','SOS']])$(button).onclick=()=>{const scope=incidentScope();return task(async()=>{await saves.run('safety',()=>appendScopedIncident(store,scope,'ANSWER',{safety}),changed,labels[safety]);await refresh();});};
$('care-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;const scope=incidentScope(),key=careKey(),subject=selected,action=$('action').value,note=$('note').value.trim(),supersedes=$('supersedes').value||null;task(async()=>{await saves.run(key,async()=>{if(!subject||!records.some(r=>r.originId===subject))throw Error('対象住民の登録を確認してください');
 await appendScopedIncident(store,scope,'CARE',{subjectId:subject,action,note,supersedes});$('note').value='';$('supersedes').value='';
 },changed,'対応記録');await refresh();});};
$('incident-select').onchange=()=>task(async()=>{if(saves.busy)return;await store.update('meta','incident:'+group,()=>$('incident-select').value);await refresh();});
$('incident-open').onclick=()=>{$('incident-form').hidden=!$('incident-form').hidden;};
trainingForm.onsubmit=e=>{e.preventDefault();if(saves.busy)return;task(async()=>{let eventId;await saves.run('incident',async()=>{const r=await store.roster(group);if(!r||memberFor(r,id)?.role!=='ADMIN')throw Error('訓練を開始できるのは管理者だけです');if($('training-fictional').checked){if(!cloudUi)throw Error('架空の位置を共有するにはインターネット接続が必要です');eventId=await cloudUi.startTraining($('training-title').value.trim(),$('training-area').value.trim());}else{eventId=crypto.randomUUID();await store.commandQuake({groupId:group,eventId,actorDeviceId:id,operatedAt:Date.now(),action:'TRAINING_START',training:true,title:$('training-title').value.trim(),area:$('training-area').value.trim()});}},async()=>{await store.update('meta','incident:'+group,()=>eventId);await changed();},'訓練の開始');await refresh();tab('safety');});};
$('incident-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;task(async()=>{let created;await saves.run('incident',async()=>{
 const own=await store.get(group,id);if(!own)throw Error('先に登録してください');const eventId=crypto.randomUUID();
 created={groupId:group,eventId,incidentId:eventId,authorId:id,authorName:own.name,createdAt:Date.now(),sequence:1,kind:'START',title:$('incident-title').value.trim(),area:$('incident-area').value.trim(),training:$('incident-training').checked};if(await store.roster(group)){await store.commandQuake({groupId:group,eventId,actorDeviceId:id,operatedAt:created.createdAt,action:'REPORT',training:created.training,title:created.title,area:created.area});const states=quakeStates(await store.roster(group),await store.quakeEvents(group));created.eventId=states.find(s=>!s.closed&&s.formation.training===created.training)?.formation.incidentId||'';}else await store.mergeIncident(created);
 },async()=>{await store.update('meta','incident:'+group,()=>created.eventId);$('incident-form').hidden=true;await changed();},'地震発生・安否確認の開始');await refresh();});};
$('incident-end').onclick=()=>{const scope=incidentScope();return task(async()=>{await saves.run('incident',async()=>{const r=await store.roster(scope.groupId);if(r){const state=quakeStates(r,await store.quakeEvents(scope.groupId)).find(s=>s.incidentIds.includes(scope.incidentId));if(!state)throw Error('開始中の災害を選んでください');await store.commandQuake({groupId:scope.groupId,eventId:crypto.randomUUID(),actorDeviceId:scope.authorId,operatedAt:Date.now(),action:'END',training:state.formation.training,targetId:scope.incidentId});}else await appendScopedIncident(store,scope,'END');},changed,'安否確認の終了');await refresh();});};
$('connect').onsubmit=e=>{e.preventDefault();task(connect);};for(const b of document.querySelectorAll('[data-stop]'))b.onclick=()=>stop();$('role').onchange=renderFeedback;
for(const phase of ['AB','BC'])$('phase-'+phase.toLowerCase()).onclick=()=>task(()=>{if(!link.requestPhase($('role').value,phase))return;try{socket.send(JSON.stringify({kind:'phase',phase}));}catch{stop('接続が終了しました。再接続して経路を確認してください。');}});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&socket)stop('画面を閉じたため停止');});
window.addEventListener('pagehide',()=>stop());
bindStaticUiLanguage(document.querySelector('.app-notes'));
designUi=createWeb1617Ui({map:tsunamiMap,selectBuilding:value=>{tab('property');$('building-registry').open=true;[...$('building-list').querySelectorAll('[data-building]')].find(b=>b.dataset.building===value)?.click();},snapshot:()=>({records,events,view,id,roster,group,incidentId,placeRows,venue}),getStore:()=>store,changed,tab,selectPerson:value=>{selected=value;}});
await task(async()=>{
 cloudMode=!!document.querySelector('meta[name="tasukeai-cloud"]');
 try{cloudMode||=localStorage.getItem('tasukeai-cloud-origin')==='1';}catch{}
 if(!cloudMode){const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),6000);try{const config=await fetch('/api/v1/info',{cache:'no-store',signal:controller.signal});cloudMode=config.ok&&(await config.json()).service==='tasukeai-sync';}catch{}finally{clearTimeout(timeout);}}
 if(cloudMode)try{localStorage.setItem('tasukeai-cloud-origin','1');}catch{}
 store=await Store.open(cloudMode?'tasukeai-cloud':'pland-lab-'+profile);id=await store.identity();group=(await store.read('meta','group'))||'demo';$('group').value=group;$('role').value=profile;
 $('token').value=location.hash.slice(1)||sessionStorage.getItem('pland-token')||'';if(location.hash)history.replaceState(null,'',location.pathname+location.search);
 profileHome=attachProfileHome({store,getGroup:()=>group,onChange:()=>{dirty=true;}});
 await refresh();$('profile-section').open=!records.some(r=>r.originId===id);
 if(cloudMode)cloudUi=await attachCloudUi({store,id,getGroup:()=>group,setGroup:async value=>{stop();group=value;selected=null;dirty=false;await store.update('meta','group',()=>group);},refresh,tab,onJoined:afterJoining});
 attachCityOffline({store,getGroup:()=>group,host:document.querySelector('.appearance-settings').parentElement});
 if($('cloud-map-preview'))$('cloud-map-preview').onclick=()=>tab('buildings');$('tsunami-back').onclick=()=>tab('settings');
 if($('cloud-map-preview'))$('cloud-map-preview').textContent='地図を見る';
 document.querySelector('footer').textContent='Tasukeai · PWA 1.6.34 · この端末に記録を保存';
 await refresh();
 if(document.body.classList.contains('registration-needed'))openSettings('group');else if(!startupNavigationChosen)await afterJoining();
 if('serviceWorker'in navigator){await setupAppUpdates();$('offline').textContent=cloudMode?'オフライン起動の準備ができました。接続できるときに記録を共有します。':'オフライン起動の準備ができました。通信には同じWi-Fiと実験サーバーが必要です。';}
});



