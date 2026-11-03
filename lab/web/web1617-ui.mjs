import {linkedBuildingResidents,registeredBuildingForPerson,buildingAtRegisteredPlace} from './building-details.mjs';
import {showCommunityScreen} from './community-screen.mjs';
import {demoCommunityFor,communityMapOverview} from './community-overview.mjs';
import {setHtml} from './dom-state.mjs';
import {wireSheetHandle} from './sheet-handle.mjs';
import {venuePositions} from './venue-demo.mjs';
import {placeBuildings,currentBuildingCare,BUILDING_ACTIONS} from './places.mjs';
import {englishUI} from './web1617-english.mjs';
import {memberFor, responseKey} from './managed-records.mjs';
import {currentEvents} from './sync.mjs';
import {appendScopedIncident} from './incidents.mjs';
import {bindFormDraft} from './form-drafts.mjs';

const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const words = {...englishUI,'衝撃を検知':'Impact detected','ROAD_BLOCKED':'Blocked road','WATER_OUTAGE':'Water outage','BUILDING_DAMAGE':'Building damage','自分の登録情報':'My information','名前・住居・建物と住戸':'Name, home and dwelling','訓練・地震':'Drills and earthquakes','名前と住居':'Name and home','住んでいる建物・住戸を選ぶ':'Choose your building and dwelling','表示モード':'Appearance','端末に合わせる':'Use device setting','ライト':'Light','ダーク':'Dark','地震':'Earthquake','詳細':'Details','訓練の詳細':'Training details','地震の詳細':'Earthquake details','開始':'Started','経過時間':'Elapsed time','インターネット未接続':'No internet connection','登録情報':'Your information','災害への備え':'Preparedness','アプリ設定':'App settings','AVAILABILITY':'Availability','REPORT':'Incident report','PROGRESS':'Progress','PRIORITY':'Priority','ASSIGNMENT':'Assignment','MATERIAL':'Materials',
 '津波の浸水範囲':'Tsunami inundation','津波高':'Coastal wave height','表示する':'Show result','熱海を見る':'View Atami','浸水深を表示':'Show inundation depth','計算条件と出典':'Conditions and sources','最大浸水深の凡例':'Maximum depth legend','読み込みを中止':'Cancel loading','登録に戻る':'Back to registration','共有停止中':'Sharing stopped','安否と対応の共有':'Safety and response sharing','同時に更新された記録があります。保存する対応状況を選んでください。':'There are concurrent updates. Choose the progress state to save.','訂正する記録':'Record to edit','記録を選んでください':'Select a record','同時に更新された記録があります。選んだ記録だけ訂正します。':'There are concurrent updates. Only the selected record will be edited.','管理者':'Administrator','班長':'Team leader','メンバー':'Member','共有停止中':'Sharing stopped','確認中':'Checking','要救助':'Needs help','撤回':'Revoked','自分の情報・住居':'My profile and home','実験グループ':'Group ID','表示名':'Display name','登録住居の緯度':'Home latitude','登録住居の経度':'Home longitude','登録情報を保存':'Save profile','共有状況':'Sharing status','このアプリについて':'About this app','情報の精度':'Position accuracy','寝室':'Sleeping area','位置更新から':'Location updated','分前':'minutes ago','秒前':'seconds ago','訓練':'Drill','地震発生':'Earthquake','地図':'Map','一覧':'People','対応':'Tasks','安否':'Safety','設定':'Settings','メニュー':'Menu','閉じる':'Close',
 'メンバー一覧':'Resident list','個別に確認できる人':'People in this group','全員':'All','応答なし':'No response','無事・救助済み':'Safe / Rescued',
 '無事':'Safe','救助済み':'Rescued','無事の人':'Safe person','救助済みの人':'Rescued person','位置不明':'Location unknown',
 '登録住居':'Registered home','寝る部屋・場所':'Sleeping area','階数':'Floor','未登録':'Not registered','今回未回答':'No response to this incident',
 '対応の記録':'Response history','まだ記録はありません。':'No records yet.','このグループの安否':'Safety status in this group',
 '自分の活動':'My availability','活動可能':'Available','対応中':'In progress','活動不可':'Unavailable','未設定':'Not set','活動状況':'Availability',
 '担当者無し':'Unassigned','自分の担当':'Assigned to me','すべて':'All','通報を追加':'Report an incident','CSVを書き出す':'Export CSV',
 '地域集計':'Area summary','未対応':'Not started','対応済み':'Resolved','緊急度':'Priority','未評価':'Not assessed','低':'Low','中':'Medium','高':'High',
 '担当':'Assigned to','対応状況':'Progress','あなたはこの案件の担当ではありません':'You are not assigned to this case',
 '担当を変更':'Change assignment','対応状況を変更':'Change progress','対応を始める':'Start response','担当を解除':'Unassign myself',
 '必要資材':'Materials','資材を要請する':'Request materials','必要':'Needed','手配済み':'Arranged','不足':'Shortfall','資材名':'Material name','単位':'Unit',
 '通報内容':'Incident report','種別':'Category','道路閉塞':'Blocked road','建物倒壊':'Building collapse','その他':'Other','場所':'Location',
 '緯度':'Latitude','経度':'Longitude','内容':'Description','保存する':'Save','保存しています…':'Saving…','保存しました':'Saved',
 '変更理由':'Reason for change','対応結果':'Response result','対応結果を入力してください':'Enter the response result','通報を訂正':'Edit report',
 '自分の安否':'My status','無事を記録':'I’m safe','SOSを記録':'I need help','言語':'Language','日本語':'日本語','English':'English',
 'グループ':'Group','自分の住居・寝室':'Home and sleeping area','建物・住戸':'Buildings and units','災害・訓練':'Incidents and drills',
 '情報を登録してください。':'Register your profile first.','対象の記録はありません。':'No matching records.',
 '災害・訓練を選択してください。':'Select an incident or drill.','参加者設定が必要です。':'Join a group to use response records.',
 '位置情報は住居の登録情報です。現在地ではありません。':'This is a registered home location, not a current phone location.',
 '応答がない人の詳細は、安否確認の開始から3分後に表示します。':'Details of people who have not responded appear three minutes after the incident starts.',
 '地域はまだ設定されていません。':'Area membership has not been registered.',
 'このグループ':'This group','未分類の案件':'Cases without an area','案件数':'Cases','緊急度が高い未完了案件':'Open high-priority cases','不足資材':'Material shortfalls',
 '本人の安否とは別の対応記録です。':'Response records are separate from the person’s safety status.',
 '通信状況は設定から確認できます。':'Connection status is available in Settings.',
 '自分':'Me','日時不明':'Time unknown','活動名':'Activity name','記録者':'Recorded by','訂正前':'Superseded',
 '対応予定':'Acknowledged','SOSメモ':'Help request note','メモだけ保存する':'Save note only','安否を更新する':'Update my status','最後の端末位置':'Last phone location','位置更新':'Location updated','取り消した通報':'Canceled reports','通報を取り消す':'Cancel report','通報を復元':'Restore report','取消理由':'Reason for cancellation','地域を変更':'Change report area',
 '地域はまだ設定されていません。':'Areas have not been configured yet.',
 '通報の取消・復元は現在の共有形式に未対応です。':'Canceling and restoring reports is not supported by the current shared record format.'
};

/** No current phone position exists in the resident schema. Never use home coordinates as live location. */
export function personPresentation(person, events, view, ownId, now=Date.now(), position=null) {
 const rescued=currentEvents(events.filter(e=>e.subjectId===person.originId)).some(e=>e.action==='RESCUED' && e.createdAt >= (person.updatedAt||0));
 const status=rescued?'RESCUED':person.safety;
 if(person.venueSessionId&&person.venueSessionId===view?.start.eventId)return {status,disclosed:true,unknownLocation:!position,name:person.name,showHome:!!person.homeAddress,showObservations:!['SAFE','RESCUED'].includes(status)&&!!(person.impactAt||person.enclosedAt)};
 const disclosed=person.originId===ownId || status!=='UNREPORTED' || (!!view && now-view.start.createdAt>=180000);
 return {status,disclosed,unknownLocation:!position,name:!disclosed?'応答なし':status==='SAFE'?'無事の人':status==='RESCUED'?'救助済みの人':person.name,
  showHome:disclosed&&(!position||status==='UNREPORTED'),showObservations:disclosed&&!['SAFE','RESCUED'].includes(status)&&!!(person.impactAt||person.enclosedAt)};
}

export const reportEditSources = row => row.reports.some(e=>!e.change.cancelled)?row.reports.filter(e=>!e.change.cancelled):row.reports;
export const isResolvedCase = row => row.progress.length>0 && row.progress.every(e=>e.change.status==='DONE');
export function responseCases(snapshot, heads) {
 const {records=[],events=[],view,id,memos=[]}=snapshot;
 const rows=records.filter(p=>p.safety==='SOS'||heads.some(e=>e.change.type!=='INJURY'&&e.change.target?.id===p.originId)).map(p=>{
  const presentation=personPresentation(p,events,view,id);
  return {target:{kind:'PERSON',id:p.originId},title:presentation.name,description:p.safety==='SOS'?(memos.find(m=>m.answerId===view?.answers[p.originId]?.eventId)?.text||snapshot.venue?.scenario?.people?.find(x=>x.deviceId===p.originId)?.note||''):'',person:p,statusIcon:presentation.status};
 });
 const reports=new Map();
 for(const e of heads.filter(e=>e.change.type==='REPORT')){const list=reports.get(e.change.reportId)||[];list.push(e);reports.set(e.change.reportId,list);}
 for(const [reportId,list] of reports)rows.push({target:{kind:'REPORT',id:reportId},title:[...new Set((list.some(e=>!e.change.cancelled)?list.filter(e=>!e.change.cancelled):list).map(e=>e.change.category))].join(' / '),description:(list.some(e=>!e.change.cancelled)?list.filter(e=>!e.change.cancelled):list).map(e=>e.change.description).join(' / '),reports:list,statusIcon:'REPORT'});
 return rows.filter(row=>!row.person||personPresentation(row.person,events,view,id).disclosed).map(row=>{
  const relevant=heads.filter(e=>e.change.target?.kind===row.target.kind&&e.change.target?.id===row.target.id);
  const progress=relevant.filter(e=>e.change.type==='PROGRESS');
  return {...row,heads:relevant,progress,assignments:relevant.filter(e=>e.change.type==='ASSIGNMENT'&&e.change.active),materials:relevant.filter(e=>e.change.type==='MATERIAL'),priorities:relevant.filter(e=>e.change.type==='PRIORITY'),updatedAt:Math.max(0,...[...relevant,...row.reports||[]].map(e=>e.operatedAt))};
 });
}

export function createWeb1617Ui({map,snapshot,getStore,changed,tab,selectPerson,selectBuilding}) {
 const $=id=>document.getElementById(id);
 let language='ja';try{language=localStorage.getItem('tasukeai-language')||'ja';}catch{}
 let groupLabel='',groupLabelId='';const groupName=()=>groupLabelId===snapshot().group&&groupLabel?groupLabel:t('このグループ');
 let unknownOnly=false;let heads=[],history=[],positions=[],memos=[],cases=[],filter='all',target=null,busy=false,renderEpoch=0,memoDirty=false;
 const positionFor=id=>positions.find(p=>p.originId===id);
 const t=key=>language==='en'?(words[key]||key):({ROAD_BLOCKED:'道路閉塞',WATER_OUTAGE:'断水',BUILDING_DAMAGE:'建物損傷'}[key]||key);
 const h=key=>escape(t(key));
 const elapsed=value=>{if(!value)return t('日時不明');const sec=Math.max(0,Math.floor((Date.now()-value)/1000));return language==='en'?(sec<60?`${sec} seconds ago`:`${Math.floor(sec/60)} minutes ago`):(sec<60?`${sec}秒前`:`${Math.floor(sec/60)}分前`);};
 const date=value=>value?new Date(value).toLocaleString(language==='en'?'en-GB':'ja-JP') : t('日時不明');
 const statusLabels={SOS:'SOS',SAFE:'無事',RESCUED:'救助済み',UNREPORTED:'応答なし'};
 const progressLabels={UNASSIGNED:'未対応',PLANNED:'対応予定',IN_PROGRESS:'対応中',DONE:'対応済み'};
 const priorityLabels={UNSET:'未評価',LOW:'低',MEDIUM:'中',HIGH:'高'};
 const availabilityLabels={UNSET:'未設定',AVAILABLE:'活動可能',WORKING:'対応中',UNAVAILABLE:'活動不可'};
 const reportAsset=urgency=>urgency==='HIGH'?'/web1617-report-emergency.png':['MEDIUM','LOW'].includes(urgency)?'/web1617-report-normal.png':'/web1617-report-unrated.svg';
 const urgencyFor=row=>['HIGH','MEDIUM','LOW'].find(u=>row.priorities.some(e=>e.change.urgency===u))||'UNSET';
 function icon(status,urgency='UNSET'){return status==='BUILDING'?'<img class="case-symbol" src="/nav-buildings.svg" alt="">':status==='REPORT'?`<img class="case-symbol report-symbol" src="${reportAsset(urgency)}" alt="">`:`<span class="person-symbol ${escape(status)}" aria-hidden="true">${status==='SOS'?'SOS':status==='UNREPORTED'?'?':`<img src="/web1617-${status==='RESCUED'?'rescued':'safe'}.svg" alt="">`}</span>`;}
 const chevron='<img class="row-chevron" src="/chevron.svg" alt="">';
 const member=()=>{try{return memberFor(snapshot().roster,snapshot().id);}catch{return null;}};
 const canWrite=()=>!!member()&&!!snapshot().view&&!snapshot().view.closed;
 const coordinator=()=>['ADMIN','LEADER'].includes(member()?.role);
 function dialog(id,title){const d=document.createElement('dialog');d.id=id;d.className='design-sheet';d.setAttribute('aria-labelledby',id+'-title');d.innerHTML=`<div class="sheet-handle"></div><header><h2 id="${id}-title">${h(title)}</h2><button type="button" class="icon-button sheet-close" aria-label="${h('閉じる')}"><img src="/close.svg" alt=""></button></header><div class="sheet-body"></div>`;document.body.append(d);wireSheetHandle(d);d.querySelector('.sheet-close').onclick=()=>d.close();d.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();d.close();}});return d;}
 const incidentSheet=dialog('incident-details-sheet','訓練の詳細');
 const personSheet=dialog('person-sheet','メンバー一覧');personSheet.querySelector('.sheet-body').append($('detail'));
 const personHeading=document.createElement('div');personHeading.className='person-heading';
 const headingText=document.createElement('div');headingText.className='person-heading-text';headingText.append(personSheet.querySelector('h2'));
 const headingStatus=document.createElement('span');headingStatus.className='person-heading-status';headingText.append(headingStatus);
 const headingIcon=document.createElement('span');headingIcon.className='person-heading-icon';personHeading.append(headingIcon,headingText);personSheet.querySelector('header').prepend(personHeading);
 const personFooter=document.createElement('div');personFooter.className='sheet-footer';personSheet.append(personFooter);
 const personHomeCache=new Map();

 const settingsSheet=dialog('design-settings','設定');const caseSheet=dialog('case-sheet','対応');const editor=dialog('response-editor','対応');const summary=dialog('area-sheet','地域集計');
 const buildingSheet=dialog('map-building-sheet','建物の詳細'),townSheet=dialog('town-summary-sheet','町内会の状況'),townDetails=dialog('town-details-sheet','町内会の詳細');
 let selectedMapBuilding=null,buildingEpoch=0;
 const local=(ja,en)=>language==='en'?en:ja;
 const observationIcon=kind=>`<span class="obs-icon ${kind}"><img src="/web1617-${kind==='enclosed'?'enclosed.png':'impact-collision.svg'}" alt=""></span>`;
 const observationCard=(kind,time)=>`<div class="observation">${observationIcon(kind)}<div><b>${kind==='enclosed'?h('閉所を検知'):local('衝撃あり','Impact detected')}</b><small>${escape(new Date(time).toLocaleString(language==='en'?'en-GB':'ja-JP',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}))}</small></div></div>`;
 function renderMapBuilding(){
  const b=selectedMapBuilding;if(!b)return;const s=snapshot(),input=communityInput(),own=input.people.find(p=>p.id===s.id)?.communityId;
  const visible=linkedBuildingResidents(b,s.placeRows||[],s.records).map(p=>({p,v:personPresentation(p,s.events,s.view,s.id,Date.now(),positionFor(p.originId))})).filter(({p,v})=>v.disclosed&&v.showHome&&(!s.venue?.scenario||input.people.find(x=>x.id===p.originId)?.communityId===own));
  const registered=(s.placeRows||[]).find(r=>r.kind==='BUILDING'&&(r.buildingId===b.registeredId||r.locationReference?.plateauId===b.id));
  const a=b.attributes||{},unknown=local('不明','Unknown'),structure=({'601':local('木造','Wood'),'602':local('鉄骨鉄筋コンクリート造','Steel reinforced concrete'),'603':local('鉄筋コンクリート造','Reinforced concrete'),'604':local('鉄骨造','Steel'),'605':local('軽量鉄骨造','Light steel')})[a.structureCode]||unknown;
  buildingSheet.dataset.buildingId=b.id;buildingSheet.querySelector('h2').textContent=local('建物の詳細','Building details');
  setHtml(buildingSheet.querySelector('.sheet-body'),`<p>${escape(b.label||registered?.label||local('選択した建物','Selected building'))}</p><dl class="building-attributes"><div><dt>${local('建築年','Year built')}</dt><dd>${a.year?escape(a.year)+local('年',''):unknown}</dd></div><div><dt>${local('建材・構造','Structure')}</dt><dd>${escape(structure)}</dd></div><div><dt>${local('階数','Storeys')}</dt><dd>${a.floors?escape(a.floors)+local('階建',''):unknown}</dd></div></dl><h3>${local('個別に確認できる登録住民','Registered residents you can view')}</h3>${visible.map(({p,v})=>`<button class="resident person-row" data-building-person="${escape(p.originId)}">${icon(v.status)}<span><strong>${escape(v.name===p.name?p.name:t(v.name))}</strong><small>${statusName(v.status)}</small></span>${chevron}</button>`).join('')||`<p class="muted">${local('表示できる住民情報はありません。','No resident information available to display.')}</p>`}`);
  for(const button of buildingSheet.querySelectorAll('[data-building-person]'))button.onclick=()=>{buildingSheet.close();selectPerson(button.dataset.buildingPerson);renderPerson(button.dataset.buildingPerson,true);};
 }
 function showMapBuildings(buildings){
  personSheet.close();townSheet.close();townDetails.close();
  if(buildings.length===1){selectedMapBuilding=buildings[0];renderMapBuilding();}
  else{selectedMapBuilding=null;buildingSheet.querySelector('h2').textContent=local('建物を選ぶ','Choose a building');buildingSheet.querySelector('.sheet-body').innerHTML=buildings.map((b,i)=>`<button class="resident" data-roof="${i}">${escape(b.label||local('建物','Building')+' '+(i+1))} · ${escape(b.attributes?.floors||local('階数不明','Unknown storeys'))}</button>`).join('');for(const btn of buildingSheet.querySelectorAll('[data-roof]'))btn.onclick=()=>{selectedMapBuilding=buildings[Number(btn.dataset.roof)];renderMapBuilding();};}
  if(!buildingSheet.open)buildingSheet.show();
 }
 async function openRegisteredBuilding(place){const epoch=++buildingEpoch;try{const building=await buildingAtRegisteredPlace(place);if(epoch===buildingEpoch&&snapshot().group===place.groupId)showMapBuildings([building]);}catch{if(epoch===buildingEpoch)showMapBuildings([{id:place.locationReference?.plateauId||place.buildingId,registeredId:place.buildingId,label:place.label,attributes:{}}]);}}
 map.setBuildingSelect(showMapBuildings);
 function showTown(id,details=false){
  const region=communityMapOverview(communityInput(),snapshot().id).find(r=>r.id===id);if(!region){townSheet.close();townDetails.close();return;}
  const sheet=details?townDetails:townSheet;sheet.dataset.communityId=id;sheet.querySelector('h2').textContent=language==='en'?region.nameEn:region.nameJa;
  const body=sheet.querySelector('.sheet-body');
  if(details){setHtml(body,`<h3>${h('不足資材')}</h3>${region.materials.map(m=>`<div class="town-material"><strong>${escape(m.name)}</strong><span>${h('不足')} ${m.shortfall} ${escape(m.unit)}</span></div>`).join('')||`<p class="muted">${local('不足の記録はありません。','No recorded shortfalls.')}</p>`}`);townSheet.close();}
  else{setHtml(body,`<dl class="town-counts">${[['sos','SOS'],['unreported','応答なし'],['safe','無事'],['rescued','救助済み']].map(([key,label])=>`<div><dt>${h(label)}</dt><dd>${region[key]}<small>${local('人','')}</small></dd></div>`).join('')}</dl><p class="town-urgent"><span><img src="/web1617-report-emergency.png" alt=""> ${local('高い緊急度の通報','High-priority reports')}</span><strong>${region.urgentOpen} ${local('件','')}</strong></p><p class="muted">${local('未対応・対応予定・対応中','Not started · Acknowledged · In progress')}</p><p class="town-material"><span>${h('必要資材')}</span><strong>${region.materials.length?local('不足あり','Shortfalls'):local('不足なし','No shortfalls')}</strong></p><button class="text-button" id="town-zoom">${local('この町内会を拡大','Zoom to this neighborhood')}</button>`);
   body.querySelector('#town-zoom').onclick=()=>{map.focusRegion(region);townSheet.close();};
   let footer=sheet.querySelector('.sheet-footer');if(!footer){footer=document.createElement('div');footer.className='sheet-footer';footer.innerHTML='<button id="town-detail-open"></button>';sheet.append(footer);}footer.querySelector('button').textContent=local('詳細を見る','View details');footer.querySelector('button').onclick=()=>showTown(id,true);
  }
  if(!sheet.open)sheet.show();
 }
 const detail=$('detail');detail.classList.add('sheet-person');const careEditor=dialog('person-care-sheet','救助の記録');careEditor.querySelector('.sheet-body').append($('care-form'));
 const historyDetails=document.createElement('details');historyDetails.className='case-history';historyDetails.innerHTML='<summary>対応の記録</summary>';detail.querySelector('h3').hidden=true;detail.querySelector(':scope > p.muted').hidden=true;historyDetails.append($('history'));detail.append(historyDetails);const personAction=document.createElement('button');personAction.className='person-rescue';personAction.type='button';personFooter.append(personAction);personAction.onclick=()=>{$('action').value='RESCUED';careEditor.querySelector('h2').textContent=t('救助の記録');careEditor.show();};
 const listIntro=document.createElement('div');listIntro.className='people-intro';$('residents').prepend(listIntro);
 const stats=document.createElement('div');stats.className='group-statistics';$('residents').prepend(stats);
 $('residents').querySelector(':scope > h2').hidden=true;$('filter').closest('label').hidden=true;
 const filterBar=document.createElement('div');filterBar.className='person-filters';listIntro.after(filterBar);
 const locationNote=document.createElement('button');locationNote.type='button';locationNote.onclick=()=>{unknownOnly=!unknownOnly;renderPeople();};locationNote.className='unknown-row';filterBar.after(locationNote);
 const activity=document.createElement('button');activity.id='own-activity';activity.className='availability-card';$('response').prepend(activity);
 const responseTitle=$('response').querySelector(':scope > h2');responseTitle.hidden=true;
 const canceledButton=document.createElement('button');canceledButton.id='canceled-reports';canceledButton.className='text-button';$('response-list').after(canceledButton);canceledButton.onclick=()=>{filter=filter==='canceled'?'all':'canceled';renderResponse();};
 const responseActions=document.createElement('div');responseActions.className='response-actions';responseActions.innerHTML='<button id="report-add" type="button"></button><button id="response-export" class="text-button" type="button"></button>';$('response-list').after(responseActions);
 $('response-residents').hidden=true;$('response-buildings').hidden=true;
 const areaButton=document.createElement('button');areaButton.id='area-summary';areaButton.className='text-button response-summary-link';areaButton.onclick=()=>showSummary(true,null);activity.after(areaButton);
 const languagePanel=document.createElement('fieldset');languagePanel.className='language-settings';languagePanel.id='language-settings';languagePanel.innerHTML='<legend>言語 / Language</legend><label><span>日本語</span><input type="radio" name="ui-language" value="ja"></label><label><span>English</span><input type="radio" name="ui-language" value="en"></label>';$('settings').querySelector('h2').after(languagePanel);
 
 const appearancePanel=document.createElement('fieldset');appearancePanel.className='appearance-settings';appearancePanel.innerHTML='<legend>表示モード</legend>'+[['system','端末に合わせる'],['light','ライト'],['dark','ダーク']].map(([value,label])=>`<label><span data-label="${label}">${label}</span><input type="radio" name="ui-appearance" value="${value}"></label>`).join('');
 appearancePanel.onchange=e=>{window.dispatchEvent(new CustomEvent('appearance-change',{detail:e.target.value}));};
 settingsSheet.querySelector('.sheet-body').append(appearancePanel,languagePanel);
 const notes=document.querySelector('.app-notes');if(notes)settingsSheet.querySelector('.sheet-body').append(notes);
document.querySelector('[data-menu="settings"]').onclick=()=>{$('app-menu').close();settingsSheet.show();};
 languagePanel.onchange=e=>{language=e.target.value;try{localStorage.setItem('tasukeai-language',language);}catch{}applyLanguage();render();};
 // Only static interface text is captured. Names, addresses and free text never enter this translator.
 const staticText=[];for(const root of [document.querySelector('.app-nav'),$('app-menu'),$('safety'),$('settings'),$('detail'),careEditor,$('buildings'),$('property')]){
  const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node;while(node=walker.nextNode()){const key=node.textContent.trim();if(words[key])staticText.push([node,key]);}
 }
 function applyLanguage(){document.documentElement.lang=language;for(const [node,key]of staticText)if(node.isConnected)node.textContent=t(key);for(const radio of languagePanel.querySelectorAll('input'))radio.checked=radio.value===language;
  settingsSheet.querySelector('h2').textContent=t('アプリ設定');appearancePanel.querySelector('legend').textContent=t('表示モード');for(const el of appearancePanel.querySelectorAll('[data-label]'))el.textContent=t(el.dataset.label);for(const radio of appearancePanel.querySelectorAll('input'))radio.checked=radio.value===(document.documentElement.dataset.appearance||'system');languagePanel.querySelector('legend').textContent=t('言語');const currentView=snapshot().view;if(member())$('header-group').textContent=t(({ADMIN:'管理者',LEADER:'班長',MEMBER:'メンバー'})[member().role])+' · '+(groupName());if(words[$('network').textContent])$('network').textContent=t($('network').textContent);document.querySelector('.app-header h1').textContent=currentView?t(currentView.start.training?'訓練':'地震'):'';if(currentView)$('header-date').textContent=date(currentView.start.createdAt);areaButton.textContent=t('地域集計');$('report-add').textContent=t('通報を追加');$('response-export').textContent=t('CSVを書き出す');
  $('incident-details').hidden=!currentView;$('incident-details').textContent=t('詳細');$('header-offline').setAttribute('aria-label',t('インターネット未接続'));if(incidentSheet.open)renderIncidentDetails();
  for(const d of [personSheet,caseSheet,editor,summary,settingsSheet,careEditor,incidentSheet])d.querySelector('.sheet-close').setAttribute('aria-label',t('閉じる'));
 }
 function renderIncidentDetails(){const view=snapshot().view;if(!view){incidentSheet.close();return;}incidentSheet.querySelector('h2').textContent=t(view.start.training?'訓練の詳細':'地震の詳細');incidentSheet.querySelector('.sheet-body').innerHTML=`<dl class="incident-detail-facts"><div><dt>${h('開始')}</dt><dd>${escape(date(view.start.createdAt))}</dd></div><div><dt>${h('経過時間')}</dt><dd>${Math.max(0,Math.floor((Date.now()-view.start.createdAt)/60000))}${language==='en'?' min':'分'}</dd></div></dl>`;}
 $('incident-details').onclick=()=>{renderIncidentDetails();if(snapshot().view)incidentSheet.show();};
 const offlineHost=$('header-offline');offlineHost.innerHTML='<span class="offline-word">No internet</span><svg class="offline-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 3l18 18M3 8c5-4 13-4 18 0M6 12c4-3 8-3 12 0M9 16c2-1 4-1 6 0"/><circle cx="12" cy="20" r="1"/></svg>';
 const header=document.querySelector('.app-header');const adaptHeader=()=>header.classList.toggle('large-header-text',parseFloat(getComputedStyle(document.documentElement).fontSize)>20);new ResizeObserver(adaptHeader).observe(header);adaptHeader();
 const updateInternet=()=>{offlineHost.style.visibility=navigator.onLine?'hidden':'visible';offlineHost.setAttribute('aria-hidden',String(navigator.onLine));};window.addEventListener('online',updateInternet);window.addEventListener('offline',updateInternet);updateInternet();
 function statusName(status){return h(statusLabels[status]||status);}
 function renderPeople(){const s=snapshot(),presented=s.records.map(p=>({p,...personPresentation(p,s.events,s.view,s.id,Date.now(),positionFor(p.originId))}));
  setHtml(stats,`<h3>${h('このグループの安否')}</h3><div class="safety-statistics">${['SOS','UNREPORTED','SAFE','RESCUED'].map(k=>`<div><span>${statusName(k)}</span><strong>${presented.filter(p=>p.status===k).length}</strong></div>`).join('')}</div>`);
  setHtml(listIntro,`<h2>${h('メンバー一覧')}</h2><p>${h('個別に確認できる人')}</p>`);
  const opts=[['ALL','全員',presented.length],['SOS','SOS',presented.filter(p=>p.status==='SOS').length],['UNREPORTED','応答なし',presented.filter(p=>p.status==='UNREPORTED').length],['SAFE','無事・救助済み',presented.filter(p=>['SAFE','RESCUED'].includes(p.status)).length]];
  setHtml(filterBar,opts.map(([v,label,n])=>`<button type="button" data-person-filter="${v}" aria-pressed="${$('filter').value===v}">${h(label)} <span>${n}</span></button>`).join(''));
  for(const b of filterBar.querySelectorAll('button'))b.onclick=()=>{unknownOnly=false;$('filter').value=b.dataset.personFilter;renderPeople();};
  setHtml(locationNote,`<img src="/nav-user.svg" alt="">${h('位置不明')} ${presented.filter(p=>p.unknownLocation).length}`);
  const shown=presented.filter(p=>!unknownOnly||p.unknownLocation).filter(p=>$('filter').value==='ALL'||($('filter').value==='NONSAFE'?!['SAFE','RESCUED'].includes(p.status):$('filter').value==='SAFE'?['SAFE','RESCUED'].includes(p.status):p.status===$('filter').value));
  setHtml($('list'),shown.length?shown.map(p=>`<button class="resident person-row" data-person="${escape(p.p.originId)}">${icon(p.status)}<span><strong>${escape(p.disclosed&&p.name===p.p.name?p.name:t(p.name))}</strong><small>${statusName(p.status)} · ${h(p.unknownLocation?'位置不明':s.venue?'会場デモの位置':'最後の端末位置')}${!p.unknownLocation&&!s.venue?' · '+escape(elapsed(positionFor(p.p.originId).acquiredAt)):''}</small>${p.showObservations?`<small class="person-observations">${[p.p.impactAt?h('衝撃を検知'):'',p.p.enclosedAt?h('閉所を検知'):''].filter(Boolean).join(' · ')}</small>`:''}${p.showHome&&(p.p.floor||p.p.room)?`<small>${h('寝室')}：${escape([p.p.floor,p.p.room].filter(Boolean).join(' · '))}</small>`:''}</span>${chevron}</button>`).join(''):`<p class="empty-state">${h('対象の記録はありません。')}</p>`);
  for(const b of $('list').querySelectorAll('button'))b.onclick=()=>{selectPerson(b.dataset.person);renderPerson(b.dataset.person,true);};
 }
 function renderPerson(id,open=false){const s=snapshot(),p=s.records.find(p=>p.originId===id);detail.hidden=!p;if(!p){personSheet.close();return;}
  personSheet.dataset.personId=id;const v=personPresentation(p,s.events,s.view,s.id,Date.now(),positionFor(p.originId));personSheet.querySelector('h2').textContent=v.name===p.name?p.name:t(v.name);$('detail-name').hidden=true;
  setHtml(headingIcon,icon(v.status));headingStatus.textContent=t(statusLabels[v.status]);headingStatus.dataset.status=v.status;
  const pos=positionFor(id),homeBuilding=v.showHome?registeredBuildingForPerson(s.placeRows||[],id):null;
  const homeKey=homeBuilding?JSON.stringify([s.group,homeBuilding.buildingId,homeBuilding.latitude,homeBuilding.longitude,homeBuilding.locationReference]):null;
  if(homeKey&&!personHomeCache.has(homeKey)){personHomeCache.set(homeKey,{pending:true});buildingAtRegisteredPlace(homeBuilding).then(b=>personHomeCache.set(homeKey,{attributes:b.attributes||{}})).catch(()=>personHomeCache.set(homeKey,{attributes:{}})).finally(()=>{if(personSheet.open&&personSheet.dataset.personId===id&&snapshot().group===s.group)renderPerson(id);});}
  const homeData=personHomeCache.get(homeKey),attrs=homeData?.attributes||{},unknown=t('不明');
  const material=({'601':local('木造','Wood'),'602':local('鉄骨鉄筋コンクリート造','Steel reinforced concrete'),'603':local('鉄筋コンクリート造','Reinforced concrete'),'604':local('鉄骨造','Steel'),'605':local('軽量鉄骨造','Light steel')})[attrs.structureCode]||unknown;
  const age=attrs.year?Math.max(0,new Date().getFullYear()-attrs.year):null;
  const time=pos?.acquiredAt?new Date(pos.acquiredAt).toLocaleTimeString(language==='en'?'en-GB':'ja-JP',{hour:'2-digit',minute:'2-digit'}):null;
  const positionText=!pos?h('位置不明'):`${local('最後のスマホ位置','Last phone location')}　${time?escape(time)+local('取得',' captured')+'・'+escape(elapsed(pos.acquiredAt)):h('日時不明')}${pos.source!=='DEMO_ASSIGNED'&&Number.isFinite(pos.accuracyMeters)?'・'+local('精度 約','Accuracy ~')+escape(pos.accuracyMeters)+'m':''}`;
  const sample=s.venue?.scenario?.people?.find(x=>x.deviceId===id),memo=v.disclosed?(memos.find(m=>m.answerId===s.view?.answers[id]?.eventId)?.text||sample?.note||''):'';
  const bedroom=[p.floor?(language==='en'?'Floor '+p.floor:p.floor+'階'):'',p.room].filter(Boolean).join('・')||t('未登録');
  const pin='<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7m0 10a3 3 0 1 1 0-6 3 3 0 0 1 0 6"/></svg>';
  const bed='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 4v17m20-10v10M2 17h20M2 9h6v8m0-6h11a3 3 0 0 1 3 3v3H8Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>';
  setHtml($('detail-info'),`<p class="person-position">${pin}<span>${positionText}</span></p>${memo?`<p class="sos-note">${escape(memo)}</p>`:''}${v.showHome?`<section class="person-home"><h3>${local('普段の住居・寝室','Usual home and sleeping area')}</h3><div class="person-address"><strong>${escape(p.homeAddress||homeBuilding?.label||t('未登録'))}</strong>${homeBuilding?`<button id="person-home-building" class="text-button">${local('建物を見る','View building')}${chevron}</button>`:''}</div><div class="person-bedroom">${bed}<strong>${escape(bedroom)}</strong></div><dl id="person-home-attributes" class="building-attributes" data-status="${homeData?.pending?'loading':'ready'}"><div><dt>${local('築年数','Building age')}</dt><dd>${age!==null?local('築'+age+'年',age+' years'):unknown}</dd></div><div><dt>${local('建材・構造','Structure')}</dt><dd>${escape(material)}</dd></div><div><dt>${local('階数','Storeys')}</dt><dd>${attrs.floors?escape(attrs.floors)+local('階建',''):unknown}</dd></div></dl></section><section class="person-daytime"><h3>${h('普段昼間いる場所')}</h3><p>${escape(p.daytimePlace||sample?.daytimePlace||t('未登録'))}</p></section>`:!v.disclosed?`<p class="notice">${h('応答がない人の詳細は、安否確認の開始から3分後に表示します。')}</p>`:''}${v.showObservations?`<section class="person-observation-section"><h3>${local('スマホの観測','Phone observations')}</h3><div class="observations">${p.impactAt?observationCard('impact',p.impactAt):''}${p.enclosedAt?observationCard('enclosed',p.enclosedAt):''}</div></section>`:''}`);
  const homeButton=$('person-home-building');if(homeButton)homeButton.onclick=()=>openRegisteredBuilding(homeBuilding);
  const all=s.events.filter(e=>e.subjectId===id).sort((a,b)=>b.createdAt-a.createdAt);const active=currentEvents(all);
  $('history').innerHTML=v.disclosed&&all.length?all.map(e=>`<div class="event"><strong>${h(({CHECKING:'確認中',NEEDS_HELP:'要救助',RESCUED:'救助済み',REVOKED:'撤回'})[e.action]||e.action)}</strong><p>${escape(e.note)}</p><small>${escape(e.authorName)} · ${escape(date(e.createdAt))}</small></div>`).join(''):`<p class="muted">${h('まだ記録はありません。')}</p>`;
  const old=$('supersedes').value;$('supersedes').innerHTML='<option value="">'+h('対応の記録')+'</option>'+active.filter(e=>e.authorId===s.id).map(e=>`<option value="${e.eventId}">${escape(date(e.createdAt))}</option>`).join('');if(active.some(e=>e.eventId===old))$('supersedes').value=old;
  $('care-form').hidden=!v.disclosed||!s.view||s.view.closed||!s.records.some(r=>r.originId===s.id);personAction.hidden=$('care-form').hidden||v.status==='SAFE';personFooter.hidden=personAction.hidden;setHtml(personAction,`<img src="/web1617-rescued.svg" alt="">${h(v.status==='RESCUED'?'救助記録を訂正する':'救助済みにする')}`);historyDetails.querySelector('summary').textContent=local('救助・確認の記録','Rescue and check history')+' '+(v.disclosed?all.length:0)+local('件','');
  if(open&&!personSheet.open)personSheet.show();
 }
 function progress(row){if(row.legacyActionLabel)return t(row.legacyActionLabel);return row.progress.length?[...new Set(row.progress.map(e=>t(progressLabels[e.change.status])))].join(' / '):t('未対応');}
 function assignees(row){return row.assignments.map(e=>snapshot().roster?.members.find(m=>m.memberId===e.change.memberId)?.activityName||'').filter(Boolean).join(' / ')||t('担当者無し');}
 function priority(row){return row.priorities.map(e=>t(priorityLabels[e.change.urgency])).join(' / ')||t('未評価');}
 function renderResponse(){applyLanguage();const me=member(),s=snapshot();
  cases=responseCases({...s,memos},heads.filter(e=>e.groupId===s.group&&e.incidentId===s.incidentId));const buildings=new Map(placeBuildings(s.placeRows||[]).map(b=>[b.buildingId,b.label]));for(const e of currentBuildingCare((s.placeRows||[]).filter(e=>e.kind==='CARE'&&e.incidentId===s.incidentId))){cases.push({target:{kind:'BUILDING',id:e.buildingId},title:buildings.get(e.buildingId)||t('建物'),description:e.note||e.scope,legacyActionLabel:BUILDING_ACTIONS[e.action],statusIcon:'BUILDING',heads:[],progress:[],assignments:[],materials:[],priorities:[],updatedAt:e.createdAt});}
  const av=heads.filter(e=>e.change.type==='AVAILABILITY'&&s.roster&&memberFor(s.roster,e.actorDeviceId).memberId===me?.memberId);
  activity.innerHTML=`<strong>${h('自分の活動')}</strong><span class="activity-state">${av.map(e=>t(availabilityLabels[e.change.state])).join(' / ')||t('未設定')}</span>${chevron}`;activity.disabled=!me;
  const filters=[['unassigned','担当者無し'],['mine','自分の担当'],['all','すべて']];
  const matches=(r,f)=>f==='canceled'?r.reports?.every(e=>e.change.cancelled):f==='all'||(f==='mine'?r.assignments.some(e=>e.change.memberId===me?.memberId):!r.assignments.length);
  document.querySelector('.response-filters').innerHTML=filters.map(([v,k])=>`<button data-response-filter="${v}" aria-pressed="${filter===v}">${h(k)} <span>${cases.filter(r=>matches(r,v)&&!r.reports?.every(e=>e.change.cancelled)).length}</span></button>`).join('');
  for(const b of document.querySelectorAll('[data-response-filter]'))b.onclick=()=>{filter=b.dataset.responseFilter;renderResponse();};
  $('response-context').textContent=s.view?'':t('災害・訓練を選択してください。');
  canceledButton.textContent=t(filter==='canceled'?'すべて':'取り消した通報');canceledButton.hidden=!cases.some(r=>r.reports?.every(e=>e.change.cancelled));const rows=cases.filter(r=>matches(r,filter)&&(filter==='canceled'||!r.reports?.every(e=>e.change.cancelled)));$('response-list').innerHTML=rows.length?rows.map(r=>`<button class="case-row" data-case="${escape(r.target.kind+':'+r.target.id)}">${icon(r.statusIcon,urgencyFor(r))}<span class="case-copy"><strong>${escape(r.target.kind!=='REPORT'?r.title:t(r.title))}</strong><span class="case-priority ${r.priorities.some(e=>e.change.urgency==='HIGH')?'high':''}">${escape(priority(r))}</span><p>${escape(r.description||t(statusLabels[r.statusIcon]||''))}</p><span class="progress-tag">${escape(progress(r))}</span><small>${h('担当')}：${escape(assignees(r))}</small><small>${escape(date(r.updatedAt||s.view?.start.createdAt))}</small></span>${chevron}</button>`).join(''):`<p class="empty-state">${h('対象の記録はありません。')}</p>`;
  for(const b of $('response-list').querySelectorAll('button'))b.onclick=()=>{target=cases.find(r=>r.target.kind+':'+r.target.id===b.dataset.case)?.target;if(target?.kind==='BUILDING'){selectBuilding(target.id);return;}showCase();};
  $('report-add').disabled=!canWrite();$('response-export').disabled=!me||!s.view;
 }
 async function render(){const epoch=++renderEpoch,s=snapshot();applyLanguage();const localRows=await Promise.all([getStore().positions(s.group),getStore().memos(s.group),getStore().read('meta','cloud-session:'+s.group)]);if(epoch!==renderEpoch||s.group!==snapshot().group||s.incidentId!==snapshot().incidentId)return;[positions,memos]=localRows;if(s.venue)positions=venuePositions(s.venue,positions);groupLabel=localRows[2]?.groupName||'';groupLabelId=s.group;applyLanguage();renderPeople();renderSafety();
  const responseRows=s.roster?await Promise.all([getStore().responseHeads(s.group),getStore().responseEvents(s.group)]):[[],[]];
  if(epoch!==renderEpoch||s.group!==snapshot().group||s.incidentId!==snapshot().incidentId)return;[heads,history]=responseRows;renderResponse();renderMap();if(personSheet.open)renderPerson(personSheet.dataset.personId);if(incidentSheet.open)renderIncidentDetails();if(caseSheet.open)showCase(false);if(summary.open)showSummary(false);if(buildingSheet.open&&selectedMapBuilding)renderMapBuilding();if(townSheet.open)showTown(townSheet.dataset.communityId);if(townDetails.open)showTown(townDetails.dataset.communityId,true);
 }
 function currentCase(){return cases.find(r=>r.target.kind===target?.kind&&r.target.id===target?.id);}
 function showCase(open=true){const row=currentCase();if(!row)return;caseSheet.querySelector('h2').innerHTML=`${row.target.kind==='REPORT'?`<img class="sheet-title-icon" src="${reportAsset(urgencyFor(row))}" alt="">`:''}${escape(row.target.kind==='REPORT'?t(row.title):row.title)}`;
  const mine=row.assignments.some(e=>e.change.memberId===member()?.memberId),body=caseSheet.querySelector('.sheet-body');
  const entries=history.filter(e=>e.change.target?.kind===target.kind&&e.change.target?.id===target.id||target.kind==='REPORT'&&e.change.reportId===target.id).sort((a,b)=>b.operatedAt-a.operatedAt);
  body.innerHTML=`<div id="case-feedback" role="status"></div>${!mine?`<p class="case-caution">${h('あなたはこの案件の担当ではありません')}</p>`:''}${row.reports?`<p class="registered-place">${h('記録者')}：${escape(coordinator()?memberFor(snapshot().roster,row.reports[0].actorDeviceId).activityName:t('メンバー'))}</p>`:''}${row.description?`<p class="case-description">${escape(row.description)}</p>`:''}${row.reports?`<p class="muted">${h('地域はまだ設定されていません。')}</p>`:''}<dl class="case-facts"><div><dt>${h('担当')}</dt><dd>${escape(assignees(row))}</dd></div><div><dt>${h('対応状況')}</dt><dd>${escape(progress(row))}</dd></div><div><dt>${h('緊急度')}</dt><dd>${escape(priority(row))}</dd></div></dl><div class="case-secondary-actions"><button data-edit="assignment" class="secondary">${h('担当を変更')}</button><button data-edit="progress" class="secondary">${h('対応状況を変更')}</button>${coordinator()?`<button data-edit="priority" class="secondary">${h('緊急度')}</button>`:''}${row.reports&&row.reports.every(e=>coordinator()||memberFor(snapshot().roster,e.actorDeviceId).memberId===member()?.memberId)?`<button data-edit="report" class="secondary">${h('通報を訂正')}</button><button data-edit="cancel-report" class="secondary">${h(row.reports.every(e=>e.change.cancelled)?'通報を復元':'通報を取り消す')}</button>`:''}</div><h3 class="materials-heading">${h('必要資材')}</h3><div>${row.materials.length?row.materials.map(e=>`<button class="material-row" data-material="${e.eventId}"><span><strong>${escape(e.change.name)}</strong><small>${h('必要')} ${e.change.needed}${escape(e.change.unit)} · ${h('手配済み')} ${e.change.arranged}${escape(e.change.unit)}</small></span><strong>${h('不足')} ${Math.max(0,e.change.needed-e.change.arranged)}${escape(e.change.unit)}</strong>${chevron}</button>`).join(''):`<p class="muted">${h('まだ記録はありません。')}</p>`}</div><button class="text-button add-material">${h('資材を要請する')}</button><details class="case-history"><summary>${h('対応の記録')} ${entries.length}</summary>${entries.map(e=>`<div class="event"><strong>${h(({AVAILABILITY:'活動状況',REPORT:'通報内容',PROGRESS:'対応状況',PRIORITY:'緊急度',ASSIGNMENT:'担当',MATERIAL:'必要資材'})[e.change.type]||e.change.type)}</strong><p>${escape(e.change.cancelled?t('通報を取り消す')+'：'+e.change.cancelReason:e.change.type==='REPORT'&&e.replaces.some(id=>history.find(p=>p.eventId===id)?.change.cancelled)?t('通報を復元')+'：'+e.change.description:e.change.result||e.change.reason||e.change.description||e.change.name||'')}</p><small>${escape(memberFor(snapshot().roster,e.actorDeviceId).activityName)} · ${escape(date(e.operatedAt))}</small></div>`).join('')}</details><div class="case-bottom"><button class="start-response">${h(mine?'担当を解除':'対応を始める')}</button></div>`;
  for(const b of body.querySelectorAll('button'))b.disabled=!canWrite()||(row.reports?.every(e=>e.change.cancelled)&&!['cancel-report','report'].includes(b.dataset.edit));
  for(const b of body.querySelectorAll('[data-edit]'))b.onclick=()=>edit(b.dataset.edit);
  for(const b of body.querySelectorAll('[data-material]')){const e=row.materials.find(e=>e.eventId===b.dataset.material);b.disabled=!canWrite()||row.reports?.every(e=>e.change.cancelled)||!(coordinator()||memberFor(snapshot().roster,e.actorDeviceId).memberId===member()?.memberId);b.onclick=()=>edit('material',e);}
  body.querySelector('.add-material').onclick=()=>edit('material');
  body.querySelector('.start-response').onclick=()=>save(async()=>{
   if(mine)await append({type:'ASSIGNMENT',target,memberId:member().memberId,active:false});
   else {const s=snapshot();await getStore().startWork(s.group,s.incidentId,s.id,target,Date.now());}
  },body.querySelector('#case-feedback'));
  caseSheet.querySelector('.sheet-footer')?.remove();
  const footer=document.createElement('div');footer.className='sheet-footer';footer.append(body.querySelector('.start-response'));body.querySelector('.case-bottom').remove();caseSheet.append(footer);
  if(open&&!caseSheet.open)caseSheet.show();
 }
 function parents(change){const s=snapshot();const candidate={groupId:s.group,incidentId:change.type==='AVAILABILITY'?null:s.incidentId,actorDeviceId:s.id,change};return heads.filter(e=>responseKey(s.roster,e)===responseKey(s.roster,candidate)).map(e=>e.eventId);}
 async function append(change,eventId=crypto.randomUUID(),replaces=parents(change)){const s=snapshot();return getStore().appendResponse({groupId:s.group,incidentId:change.type==='AVAILABILITY'?null:s.incidentId,eventId,actorDeviceId:s.id,operatedAt:Date.now(),change,replaces});}
 async function save(work,feedback){if(busy)return;busy=true;feedback.className='feedback';feedback.textContent=t('保存しています…');for(const b of editor.querySelectorAll('button'))b.disabled=true;
  try{await work();await changed();await render();feedback.textContent=t('保存しました');if(editor.open)editor.close();if(caseSheet.open){showCase(false);$('case-feedback').className='feedback';$('case-feedback').textContent=t('保存しました');}}
  catch(error){feedback.className='feedback error';feedback.textContent=error.message;}
  finally{busy=false;for(const b of editor.querySelectorAll('button'))b.disabled=false;}
 }
 const field=(key,label,value='',options={})=>`<label>${h(label)}${options.multiline?`<textarea name="${key}" maxlength="${options.max||500}" ${options.required?'required':''}>${escape(value)}</textarea>`:`<input name="${key}" value="${escape(value)}" type="${options.number?'number':'text'}" ${options.number?'step="any"':''} ${options.min!==undefined?`min="${options.min}"`:''} ${options.required?'required':''} maxlength="${options.max||240}">`}</label>`;
 function edit(kind,record,picked=null){const row=currentCase(),s=snapshot(),formTarget=target;let html='',title='',onSubmit;
  if(kind==='availability'){const replaces=parents({type:'AVAILABILITY'});title='活動状況';html=Object.entries(availabilityLabels).map(([v,k])=>`<label class="choice-row"><input type="radio" name="state" value="${v}" ${heads.some(e=>e.change.type==='AVAILABILITY'&&e.change.state===v&&memberFor(s.roster,e.actorDeviceId).memberId===member()?.memberId)?'checked':''} required>${h(k)}</label>`).join('');onSubmit=d=>append({type:'AVAILABILITY',state:d.get('state')},crypto.randomUUID(),replaces);}
  if(kind==='progress'){const replaces=row.progress.map(e=>e.eventId);title='対応状況を変更';html=(row.progress.length>1?`<p class="notice">${h('同時に更新された記録があります。保存する対応状況を選んでください。')}</p>`:'')+Object.entries(progressLabels).map(([v,k])=>`<label class="choice-row"><input type="radio" name="status" value="${v}" ${(row.progress.length===0&&v==='UNASSIGNED'||row.progress.length===1&&row.progress[0].change.status===v)?'checked':''} required>${h(k)}</label>`).join('')+field('result','対応結果',row.progress.map(e=>e.change.result).join(' / '),{multiline:true});onSubmit=d=>append({type:'PROGRESS',target:formTarget,status:d.get('status'),result:d.get('result').trim()},crypto.randomUUID(),replaces);}
  if(kind==='priority'){const replaces=row.priorities.map(e=>e.eventId);title='緊急度';html=Object.entries(priorityLabels).map(([v,k])=>`<label class="choice-row"><input type="radio" name="urgency" value="${v}" required>${h(k)}</label>`).join('')+field('reason','変更理由','',{multiline:true});onSubmit=d=>append({type:'PRIORITY',target:formTarget,urgency:d.get('urgency'),reason:d.get('reason').trim()},crypto.randomUUID(),replaces);}
  if(kind==='assignment'){title='担当を変更';const members=coordinator()?s.roster.members:[member()],replacesByMember=new Map(members.map(m=>[m.memberId,parents({type:'ASSIGNMENT',target:formTarget,memberId:m.memberId})]));html=members.map(m=>`<label class="choice-row"><input type="checkbox" name="assigned" value="${m.memberId}" ${row.assignments.some(e=>e.change.memberId===m.memberId)?'checked':''}>${escape(m.activityName)}</label>`).join('');onSubmit=async d=>{for(const m of members){const active=d.getAll('assigned').includes(m.memberId);if(active!==row.assignments.some(e=>e.change.memberId===m.memberId))await append({type:'ASSIGNMENT',target:formTarget,memberId:m.memberId,active},crypto.randomUUID(),replacesByMember.get(m.memberId));}};}
  if(kind==='material'){title='必要資材';const c=record?.change;html=field('name','資材名',c?.name,{required:true})+field('unit','単位',c?.unit,{required:true})+`<div class="columns">${field('needed','必要',c?.needed??0,{number:true,min:0,required:true})}${field('arranged','手配済み',c?.arranged??0,{number:true,min:0,required:true})}</div>`;onSubmit=d=>{const eventId=crypto.randomUUID();return append({type:'MATERIAL',target:formTarget,requestId:c?.requestId||eventId,name:d.get('name').trim(),unit:c?.unit||d.get('unit').trim(),needed:Number(d.get('needed')),arranged:Number(d.get('arranged'))},eventId,record?[record.eventId]:[]);};}
  if(kind==='cancel-report'){const replaces=row.reports.map(e=>e.eventId);title=row.reports.every(e=>e.change.cancelled)?'通報を復元':'通報を取り消す';const c=reportEditSources(row)[0].change;html=title==='通報を取り消す'?field('cancelReason','取消理由','',{required:true,multiline:true}):`<p>${escape(c.cancelReason||'')}</p>`;onSubmit=d=>append({...c,cancelled:title==='通報を取り消す',cancelReason:title==='通報を取り消す'?d.get('cancelReason').trim():''},crypto.randomUUID(),replaces);}
  if(kind==='report'||kind==='new-report'){
   title=kind==='report'?'通報を訂正':'通報を追加';const sources=kind==='report'?reportEditSources(row):[];const c=sources[0]?.change||picked;
   const revision=sources.length>1?`<label>${h('訂正する記録')}<select name="revision" required><option value="">${h('記録を選んでください')}</option>${sources.map(e=>`<option value="${e.eventId}">${escape(date(e.operatedAt))} — ${escape(e.change.description.slice(0,80))}</option>`).join('')}</select></label><p class="notice">${h('同時に更新された記録があります。選んだ記録だけ訂正します。')}</p>`:'';
   html=revision+`<label>${h('種別')}<select name="category">${[...new Set([c?.category,'道路閉塞','建物倒壊','その他'].filter(Boolean))].map(v=>`<option value="${v}" ${c?.category===v?'selected':''}>${h(v)}</option>`).join('')}</select></label>`+field('description','内容',c?.description,{multiline:true,required:true})+`<div class="columns">${field('latitude','緯度',c?.latitude,{number:true,required:true})}${field('longitude','経度',c?.longitude,{number:true,required:true})}</div>`;
   onSubmit=d=>{const source=sources.length>1?sources.find(e=>e.eventId===d.get('revision')):sources[0];if(sources.length&&!source)throw Error(t('記録を選んでください'));const original=source?.change,eventId=crypto.randomUUID();return append({type:'REPORT',reportId:original?.reportId||eventId,category:d.get('category'),description:d.get('description').trim(),latitude:Number(d.get('latitude')),longitude:Number(d.get('longitude')),regionIds:snapshot().venue?.scenario&&Number(d.get('latitude'))>35&&Number(d.get('latitude'))<35.04&&Number(d.get('longitude'))>135.75&&Number(d.get('longitude'))<135.78?[demoCommunityFor(Number(d.get('longitude')))]:original?.regionIds||[],cancelled:original?.cancelled||false,cancelReason:original?.cancelReason||''},eventId,source?[source.eventId]:[]);};
  }
  editor.querySelector('h2').textContent=t(title);editor.querySelector('.sheet-body').innerHTML=`<form id="response-edit-form">${html}<p class="feedback" role="status"></p><button type="submit">${h('保存する')}</button></form>`;
  if(kind==='report'&&editor.querySelector('[name="revision"]'))editor.querySelector('[name="revision"]').onchange=e=>{const c=row.reports.find(r=>r.eventId===e.target.value)?.change;if(c)for(const key of ['category','description','latitude','longitude'])editor.querySelector(`[name="${key}"]`).value=c[key];};
  if(kind==='material'&&record)editor.querySelector('[name="unit"]').readOnly=true;
  const form=editor.querySelector('form');
  const baseline=JSON.stringify((row?.heads||[]).concat(row?.reports||[]).map(e=>e.eventId).sort());
  const draft=bindFormDraft(form,[s.id,s.group,s.incidentId,kind,kind==='new-report'?null:formTarget,record?.change?.requestId],baseline,()=>{
   const review=document.createElement('label');review.className='choice-row draft-review';
   const check=document.createElement('input');check.type='checkbox';check.required=true;
   review.append(check,language==='en'?'The shared record changed. Review your draft before saving.':'共有された記録が更新されています。入力内容を確認してから保存してください。');form.prepend(review);
  });
  form.onsubmit=e=>{e.preventDefault();const d=new FormData(form);save(async()=>{await onSubmit(d);draft.clear();},form.querySelector('.feedback'));};
  if(!editor.open)editor.show();
 }
 let communitySignature="";
 function communityInput(){const s=snapshot(),v=s.venue;const fictional=new Set((v?.scenario?.people||[]).map(p=>p.deviceId));
  const communities=v?.scenario?[...v.scenario.communities,...(v.positionMode==='ACTUAL'?[{id:'venue',nameJa:'会場参加者',nameEn:'Venue participants'}]:[])]:[{id:'group',nameJa:groupName(),nameEn:groupName()}];
  const people=s.records.map(p=>({id:p.originId,name:p.name,communityId:v?.scenario?(v.positionMode==='ACTUAL'&&!fictional.has(p.originId)?'venue':demoCommunityFor(p.homeLongitude??p.longitude)):'group',latitude:p.homeLatitude??p.latitude,longitude:p.homeLongitude??p.longitude,status:personPresentation(p,s.events,s.view,s.id).status,fictional:fictional.has(p.originId),buildingId:(v?.placements.find(x=>x.deviceId===p.originId)?.home||v?.scenario?.people.find(x=>x.deviceId===p.originId)?.home)?.buildingId||null}));
  const input={groupId:s.group,groupName:groupName(),incidentId:s.incidentId,demo:!!v,communities,people,heads:heads.filter(e=>e.groupId===s.group&&e.incidentId===s.incidentId),sourceIds:[...s.events.map(e=>e.eventId),...Object.values(s.view?.answers||{}).map(e=>e.eventId)]};
  return input;
 }
 function showSummary(force=true,communityId=summary.dataset.communityId||null){
  const input=communityInput();summary.dataset.incidentId=input.incidentId||'';summary.dataset.communityId=communityId||'';
  const signature=JSON.stringify([language,input,communityId]);if(!force&&signature===communitySignature)return;communitySignature=signature;
  showCommunityScreen({dialog:summary,input,language,store:getStore(),editable:canWrite(),communityId,saveInjury:async(id,state)=>{const event=await append({type:'INJURY',target:{kind:'PERSON',id},state});await changed();return event;}}).catch(e=>{$('message').textContent=e.message;});
 }

 activity.onclick=()=>edit('availability');const beginReport=()=>{if(!canWrite())return;tab('buildings');map.pickLocation(position=>edit('new-report',null,position),{language});};$('report-add').onclick=beginReport;
 const legend=document.createElement('div');legend.className='people-legend';legend.innerHTML=['SOS','UNREPORTED','SAFE','RESCUED'].map(status=>`<span>${icon(status)}<span data-legend-status="${status}">${statusName(status)}</span></span>`).join('');document.querySelector('.map-legend summary').after(legend);const legendTitle=document.createElement('h3');legendTitle.className='legend-section-title';legendTitle.textContent=t('安否');legend.before(legendTitle);const observationLegend=document.createElement('div');observationLegend.className='map-observation-legend';legend.after(observationLegend);
 const mapAdd=document.createElement('button');mapAdd.id='map-report-add';mapAdd.className='map-report-add';mapAdd.type='button';mapAdd.textContent=t('通報を追加');mapAdd.onclick=beginReport;document.querySelector('.map-stage').append(mapAdd);
 $('map-show-safe').onchange=()=>renderMap();
 $('map-unknown').onclick=()=>{unknownOnly=true;$('filter').value=$('map-show-safe').checked?'ALL':'NONSAFE';tab('residents');renderPeople();};
 function renderMap(){observationLegend.innerHTML=`<span>${observationIcon('impact')} ${h('衝撃を検知')}</span><span>${observationIcon('enclosed')} ${h('閉所を検知')}</span>`;const s=snapshot(),input=communityInput(),other=communityMapOverview(input,s.id),otherIds=new Set(other.map(r=>r.id)),otherPeople=new Set(input.people.filter(p=>otherIds.has(p.communityId)).map(p=>p.id));map.setCommunities(s.venue?.scenario?other:[],id=>showTown(id));for(const label of legend.querySelectorAll('[data-legend-status]'))label.textContent=t(statusLabels[label.dataset.legendStatus]);map.setShowSafe($('map-show-safe').checked);map.setPeople(s.records.filter(p=>!s.venue?.scenario||!otherPeople.has(p.originId)).map(p=>{const position=positionFor(p.originId),v=personPresentation(p,s.events,s.view,s.id,Date.now(),position);return {id:p.originId,status:v.status,label:v.name===p.name?p.name:t(v.name),position,impactAt:v.showObservations?p.impactAt:null,enclosedAt:v.showObservations?p.enclosedAt:null};}),id=>{selectPerson(id);renderPerson(id,true);});map.setReports(cases.flatMap(r=>(r.reports||[]).map(e=>({...e.change,id:r.target.id,label:t(r.title),urgency:urgencyFor(r)}))),id=>{target={kind:'REPORT',id};showCase();});$('map-unknown-count').textContent=String(s.records.filter(p=>!positionFor(p.originId)&&($('map-show-safe').checked||!['SAFE','RESCUED'].includes(personPresentation(p,s.events,s.view,s.id).status))).length);mapAdd.disabled=!canWrite();mapAdd.textContent=t('通報を追加');}

 $('response-export').onclick=async()=>{try{const s=snapshot(),csv=await getStore().exportCsv(s.group,s.incidentId,{}, {language});const blob=new Blob([csv],{type:'text/csv;charset=utf-8'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='tasukeai-'+s.incidentId+'.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){$('message').textContent=e.message;}};
 const safetyForm=document.createElement('form');safetyForm.id='own-status-form';safetyForm.innerHTML='<label id="memo-label">SOSメモ<textarea id="own-memo" maxlength="500" rows="3"></textarea></label><p class="memo-count"></p><button id="memo-save" type="button" class="secondary">メモだけ保存する</button><button id="status-update" type="submit">安否を更新する</button><p id="own-status-feedback" class="feedback" role="status"></p>';document.querySelector('.safety-actions').after(safetyForm);
 let safetyChoice=null;
 for(const [button,value]of [['safe','SAFE'],['sos','SOS']])$(button).onclick=()=>{safetyChoice=value;renderSafety();};
 $('own-memo').oninput=()=>{memoDirty=true;safetyForm.querySelector('.memo-count').textContent=`${$('own-memo').value.length} / 500`;};
 async function saveMemo(answer){if(!answer||answer.safety!=='SOS')throw Error(t('災害・訓練を選択してください。'));const s=snapshot(),old=memos.find(m=>m.answerId===answer.eventId);await getStore().mergeMemo({groupId:s.group,incidentId:s.incidentId,authorId:s.id,answerId:answer.eventId,revision:(old?.revision||0)+1,updatedAt:Date.now(),text:$('own-memo').value.trim()});memoDirty=false;}
 $('memo-save').onclick=()=>save(()=>saveMemo(snapshot().view?.answers[snapshot().id]),$('own-status-feedback'));
 safetyForm.onsubmit=e=>{e.preventDefault();save(async()=>{const s=snapshot();const value=safetyChoice||s.view?.answers[s.id]?.safety;if(!value)throw Error(t('情報を登録してください。'));const answer=await appendScopedIncident(getStore(),{groupId:s.group,incidentId:s.incidentId,authorId:s.id},'ANSWER',{safety:value});if(value==='SOS')await saveMemo(answer);safetyChoice=null;tab('buildings');},$('own-status-feedback'));};
 function renderSafety(){const s=snapshot(),own=s.records.find(p=>p.originId===s.id),answer=s.view?.answers[s.id],choice=safetyChoice||answer?.safety;const memo=memos.find(m=>m.answerId===answer?.eventId);$('own').innerHTML=own?`<strong>${escape(own.name)}</strong><p>${s.view?escape(date(s.view.start.createdAt)):h('災害・訓練を選択してください。')}</p>`:h('情報を登録してください。');
  if(!memoDirty)$('own-memo').value=memo?.text||'';
  $('memo-label').firstChild.textContent=t('SOSメモ');$('memo-save').textContent=t('メモだけ保存する');$('status-update').textContent=t('安否を更新する');
  $('memo-label').hidden=choice!=='SOS';$('memo-save').hidden=choice!=='SOS';$('memo-save').disabled=!own||!s.view||s.view.closed||answer?.safety!=='SOS';$('status-update').disabled=!own||!s.view||s.view.closed||!choice;
  $('safe').innerHTML=icon('SAFE')+`<span>${language==='en'?'I’m safe':'無事'}</span>`;$('sos').innerHTML=icon('SOS')+`<span>${language==='en'?'I need help':'SOS'}</span>`;$('safe').setAttribute('aria-pressed',String(choice==='SAFE'));$('sos').setAttribute('aria-pressed',String(choice==='SOS'));
  safetyForm.querySelector('.memo-count').hidden=choice!=='SOS';safetyForm.querySelector('.memo-count').textContent=`${$('own-memo').value.length} / 500`;
 }
 applyLanguage();setInterval(()=>{if(!getStore())return;renderPeople();if(personSheet.open)renderPerson(personSheet.dataset.personId);if(incidentSheet.open)renderIncidentDetails();},30000);
 return {render,renderResponse,renderPerson,closeSheets:()=>{for(const d of [personSheet,caseSheet,editor,summary,settingsSheet,careEditor,incidentSheet])d.close();}};
}

