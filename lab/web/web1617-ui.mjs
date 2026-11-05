import {usDateTime,usTime} from './date-time.mjs';
import {buildingAge,linkedBuildingResidents,registeredBuildingForPerson,buildingAtRegisteredPlace} from './building-details.mjs';
import {demoAddressLabel} from './demo-address-display.mjs';
import {showCommunityScreen} from './community-screen.mjs';
import {demoCommunityFor,communityMapOverview,communityOverview,areaCaseCategory,areaCaseCommunityIds} from './community-overview.mjs';
import {setHtml} from './dom-state.mjs';
import {wireSheetHandle} from './sheet-handle.mjs';
import {venuePositions} from './venue-demo.mjs';
import {placeBuildings,currentBuildingCare,BUILDING_ACTIONS} from './places.mjs';
import {englishUI} from './web1617-english.mjs';
import {canTriage, memberFor, responseKey, responsePermissions, responseTargetPermissions, canonicalIncidentId} from './managed-records.mjs';
import {currentEvents} from './sync.mjs';
import {appendScopedIncident,revokePersonRescue} from './incidents.mjs';
import {bindFormDraft} from './form-drafts.mjs';

const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const words = {...englishUI,'津波':'Tsunami','建物の全壊率':'Building damage risk','津波の浸水深':'Tsunami inundation depth','3m以上':'3m or more','衝撃を検知':'Impact','ROAD_BLOCKED':'Blocked road','WATER_OUTAGE':'Water outage','BUILDING_DAMAGE':'Building damage','自分の登録情報':'My information','名前・住居・建物と住戸':'Name, home and dwelling','訓練・地震':'Drills and earthquakes','名前と住居':'Name and home','住んでいる建物・住戸を選ぶ':'Choose your building and dwelling','表示モード':'Appearance','端末に合わせる':'Use device setting','ライト':'Light','ダーク':'Dark','地震':'Earthquake','詳細':'Details','訓練の詳細':'Training details','地震の詳細':'Earthquake details','開始':'Started','経過時間':'Elapsed time','インターネット未接続':'No internet connection','登録情報':'Your information','災害への備え':'Preparedness','アプリ設定':'App settings','AVAILABILITY':'Availability','REPORT':'Incident report','PROGRESS':'Progress','PRIORITY':'Priority','ASSIGNMENT':'Assignment','MATERIAL':'Materials',
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
 '種類（自由入力）':'Category (free text)','品名':'Item','必要数':'Quantity needed','手配済み数':'Quantity arranged','資材を要請':'Request materials','情報を登録してください。':'Register your profile first.','対象の記録はありません。':'No matching records.',
 '災害・訓練を選択してください。':'Select an incident or drill.','参加者設定が必要です。':'Join a group to use response records.',
 '開始時の経過時間を設定します。開始後も変更できます。':'Choose the elapsed time at the start. You can change it later.',
 '開始時の経過分数':'Elapsed minutes at start',
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

// Format the floor without rewriting the saved profile or free-text room description.
export function residenceFloorLabel(floor, language='ja') {
 const value=String(floor??'').trim(),normalized=value.normalize('NFKC');
 const level=normalized.match(/^(-?\d+)(?:階)?$/),basement=normalized.match(/^(?:B|地下)(\d+)(?:階)?$/i);
 if(!level&&!basement)return value;
 const number=level?Number(level[1]):-Number(basement[1]);
 if(!Number.isSafeInteger(number)||number<-2147483648||number>2147483647)return value;
 return language==='en'?`Floor ${number}`:number<0?`地下${-number}階`:`${number}階`;
}
export function buildingStoreysLabel(floors,language='ja') {
 return language==='en'?`${floors} ${Number(floors)===1?'story':'stories'}`:`${floors}階建`;
}

/** Phone positions are separate records; home coordinates must never substitute for them. */
export function personPresentation(person, events, view, ownId, now=Date.now(), position=null, roster=null) {
 const rescues=currentEvents(events.filter(e=>e.subjectId===person.originId),roster).filter(e=>e.action==='RESCUED' && e.createdAt >= (person.updatedAt||0));
 const rescued=rescues.length>0;
 const status=rescued?'RESCUED':person.safety;
 if(['SAFE','RESCUED'].includes(status))return {status,disclosed:false,showDetails:false,showStatus:true,rescuedAt:rescued?Math.max(...rescues.map(e=>e.createdAt)):null,unknownLocation:!position,name:status==='SAFE'?'無事の人':'救助済みの人',showHome:false,showObservations:false};
 // The later venue plan permits names for mutual recognition, not disclosure of homes behind a known SOS position.
 if(!person.fictional&&person.venueSessionId&&person.venueSessionId===view?.start.eventId)return {status,disclosed:true,showDetails:true,unknownLocation:!position,name:person.name,showHome:!position||status==='UNREPORTED',showObservations:!['SAFE','RESCUED'].includes(status)&&!!(person.impactAt||person.enclosedAt)};
 const disclosed=person.originId===ownId || status!=='UNREPORTED' || (!!view && now-view.start.createdAt>=180000);
 return {status,disclosed,showDetails:true,unknownLocation:!position,name:!disclosed?'応答なし':status==='SAFE'?'無事の人':status==='RESCUED'?'救助済みの人':person.name,
  showHome:disclosed&&(!position||status==='UNREPORTED'),showObservations:!['SAFE','RESCUED'].includes(status)&&!!(person.impactAt||person.enclosedAt)};
}
/** Building statistics are anonymous, including before personal disclosure begins. */
export function buildingSafetyCounts(people,events=[],roster=null) {
 const counts={total:people.length,SAFE:0,SOS:0,UNREPORTED:0,RESCUED:0};
 for(const person of people){const {status}=personPresentation(person,events,null,null,Date.now(),null,roster);if(Object.hasOwn(counts,status))counts[status]++;}
 return counts;
}

export function personCommunity(snapshot, person) {
 const v=snapshot.venue;if(!v?.scenario)return 'group';
 const sample=v.scenario.people.find(p=>p.deviceId===person.originId);if(sample)return sample.communityId;
 if(v.positionMode==='ACTUAL')return 'venue';
 return demoCommunityFor(person.homeLongitude??person.longitude,v.scenario.communityLongitudeCuts);
}
export function visiblePeople(snapshot) {
 const records=snapshot.records||[];if(!snapshot.venue?.scenario)return records;
 const own=records.find(p=>p.originId===snapshot.id);if(!own)return [];
 const community=personCommunity(snapshot,own);
 return records.filter(p=>p.originId===snapshot.id||community&&personCommunity(snapshot,p)===community);
}

export const reportEditSources = row => row.reports.some(e=>!e.change.cancelled)?row.reports.filter(e=>!e.change.cancelled):row.reports;
export const isResolvedCase = row => row.progress.length>0 && row.progress.every(e=>e.change.status==='DONE');
export function responseCases(snapshot, heads) {
 const {records=[],events=[],view,id,memos=[]}=snapshot;
 const rows=visiblePeople(snapshot).filter(p=>p.safety==='SOS'||heads.some(e=>e.change.type!=='INJURY'&&e.change.target?.id===p.originId)).map(p=>{
  const presentation=personPresentation(p,events,view,id,Date.now(),null,snapshot.roster);
  return {target:{kind:'PERSON',id:p.originId},title:presentation.name,description:p.safety==='SOS'?(memos.find(m=>m.answerId===view?.answers[p.originId]?.eventId)?.text||snapshot.venue?.scenario?.people?.find(x=>x.deviceId===p.originId)?.note||''):'',person:p,statusIcon:presentation.status};
 });
 const reports=new Map();
 for(const e of heads.filter(e=>e.change.type==='REPORT')){const list=reports.get(e.change.reportId)||[];list.push(e);reports.set(e.change.reportId,list);}
 for(const [reportId,list] of reports)rows.push({target:{kind:'REPORT',id:reportId},title:[...new Set((list.some(e=>!e.change.cancelled)?list.filter(e=>!e.change.cancelled):list).map(e=>e.change.category))].join(' / '),description:(list.some(e=>!e.change.cancelled)?list.filter(e=>!e.change.cancelled):list).map(e=>e.change.description).join(' / '),reports:list,statusIcon:'REPORT'});
 return rows.filter(row=>!row.person||personPresentation(row.person,events,view,id,Date.now(),null,snapshot.roster).disclosed).map(row=>{
  const relevant=heads.filter(e=>e.change.target?.kind===row.target.kind&&e.change.target?.id===row.target.id);
  const progress=relevant.filter(e=>e.change.type==='PROGRESS');
  return {...row,heads:relevant,progress,assignments:relevant.filter(e=>e.change.type==='ASSIGNMENT'&&e.change.active),materials:relevant.filter(e=>e.change.type==='MATERIAL'&&!e.change.cancelled),cancelledMaterials:relevant.filter(e=>e.change.type==='MATERIAL'&&e.change.cancelled),priorities:relevant.filter(e=>e.change.type==='PRIORITY'),updatedAt:Math.max(0,...[...relevant,...row.reports||[]].map(e=>e.operatedAt))};
 });
}

export function createWeb1617Ui({map,snapshot,getStore,refreshSnapshot,changed,tab,selectPerson,selectBuilding}) {
 const $=id=>document.getElementById(id);
 let language='ja';try{language=localStorage.getItem('tasukeai-language')||'ja';}catch{}
 let groupLabel='',groupLabelId='';const groupName=()=>groupLabelId===snapshot().group&&groupLabel?groupLabel:t('このグループ');
 let unknownOnly=false;let heads=[],history=[],positions=[],memos=[],cases=[],filter='all',areaFilter=null,target=null,busy=false,renderEpoch=0,memoDirty=false;
 const positionFor=id=>positions.find(p=>p.originId===id);
 const t=key=>language==='en'?({'資材要請を取り消す':'Cancel material request','資材要請を復元':'Restore material request',...words}[key]||key):({ROAD_BLOCKED:'道路閉塞',WATER_OUTAGE:'断水',BUILDING_DAMAGE:'建物損傷'}[key]||key);
 const h=key=>escape(t(key));
 const elapsed=value=>{if(!value)return t('日時不明');const sec=Math.max(0,Math.floor((Date.now()-value)/1000));return language==='en'?(sec<60?`${sec} seconds ago`:`${Math.floor(sec/60)} minutes ago`):(sec<60?`${sec}秒前`:`${Math.floor(sec/60)}分前`);};
 const date=value=>value?usDateTime(value) : t('日時不明');
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
 buildingSheet.addEventListener('close',()=>map.clearBuildingFocus?.());
 let selectedMapBuilding=null,buildingEpoch=0,buildingFilter=null;
 const local=(ja,en)=>language==='en'?en:ja;
 const addressLabel=value=>demoAddressLabel(value,language==='en');
 const observationIcon=kind=>`<span class="obs-icon ${kind}"><img src="/web1617-${kind==='enclosed'?'enclosed.png':'impact-collision.svg'}" alt=""></span>`;
 const observationCard=(kind,time)=>`<div class="observation">${observationIcon(kind)}<div><b>${kind==='enclosed'?h('閉所を検知'):local('衝撃あり','Impact')}</b><small>${escape(usDateTime(time))}</small></div></div>`;
 function renderMapBuilding(){
  const b=selectedMapBuilding;if(!b)return;const s=snapshot(),input=communityInput(),own=input.people.find(p=>p.id===s.id)?.communityId;
  const linked=linkedBuildingResidents(b,s.placeRows||[],s.records),counts=buildingSafetyCounts(linked,s.events,s.roster);
  const visible=linked.map(p=>({p,v:personPresentation(p,s.events,s.view,s.id,Date.now(),positionFor(p.originId),s.roster)})).filter(({p,v})=>v.status==='UNREPORTED'&&v.disclosed&&v.showHome&&(!s.venue?.scenario||input.people.find(x=>x.id===p.originId)?.communityId===own));
  const outsideNeighborhood=!!s.venue?.scenario&&linked.length>0&&linked.every(p=>input.people.find(x=>x.id===p.originId)?.communityId!==own);
  const registered=(s.placeRows||[]).find(r=>r.kind==='BUILDING'&&(r.buildingId===b.registeredId||r.locationReference?.plateauId===b.id));
  const a=b.attributes||{},unknown=b.attributesStatus==='error'?local('取得できません','Unavailable'):b.attributesStatus==='unmatched'?local('照合できません','Not matched'):b.attributesStatus==='unlinked'?local('未登録','Not linked'):local('不明','Unknown'),structure=({'601':local('木造','Wood'),'602':local('鉄骨鉄筋コンクリート造','Steel reinforced concrete'),'603':local('鉄筋コンクリート造','Reinforced concrete'),'604':local('鉄骨造','Steel'),'605':local('軽量鉄骨造','Light steel')})[a.structureCode]||unknown;
  buildingSheet.dataset.buildingId=b.id;buildingSheet.querySelector('h2').textContent=local('建物の詳細','Building details');
  setHtml(buildingSheet.querySelector('.sheet-body'),`${outsideNeighborhood?`<p class="muted">${local('この建物は所属する町内会の範囲外です。','This building is outside your neighborhood.')}</p>`:''}${b.label||registered?.label?`<p>${escape(addressLabel(b.label||registered.label))}</p>`:''}<dl class="building-attributes"><div><dt>${local('築年数','Building age')}</dt><dd>${a.year?local('築'+buildingAge(a.year)+'年',buildingAge(a.year)+' years'):unknown}</dd></div><div><dt>${local('建材・構造','Structure')}</dt><dd>${escape(structure)}</dd></div><div><dt>${local('階数','Stories')}</dt><dd>${a.floors?escape(buildingStoreysLabel(a.floors,language)):unknown}</dd></div></dl>${b.attributesStatus==='error'?`<button class="text-button" id="building-attributes-retry">${local('再読み込み','Retry')}</button>`:''}<h3>${local('登録住民の安否','Registered resident safety')}</h3><dl class="town-counts building-safety-counts">${[['total',local('合計','Total')],['SAFE',t('無事')],['UNREPORTED',t('応答なし')],['SOS','SOS'],['RESCUED',t('救助済み')]].map(([key,label])=>`<div data-building-count="${key}"><dt>${escape(label)}</dt><dd>${counts[key]}<small>${local('人','')}</small></dd></div>`).join('')}</dl><h3>${local('応答なし','No response')}</h3>${visible.map(({p,v})=>`<button class="resident person-row" data-building-person="${escape(p.originId)}">${icon(v.status)}<span><strong>${escape(v.name===p.name?p.name:t(v.name))}</strong><small>${statusName(v.status)}</small></span>${chevron}</button>`).join('')||(outsideNeighborhood?'':`<p class="muted">${local('この建物で共有されている個別情報はありません。','No individual details are shared for this building.')}</p>`)}`);
  const retry=buildingSheet.querySelector('#building-attributes-retry');if(retry&&b.registeredPlace)retry.onclick=()=>openRegisteredBuilding(b.registeredPlace);
  for(const button of buildingSheet.querySelectorAll('[data-building-person]'))button.onclick=()=>{const id=button.dataset.buildingPerson;buildingSheet.close();const position=positionFor(id);if(position){tab('buildings');map.focusPerson(position);}selectPerson(id);renderPerson(id,true);};
 }
 async function showMapBuildings(buildings){
  await refreshSnapshot?.();
  if(document.body.dataset.page!=='buildings')tab('buildings');
  buildingFilter=null;
  personSheet.close();townSheet.close();townDetails.close();
  if(buildings.length===1){selectedMapBuilding=buildings[0];renderMapBuilding();}
  else{selectedMapBuilding=null;buildingSheet.querySelector('h2').textContent=local('建物を選ぶ','Choose a building');buildingSheet.querySelector('.sheet-body').innerHTML=buildings.map((b,i)=>`<button class="resident" data-roof="${i}">${escape(addressLabel(b.label||local('建物','Building')+' '+(i+1)))} · ${escape(b.attributes?.floors?buildingStoreysLabel(b.attributes.floors,language):local('階数不明','Unknown stories'))}</button>`).join('');for(const btn of buildingSheet.querySelectorAll('[data-roof]'))btn.onclick=()=>{selectedMapBuilding=buildings[Number(btn.dataset.roof)];map.focusBuilding(selectedMapBuilding);renderMapBuilding();};}
  if(!buildingSheet.open)buildingSheet.show();
  if(selectedMapBuilding)map.focusBuilding(selectedMapBuilding);
 }
 async function openRegisteredBuilding(place){tab('buildings');map.focusBuilding(place);const epoch=++buildingEpoch;try{const building=await buildingAtRegisteredPlace(place);if(epoch===buildingEpoch&&snapshot().group===place.groupId){map.focusBuilding({...place,geometry:building.geometry});showMapBuildings([{...building,latitude:place.latitude,longitude:place.longitude}]);}}catch{if(epoch===buildingEpoch)showMapBuildings([{id:place.locationReference?.plateauId||place.buildingId,registeredId:place.buildingId,label:place.label,attributes:{},attributesStatus:'error',registeredPlace:place}]);}}
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
 const historyDetails=document.createElement('details');historyDetails.className='case-history';historyDetails.innerHTML='<summary>対応の記録</summary>';detail.querySelector('h3').hidden=true;detail.querySelector(':scope > p.muted').hidden=true;historyDetails.append($('history'));detail.append(historyDetails);const personAction=document.createElement('button');personAction.className='person-rescue';personAction.type='button';personFooter.append(personAction);personAction.onclick=()=>{$('supersedes').value='';$('care-correction').open=false;$('action').value='RESCUED';careEditor.querySelector('h2').textContent=t('救助の記録');careEditor.show();};
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
 const staticText=[];for(const root of [document.querySelector('.app-nav'),$('app-menu'),$('safety'),$('settings'),$('detail'),careEditor,$('buildings'),$('property'),notes]){
  const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node;while(node=walker.nextNode()){const key=node.textContent.trim();if(words[key])staticText.push([node,key]);}
 }
 function applyLanguage(){document.documentElement.lang=language;
  for(const feedback of document.querySelectorAll('.feedback')){const text=feedback.textContent.trim(),key=Object.hasOwn(words,text)?text:Object.keys(words).find(key=>words[key]===text);if(key)feedback.textContent=t(key);}
  for(const id of ['buildings','tsunami-map'])$(id).setAttribute('aria-label',t('地図'));document.querySelector('.response-filters')?.setAttribute('aria-label',t('対応記録の対象'));

  $('menu-open').setAttribute('aria-label',t('メニュー'));document.querySelector('.app-nav').setAttribute('aria-label',t('画面'));
  for(const handle of document.querySelectorAll('.sheet-handle'))handle.setAttribute('aria-label',local('下にドラッグして閉じる','Drag to close'));for(const close of document.querySelectorAll('.sheet-close'))close.setAttribute('aria-label',t('閉じる'));map.refreshLanguage?.();for(const [node,key]of staticText)if(node.isConnected)node.textContent=t(key);for(const radio of languagePanel.querySelectorAll('input'))radio.checked=radio.value===language;
  $('tsunami-height').setAttribute('aria-label',local('津波の高さ（m）','Tsunami height (m)'));$('tsunami-fit').setAttribute('aria-label',local('現在地','My location'));settingsSheet.querySelector('h2').textContent=t('アプリ設定');appearancePanel.querySelector('legend').textContent=t('表示モード');for(const el of appearancePanel.querySelectorAll('[data-label]'))el.textContent=t(el.dataset.label);for(const radio of appearancePanel.querySelectorAll('input'))radio.checked=radio.value===(document.documentElement.dataset.appearance||'system');languagePanel.querySelector('legend').textContent=t('言語');const currentView=snapshot().view;if(member())$('header-group').textContent=t(({ADMIN:'管理者',LEADER:'班長',MEMBER:'メンバー'})[member().role])+' · '+(groupName());if(words[$('network').textContent])$('network').textContent=t($('network').textContent);document.querySelector('.app-header h1').textContent=currentView?t(currentView.start.training?'訓練':'地震'):'';if(currentView)$('header-date').textContent=date(currentView.start.createdAt);areaButton.textContent=t('地域集計');$('report-add').textContent=t('通報を追加');$('response-export').textContent=t('CSVを書き出す');
  $('incident-details').hidden=!currentView;$('incident-details').textContent=t('詳細');$('header-offline').setAttribute('aria-label',t('インターネット未接続'));if(incidentSheet.open)renderIncidentDetails();
  for(const d of [personSheet,caseSheet,editor,summary,settingsSheet,careEditor,incidentSheet])d.querySelector('.sheet-close').setAttribute('aria-label',t('閉じる'));
 }
 function renderIncidentDetails(){const view=snapshot().view;if(!view){incidentSheet.close();return;}incidentSheet.querySelector('h2').textContent=t(view.start.training?'訓練の詳細':'地震の詳細');incidentSheet.querySelector('.sheet-body').innerHTML=`<dl class="incident-detail-facts"><div><dt>${h('開始')}</dt><dd>${escape(date(view.start.createdAt))}</dd></div><div><dt>${h('経過時間')}</dt><dd>${Math.max(0,Math.floor((Date.now()-view.start.createdAt)/60000))}${language==='en'?' min':'分'}</dd></div></dl>`;}
 $('incident-details').onclick=()=>{renderIncidentDetails();if(snapshot().view)incidentSheet.show();};
 const offlineHost=$('header-offline');offlineHost.innerHTML='<span class="offline-word">No internet</span><svg class="offline-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 3l18 18M3 8c5-4 13-4 18 0M6 12c4-3 8-3 12 0M9 16c2-1 4-1 6 0"/><circle cx="12" cy="20" r="1"/></svg>';
 const header=document.querySelector('.app-header');const adaptHeader=()=>header.classList.toggle('large-header-text',parseFloat(getComputedStyle(document.documentElement).fontSize)>20);new ResizeObserver(adaptHeader).observe(header);adaptHeader();
 const updateInternet=()=>{offlineHost.style.visibility=navigator.onLine?'hidden':'visible';offlineHost.setAttribute('aria-hidden',String(navigator.onLine));};window.addEventListener('online',updateInternet);window.addEventListener('offline',updateInternet);updateInternet();
 function statusName(status){return h(statusLabels[status]||status);}
 function renderPeople(){const s=snapshot(),presented=visiblePeople(s).map(p=>({p,...personPresentation(p,s.events,s.view,s.id,Date.now(),positionFor(p.originId),s.roster)}));
  const towns=communityOverview(communityInput());
  setHtml(stats,`<h3>${local('町内会の安否','Neighborhood safety')}</h3><div class="neighborhood-table-wrap" tabindex="0" role="region" aria-label="${local('町内会の安否','Neighborhood safety')}"><table class="neighborhood-table"><thead><tr><th scope="col">${local('町内会','Neighborhood')}</th>${['SOS','UNREPORTED','SAFE','RESCUED'].map(k=>`<th scope="col">${statusName(k)}</th>`).join('')}</tr></thead><tbody>${towns.map(r=>`<tr><th scope="row">${escape(language==='en'?r.nameEn:r.nameJa)}</th>${['sos','unreported','safe','rescued'].map(k=>`<td>${r[k]}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`);
  setHtml(listIntro,`<h2>${h('メンバー一覧')}</h2><p>${h('個別に確認できる人')}</p>`);
  const opts=[['ALL','全員',presented.length],['SOS','SOS',presented.filter(p=>p.status==='SOS').length],['UNREPORTED','応答なし',presented.filter(p=>p.status==='UNREPORTED').length],['SAFE','無事・救助済み',presented.filter(p=>['SAFE','RESCUED'].includes(p.status)).length]];
  setHtml(filterBar,opts.map(([v,label,n])=>`<button type="button" data-person-filter="${v}" aria-pressed="${$('filter').value===v}">${h(label)} <span>${n}</span></button>`).join(''));
  for(const b of filterBar.querySelectorAll('button'))b.onclick=()=>{unknownOnly=false;$('filter').value=b.dataset.personFilter;renderPeople();};
  setHtml(locationNote,`<img src="/nav-user.svg" alt="">${h('位置不明')} ${presented.filter(p=>p.unknownLocation).length}`);
  const shown=presented.filter(p=>!unknownOnly||p.unknownLocation).filter(p=>$('filter').value==='ALL'||($('filter').value==='NONSAFE'?!['SAFE','RESCUED'].includes(p.status):$('filter').value==='SAFE'?['SAFE','RESCUED'].includes(p.status):p.status===$('filter').value));
  setHtml($('list'),shown.length?shown.map(p=>`<button class="resident person-row" data-person="${escape(p.p.originId)}" >${icon(p.status)}<span><strong>${escape(p.disclosed&&p.name===p.p.name?p.name:t(p.name))}</strong><small>${statusName(p.status)} · ${h(p.unknownLocation?'位置不明':'最後の端末位置')}${!p.unknownLocation?' · '+escape(elapsed(positionFor(p.p.originId).acquiredAt)):''}</small>${p.showObservations?`<small class="person-observations">${[p.p.impactAt?h('衝撃を検知'):'',p.p.enclosedAt?h('閉所を検知'):''].filter(Boolean).join(' · ')}</small>`:''}${p.showHome&&(p.p.floor||p.p.room)?`<small>${h('寝室')}：${escape([residenceFloorLabel(p.p.floor,language),p.p.room].filter(Boolean).join(' · '))}</small>`:''}</span>${chevron}</button>`).join(''):`<p class="empty-state">${h('対象の記録はありません。')}</p>`);
  for(const b of $('list').querySelectorAll('button'))b.onclick=()=>{selectPerson(b.dataset.person);renderPerson(b.dataset.person,true);};
 }
 function renderPerson(id,open=false){const s=snapshot(),p=visiblePeople(s).find(p=>p.originId===id);detail.hidden=!p;if(!p){personSheet.close();return;}
  personSheet.dataset.personId=id;const v=personPresentation(p,s.events,s.view,s.id,Date.now(),positionFor(p.originId),s.roster);personSheet.querySelector('h2').textContent=v.name===p.name?p.name:t(v.name);$('detail-name').hidden=true;
  setHtml(headingIcon,icon(v.status));headingStatus.textContent=t(statusLabels[v.status]);headingStatus.dataset.status=v.status;
  if(v.showStatus){
   careEditor.close();$('care-form').hidden=true;personAction.hidden=true;personFooter.hidden=true;historyDetails.hidden=true;$('history').replaceChildren();$('supersedes').replaceChildren();
   setHtml($('detail-info'),`<p class="anonymous-person-status">${statusName(v.status)}</p>${v.status==='RESCUED'?`<p class="person-rescued-at">${local('救助日時','Rescued at')}：${escape(date(v.rescuedAt))}</p>`:''}${v.status==='RESCUED'&&member()?.role==='ADMIN'&&canWrite()?`<button id="person-revoke-rescue" class="secondary" type="button">${local('救助済みを取り消す','Revoke rescue')}</button><p id="person-rescue-feedback" class="feedback" role="status"></p>`:''}`);
   const revoke=$('person-revoke-rescue');if(revoke)revoke.onclick=()=>{if(busy)return;revoke.disabled=true;save(async()=>{const current=snapshot();await revokePersonRescue(getStore(),{groupId:current.group,incidentId:current.incidentId,authorId:current.id},id);},$('person-rescue-feedback')).finally(()=>{revoke.disabled=false;});};
   if(open&&!personSheet.open)personSheet.show();return;
  }
  historyDetails.hidden=false;
  const pos=positionFor(id),homeBuilding=v.showHome?registeredBuildingForPerson(s.placeRows||[],id):null;
  const homeKey=homeBuilding?JSON.stringify([s.group,homeBuilding.buildingId,homeBuilding.latitude,homeBuilding.longitude,homeBuilding.locationReference]):null;
  if(homeKey&&!personHomeCache.has(homeKey)){personHomeCache.set(homeKey,{pending:true});buildingAtRegisteredPlace(homeBuilding).then(b=>personHomeCache.set(homeKey,{attributes:b.attributes||{},status:b.attributesStatus})).catch(()=>personHomeCache.set(homeKey,{attributes:{},status:'error'})).finally(()=>{if(personSheet.open&&personSheet.dataset.personId===id&&snapshot().group===s.group)renderPerson(id);});}
  const homeData=personHomeCache.get(homeKey),attrs=homeData?.attributes||{},unknown=homeData?.pending?local('読み込み中…','Loading…'):homeData?.status==='error'?local('取得できません','Unavailable'):homeData?.status==='unmatched'?local('照合できません','Not matched'):homeData?.status==='unlinked'?local('未登録','Not linked'):t('不明');
  const material=({'601':local('木造','Wood'),'602':local('鉄骨鉄筋コンクリート造','Steel reinforced concrete'),'603':local('鉄筋コンクリート造','Reinforced concrete'),'604':local('鉄骨造','Steel'),'605':local('軽量鉄骨造','Light steel')})[attrs.structureCode]||unknown;
  const age=buildingAge(attrs.year);
  const time=pos?.acquiredAt?usTime(pos.acquiredAt):null;
  const positionText=!pos?h('位置不明'):`${local('最後のスマホ位置','Last phone location')}　${time?escape(time)+local('取得',' captured')+'・'+escape(elapsed(pos.acquiredAt)):h('日時不明')}${pos.source!=='DEMO_ASSIGNED'&&Number.isFinite(pos.accuracyMeters)?'・'+local('精度 約','Accuracy ~')+escape(pos.accuracyMeters)+'m':''}`;
  const sample=s.venue?.scenario?.people?.find(x=>x.deviceId===id),memo=v.disclosed?(memos.find(m=>m.answerId===s.view?.answers[id]?.eventId)?.text||sample?.note||''):'';
  const bedroom=[residenceFloorLabel(p.floor,language),p.room].filter(Boolean).join('・')||t('未登録');
  const pin='<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7m0 10a3 3 0 1 1 0-6 3 3 0 0 1 0 6"/></svg>';
  const bed='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 4v17m20-10v10M2 17h20M2 9h6v8m0-6h11a3 3 0 0 1 3 3v3H8Z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>';
  setHtml($('detail-info'),`<p class="person-position">${pin}<span>${positionText}</span></p>${memo?`<p class="sos-note">${escape(memo)}</p>`:''}${v.showHome?`<section class="person-home"><h3>${local('普段の住居・寝室','Usual home and sleeping area')}</h3><div class="person-address"><strong>${escape(addressLabel(p.homeAddress||homeBuilding?.label||t('未登録')))}</strong>${homeBuilding?`<button id="person-home-building" class="text-button">${local('建物を見る','View building')}${chevron}</button>`:''}</div><div class="person-bedroom">${bed}<strong>${escape(bedroom)}</strong></div><dl id="person-home-attributes" class="building-attributes" data-status="${homeData?.pending?'loading':homeData?.status==='error'?'error':'ready'}"><div><dt>${local('築年数','Building age')}</dt><dd>${age!==null?local('築'+age+'年',age+' years'):unknown}</dd></div><div><dt>${local('建材・構造','Structure')}</dt><dd>${escape(material)}</dd></div><div><dt>${local('階数','Stories')}</dt><dd>${attrs.floors?escape(buildingStoreysLabel(attrs.floors,language)):unknown}</dd></div></dl>${homeData?.status==='error'?`<button id="person-attributes-retry" class="text-button">${local('再読み込み','Retry')}</button>`:''}</section><section class="person-daytime"><h3>${h('普段昼間いる場所')}</h3><p>${escape(p.daytimePlace||sample?.daytimePlace||t('未登録'))}</p></section>`:!v.disclosed?`<p class="notice">${h('応答がない人の詳細は、安否確認の開始から3分後に表示します。')}</p>`:''}${v.showObservations?`<section class="person-observation-section"><h3>${local('検知情報','Detected events')}</h3><div class="observations">${p.impactAt?observationCard('impact',p.impactAt):''}${p.enclosedAt?observationCard('enclosed',p.enclosedAt):''}</div></section>`:''}`);
  const attrRetry=$('person-attributes-retry');if(attrRetry)attrRetry.onclick=()=>{personHomeCache.delete(homeKey);renderPerson(id);};
  const homeButton=$('person-home-building');if(homeButton)homeButton.onclick=()=>openRegisteredBuilding(homeBuilding);
  const all=s.events.filter(e=>e.subjectId===id).sort((a,b)=>b.createdAt-a.createdAt);const active=currentEvents(all,s.roster);
  $('history').innerHTML=v.disclosed&&all.length?all.map(e=>`<div class="event"><strong>${h(({CHECKING:'確認中',NEEDS_HELP:'要救助',RESCUED:'救助済み',REVOKED:'撤回'})[e.action]||e.action)}</strong><p>${escape(e.note)}</p><small>${escape(e.authorName==='Demo rescue team'?local('救助担当','Rescue team'):e.authorName)} · ${escape(date(e.createdAt))}</small></div>`).join(''):`<p class="muted">${h('まだ記録はありません。')}</p>`;
  const old=$('supersedes').value;$('supersedes').innerHTML='<option value="">'+h('新しい記録')+'</option>'+active.filter(e=>e.authorId===s.id).map(e=>`<option value="${e.eventId}">${escape(date(e.createdAt))} · ${h(({CHECKING:'確認中',NEEDS_HELP:'要救助',RESCUED:'救助済み',REVOKED:'撤回'})[e.action])}</option>`).join('');if(active.some(e=>e.eventId===old))$('supersedes').value=old;$('care-correction').hidden=!active.some(e=>e.authorId===s.id);
  $('care-form').hidden=!v.disclosed||!s.view||s.view.closed||!s.records.some(r=>r.originId===s.id);personAction.hidden=$('care-form').hidden||v.status==='SAFE';personFooter.hidden=personAction.hidden;setHtml(personAction,`<img src="/web1617-rescued.svg" alt="">${h(v.status==='RESCUED'?'救助記録を訂正する':'救助済みにする')}`);historyDetails.querySelector('summary').textContent=local('救助・確認の記録','Rescue and check history')+' '+(v.disclosed?all.length:0)+local('件','');
  if(open&&!personSheet.open)personSheet.show();
 }
 function progress(row){if(row.legacyActionLabel)return t(row.legacyActionLabel);return row.progress.length?[...new Set(row.progress.map(e=>t(progressLabels[e.change.status])))].join(' / '):t('未対応');}
 function assignees(row){return row.assignments.map(e=>snapshot().roster?.members.find(m=>m.memberId===e.change.memberId)?.activityName||'').filter(Boolean).join(' / ')||t('担当者無し');}
 function priority(row){return row.priorities.map(e=>t(priorityLabels[e.change.urgency])).join(' / ')||t('未評価');}
 function renderResponse(){applyLanguage();const me=member(),s=snapshot();
  cases=responseCases({...s,memos},heads.filter(e=>e.groupId===s.group&&e.incidentId===s.incidentId));const buildings=new Map(placeBuildings(s.placeRows||[]).map(b=>[b.buildingId,b.label]));for(const e of currentBuildingCare((s.placeRows||[]).filter(e=>e.kind==='CARE'&&e.incidentId===s.incidentId))){cases.push({target:{kind:'BUILDING',id:e.buildingId},title:buildings.get(e.buildingId)||t('建物'),description:e.note||e.scope,legacyActionLabel:BUILDING_ACTIONS[e.action],statusIcon:'BUILDING',heads:[],progress:[],assignments:[],materials:[],priorities:[],updatedAt:e.createdAt});}
  const av=heads.filter(e=>e.change.type==='AVAILABILITY'&&s.roster&&memberFor(s.roster,e.actorDeviceId).memberId===me?.memberId);
  setHtml(activity,`<strong>${h('自分の活動')}</strong><span class="activity-state">${av.map(e=>t(availabilityLabels[e.change.state])).join(' / ')||t('未設定')}</span>${chevron}`);activity.disabled=!me;
  const filters=[['unassigned','担当者無し'],['mine','自分の担当'],['all','すべて']];
  const matches=(r,f)=>f==='canceled'?r.reports?.every(e=>e.change.cancelled):f==='all'||(f==='mine'?r.assignments.some(e=>e.change.memberId===me?.memberId):!r.assignments.length);
  setHtml(document.querySelector('.response-filters'),filters.map(([v,k])=>`<button data-response-filter="${v}" aria-pressed="${filter===v}">${h(k)} <span>${cases.filter(r=>matches(r,v)&&!r.reports?.every(e=>e.change.cancelled)).length}</span></button>`).join(''));
  for(const b of document.querySelectorAll('[data-response-filter]'))b.onclick=()=>{filter=b.dataset.responseFilter;areaFilter=null;renderResponse();};
  $('response-context').textContent=areaFilter?[areaFilter.communityId?regionName(areaFilter.communityId):local('全町内会','All neighborhoods'),t(areaFilter.category==='all'?'すべて':areaFilter.category)].join(' · '):s.view?'':t('災害・訓練を選択してください。');
  canceledButton.textContent=t(filter==='canceled'?'すべて':'取り消した通報');canceledButton.hidden=!cases.some(r=>r.reports?.every(e=>e.change.cancelled));const rows=cases.filter(r=>matches(r,filter)&&(filter==='canceled'||!r.reports?.every(e=>e.change.cancelled))&&(!areaFilter||(areaFilter.category==='all'?areaCaseCategory(r):areaCaseCategory(r)===areaFilter.category)&&(!areaFilter.communityId||areaCaseCommunityIds(r,communityInput()).includes(areaFilter.communityId))));setHtml($('response-list'),rows.length?rows.map(r=>`<button class="case-row" data-case="${escape(r.target.kind+':'+r.target.id)}">${icon(r.statusIcon,urgencyFor(r))}<span class="case-copy"><strong>${escape(r.target.kind!=='REPORT'?r.title:t(r.title))}</strong><span class="case-priority ${r.priorities.some(e=>e.change.urgency==='HIGH')?'high':''}">${escape(priority(r))}</span><p>${escape(r.description||t(statusLabels[r.statusIcon]||''))}</p><span class="progress-tag">${escape(progress(r))}</span><small>${h('担当')}：${escape(assignees(r))}</small><small>${escape(date(r.updatedAt||s.view?.start.createdAt))}</small></span>${chevron}</button>`).join(''):`<p class="empty-state">${h('対象の記録はありません。')}</p>`);
  for(const b of $('response-list').querySelectorAll('button'))b.onclick=()=>{target=cases.find(r=>r.target.kind+':'+r.target.id===b.dataset.case)?.target;if(target?.kind==='BUILDING'){selectBuilding(target.id);return;}showCase();};
  $('report-add').disabled=!canWrite();$('response-export').disabled=!me||!s.view;
 }
 async function render(){const epoch=++renderEpoch,s=snapshot();applyLanguage();const localRows=await Promise.all([getStore().positions(s.group),getStore().memos(s.group),getStore().read('meta','cloud-session:'+s.group)]);if(epoch!==renderEpoch||s.group!==snapshot().group||s.incidentId!==snapshot().incidentId)return;[positions,memos]=localRows;if(s.venue)positions=venuePositions(s.venue,positions);groupLabel=localRows[2]?.groupName||'';groupLabelId=s.group;applyLanguage();renderPeople();renderSafety();
  const responseRows=s.roster?await Promise.all([getStore().responseHeads(s.group),getStore().responseEvents(s.group),getStore().quakeEvents(s.group)]):[[],[],[]];
  if(epoch!==renderEpoch||s.group!==snapshot().group||s.incidentId!==snapshot().incidentId)return;heads=responseRows[0];history=responseRows[1].map(e=>({...e,incidentId:canonicalIncidentId(s.roster,responseRows[2],e.incidentId)}));renderResponse();renderMap();if(personSheet.open)renderPerson(personSheet.dataset.personId);if(incidentSheet.open)renderIncidentDetails();if(caseSheet.open)showCase(false);if(summary.open)showSummary(false);if(buildingSheet.open&&selectedMapBuilding)renderMapBuilding();if(townSheet.open)showTown(townSheet.dataset.communityId);if(townDetails.open)showTown(townDetails.dataset.communityId,true);
 }
 function currentCase(){return cases.find(r=>r.target.kind===target?.kind&&r.target.id===target?.id);}
 function casePermissions(row){const s=snapshot();if(!s.roster||!member()||!row)return {canEdit:false,canCancel:false,canChangePriority:false};return responseTargetPermissions(s.roster,history,s.id,row.target,{groupId:s.group,incidentId:s.incidentId});}
 function materialPermissions(event){const s=snapshot();if(!s.roster||!member())return {canEdit:false,canCancel:false};return responsePermissions(s.roster,history,s.id,event);}
 function regionName(id){const c=communityInput().communities.find(c=>c.id===id);return c?(language==='en'?c.nameEn:c.nameJa):id;}
 function reportRegions(row){return [...new Set(reportEditSources(row).flatMap(e=>e.change.regionIds||[]))].map(regionName).join(' / ')||local('地域未設定','Area not set');}
 const reportAuthorSafe=row=>row.reports?reportAuthor(row):'';
 function reportAuthor(row){
  const s=snapshot(),original=history.find(e=>e.eventId===row.target.id&&e.change.type==='REPORT')||row.reports[0],person=s.records.find(p=>p.originId===original.actorDeviceId);
  const own=s.records.find(p=>p.originId===s.id),same=person&&own&&personCommunity(s,person)===personCommunity(s,own);
  if(person&&['SAFE','RESCUED'].includes(personPresentation(person,s.events,s.view,s.id,Date.now(),null,s.roster).status))return local('通報者','Reporter');
  if(coordinator()&&same)return person.name;
  return addressLabel(person?.homeAddress||registeredBuildingForPerson(s.placeRows||[],original.actorDeviceId)?.label||t('未登録'));
 }
 function regionOptions(ids=[]){const available=communityInput().communities;return `<option value="">${local('地域未設定','Area not set')}</option>`+[...available,...ids.filter(id=>!available.some(c=>c.id===id)).map(id=>({id,nameJa:id,nameEn:id}))].map(c=>`<option value="${escape(c.id)}" ${ids.includes(c.id)?'selected':''}>${escape(language==='en'?c.nameEn:c.nameJa)}</option>`).join('');}
 function showCase(open=true){
  const row=currentCase();if(!row){caseSheet.close();return;}
  const perms=casePermissions(row),editable=canWrite()&&perms.canEdit,cancelled=row.reports?.every(e=>e.change.cancelled);
  caseSheet.querySelector('h2').innerHTML=`${row.target.kind==='REPORT'?`<img class="sheet-title-icon" src="${reportAsset(urgencyFor(row))}" alt="">`:''}${escape(row.target.kind==='REPORT'?t(row.title):row.title)}`;
  const mine=row.assignments.some(e=>e.change.memberId===member()?.memberId),body=caseSheet.querySelector('.sheet-body');
  const entries=history.filter(e=>e.change.target?.kind===target.kind&&e.change.target?.id===target.id||target.kind==='REPORT'&&e.change.reportId===target.id).sort((a,b)=>b.operatedAt-a.operatedAt);
  const signature=JSON.stringify([row,entries,language,snapshot().roster,reportAuthorSafe(row),canWrite()]);if(caseSheet.dataset.contentSignature===signature){if(open&&!caseSheet.open)caseSheet.show();return;}caseSheet.dataset.contentSignature=signature;
  const editButton=(kind,label)=>`<button data-edit="${kind}" class="secondary">${h(label)}</button>`;
  const materialRow=e=>{const c=e.change,allowed=canWrite()&&materialPermissions(e).canEdit&&!cancelled;
   return `<div class="material-entry"><${allowed?'button':'div'} class="material-row" ${allowed?`data-material="${e.eventId}"`:''}><span><strong>${escape(c.name)}</strong><small>${h('必要')} ${c.needed}${escape(c.unit)} · ${h('手配済み')} ${c.arranged}${escape(c.unit)}</small></span><strong>${h('不足')} ${Math.max(0,c.needed-c.arranged)}${escape(c.unit)}</strong>${allowed?chevron:''}</${allowed?'button':'div'}>${allowed?`<button class="text-button" data-cancel-material="${e.eventId}">${local('資材要請を取り消す','Cancel material request')}</button>`:''}</div>`;};
  const actions=(editable?editButton('assignment','担当を変更')+editButton('progress','対応状況を変更'):'')+(canWrite()&&perms.canChangePriority?editButton('priority','緊急度'):'')+(editable&&row.reports?editButton('region','地域を変更')+editButton('report','通報を訂正')+editButton('cancel-report',cancelled?'通報を復元':'通報を取り消す'):'');
  body.innerHTML=`<div id="case-feedback" role="status"></div>${editable&&!mine?`<p class="case-caution">${h('あなたはこの案件の担当ではありません')}</p>`:''}${row.reports?`<p class="registered-place">${h('記録者')}：${escape(reportAuthor(row))}</p>`:''}${row.description?`<p class="case-description">${escape(row.description)}</p>`:''}${row.reports?`<p class="report-area">${local('地域','Area')}：${escape(reportRegions(row))}</p>`:''}<dl class="case-facts"><div><dt>${h('担当')}</dt><dd>${escape(assignees(row))}</dd></div><div><dt>${h('対応状況')}</dt><dd>${escape(progress(row))}</dd></div><div><dt>${h('緊急度')}</dt><dd>${escape(priority(row))}</dd></div></dl>${row.priorities.filter(e=>e.change.reason).map(e=>`<p class="priority-reason">${escape(e.change.reason)}</p>`).join('')}${actions?`<div class="case-secondary-actions">${actions}</div>`:''}<h3 class="materials-heading">${h('必要資材')}</h3><div>${row.materials.map(materialRow).join('')||`<p class="muted">${h('まだ記録はありません。')}</p>`}</div>${editable&&!cancelled?`<button class="text-button add-material">${h('資材を要請する')}</button>`:''}${(row.cancelledMaterials||[]).length?`<details><summary>${local('取り消した資材要請','Cancelled material requests')}</summary>${row.cancelledMaterials.map(e=>`<p>${escape(e.change.name)} · ${escape(e.change.cancelReason)}</p>${canWrite()&&materialPermissions(e).canEdit&&!cancelled?`<button class="text-button" data-cancel-material="${e.eventId}">${local('資材要請を復元','Restore material request')}</button>`:''}`).join('')}</details>`:''}<details class="case-history"><summary>${h('対応の記録')} ${entries.length}</summary>${entries.map(e=>`<div class="event"><strong>${h(({AVAILABILITY:'活動状況',REPORT:'通報内容',PROGRESS:'対応状況',PRIORITY:'緊急度',ASSIGNMENT:'担当',MATERIAL:'必要資材'})[e.change.type]||e.change.type)}</strong><p>${escape(e.change.cancelled?(e.change.type==='MATERIAL'?local('資材要請を取り消す','Cancel material request'):t('通報を取り消す'))+'：'+e.change.cancelReason:e.change.type==='REPORT'&&e.replaces.some(id=>history.find(p=>p.eventId===id)?.change.cancelled)?t('通報を復元')+'：'+e.change.description:e.change.result||e.change.reason||e.change.description||e.change.name||'')}</p><small>${escape(memberFor(snapshot().roster,e.actorDeviceId).activityName)} · ${escape(date(e.operatedAt))}</small></div>`).join('')}</details>`;
  for(const b of body.querySelectorAll('[data-edit]')){b.disabled=cancelled&&!['cancel-report','report'].includes(b.dataset.edit);b.onclick=()=>edit(b.dataset.edit);}
  for(const b of body.querySelectorAll('[data-material]'))b.onclick=()=>edit('material',row.materials.find(e=>e.eventId===b.dataset.material));
  for(const b of body.querySelectorAll('[data-cancel-material]'))b.onclick=()=>edit('cancel-material',[...row.materials,...row.cancelledMaterials||[]].find(e=>e.eventId===b.dataset.cancelMaterial));
  const add=body.querySelector('.add-material');if(add)add.onclick=()=>edit('material');
  caseSheet.querySelector('.sheet-footer')?.remove();
  if(editable&&!cancelled){const footer=document.createElement('div');footer.className='sheet-footer';footer.innerHTML=`<button class="start-response">${h(mine?'担当を解除':'対応を始める')}</button>`;caseSheet.append(footer);footer.querySelector('button').onclick=()=>save(async()=>{
   if(mine)await append({type:'ASSIGNMENT',target,memberId:member().memberId,active:false});else {const s=snapshot();await getStore().startWork(s.group,s.incidentId,s.id,target,Date.now());}
  },body.querySelector('#case-feedback'));}
  if(open&&!caseSheet.open)caseSheet.show();
 }
 function parents(change){const s=snapshot();const candidate={groupId:s.group,incidentId:change.type==='AVAILABILITY'?null:s.incidentId,actorDeviceId:s.id,change};return heads.filter(e=>responseKey(s.roster,e)===responseKey(s.roster,candidate)).map(e=>e.eventId);}
 async function append(change,eventId=crypto.randomUUID(),replaces=parents(change)){const s=snapshot();return getStore().appendResponse({groupId:s.group,incidentId:change.type==='AVAILABILITY'?null:s.incidentId,eventId,actorDeviceId:s.id,operatedAt:Date.now(),change,replaces});}
 async function save(work,feedback){if(busy)return;const editorForm=editor.open&&editor.contains(feedback)?editor.querySelector('form'):null;busy=true;feedback.className='feedback';feedback.textContent=t('保存しています…');if(editorForm)for(const b of editor.querySelectorAll('button'))b.disabled=true;
  try{await work();await changed();await render();feedback.textContent=t('保存しました');if(editorForm&&editorForm===editor.querySelector('form')&&editor.open)editor.close();if(caseSheet.open){showCase(false);$('case-feedback').className='feedback';$('case-feedback').textContent=t('保存しました');}}
  catch(error){feedback.className='feedback error';feedback.textContent=error.message;}
  finally{busy=false;for(const b of editor.querySelectorAll('button'))b.disabled=false;}
 }
 const field=(key,label,value='',options={})=>`<label>${h(label)}${options.multiline?`<textarea name="${key}" maxlength="${options.max||500}" ${options.required?'required':''}>${escape(value)}</textarea>`:`<input name="${key}" value="${escape(value)}" type="${options.number?'number':'text'}" ${options.number?'step="any"':''} ${options.min!==undefined?`min="${options.min}"`:''} ${options.required?'required':''} maxlength="${options.max||240}">`}</label>`;
 function edit(kind,record,picked=null){const row=currentCase(),s=snapshot(),formTarget=target;const perms=casePermissions(row);if(!canWrite()||kind==='priority'&&!perms.canChangePriority||['assignment','progress','report','region','cancel-report'].includes(kind)&&!perms.canEdit||['material','cancel-material'].includes(kind)&&!(record?materialPermissions(record).canEdit:perms.canEdit))return;let html='',title='',onSubmit;
  if(kind==='availability'){const replaces=parents({type:'AVAILABILITY'});title='活動状況';html=Object.entries(availabilityLabels).map(([v,k])=>`<label class="choice-row"><input type="radio" name="state" value="${v}" ${heads.some(e=>e.change.type==='AVAILABILITY'&&e.change.state===v&&memberFor(s.roster,e.actorDeviceId).memberId===member()?.memberId)?'checked':''} required>${h(k)}</label>`).join('');onSubmit=d=>append({type:'AVAILABILITY',state:d.get('state')},crypto.randomUUID(),replaces);}
  if(kind==='progress'){const replaces=row.progress.map(e=>e.eventId);title='対応状況を変更';html=(row.progress.length>1?`<p class="notice">${h('同時に更新された記録があります。保存する対応状況を選んでください。')}</p>`:'')+`<label>${h('対応状況')}<select name="status" required>${Object.entries(progressLabels).map(([v,k])=>`<option value="${v}" ${(row.progress.length===0&&v==='UNASSIGNED'||row.progress.length===1&&row.progress[0].change.status===v)?'selected':''}>${h(k)}</option>`).join('')}</select></label><p class="muted">${local('「対応予定」は、引き受け済み・未着手の状態です。','Acknowledged means accepted, but work has not started.')}</p>`+field('result','対応結果',row.progress.map(e=>e.change.result).join(' / '),{multiline:true});onSubmit=d=>append({type:'PROGRESS',target:formTarget,status:d.get('status'),result:d.get('result').trim()},crypto.randomUUID(),replaces);}
  if(kind==='priority'){const replaces=row.priorities.map(e=>e.eventId);title='緊急度';html=Object.entries(priorityLabels).map(([v,k])=>`<label class="choice-row"><input type="radio" name="urgency" value="${v}" required>${h(k)}</label>`).join('')+field('reason','変更理由','',{multiline:true});onSubmit=d=>append({type:'PRIORITY',target:formTarget,urgency:d.get('urgency'),reason:d.get('reason').trim()},crypto.randomUUID(),replaces);}
  if(kind==='assignment'){title='担当を変更';const members=coordinator()?s.roster.members:[member()],replacesByMember=new Map(members.map(m=>[m.memberId,parents({type:'ASSIGNMENT',target:formTarget,memberId:m.memberId})]));html=members.map(m=>`<label class="choice-row"><input type="checkbox" name="assigned" value="${m.memberId}" ${row.assignments.some(e=>e.change.memberId===m.memberId)?'checked':''}>${escape(m.activityName)}</label>`).join('');onSubmit=async d=>{for(const m of members){const active=d.getAll('assigned').includes(m.memberId);if(active!==row.assignments.some(e=>e.change.memberId===m.memberId))await append({type:'ASSIGNMENT',target:formTarget,memberId:m.memberId,active},crypto.randomUUID(),replacesByMember.get(m.memberId));}};}
  if(kind==='material'){title=record?'必要資材':'資材を要請';const c=record?.change;html=field('name','品名',c?.name,{required:true})+field('unit','単位',c?.unit,{required:true})+`<div class="columns">${field('needed','必要数',c?.needed??0,{number:true,min:0,required:true})}${field('arranged','手配済み数',c?.arranged??0,{number:true,min:0,required:true})}</div>`;onSubmit=d=>{const eventId=crypto.randomUUID();return append({type:'MATERIAL',target:formTarget,requestId:c?.requestId||eventId,name:d.get('name').trim(),unit:d.get('unit').trim(),needed:Number(d.get('needed')),arranged:Number(d.get('arranged')),...(c?.cancelled?{cancelled:true,cancelReason:c.cancelReason}:{})},eventId,record?[record.eventId]:[]);};}
  if(kind==='cancel-material'){const c=record.change;title=c.cancelled?'資材要請を復元':'資材要請を取り消す';html=c.cancelled?`<p>${escape(c.cancelReason||'')}</p>`:field('cancelReason','取消理由','',{required:true,multiline:true});onSubmit=d=>append({...c,cancelled:!c.cancelled,cancelReason:c.cancelled?'':d.get('cancelReason').trim()},crypto.randomUUID(),[record.eventId]);}
  if(kind==='cancel-report'){const replaces=row.reports.map(e=>e.eventId);title=row.reports.every(e=>e.change.cancelled)?'通報を復元':'通報を取り消す';const c=reportEditSources(row)[0].change;html=title==='通報を取り消す'?field('cancelReason','取消理由','',{required:true,multiline:true}):`<p>${escape(c.cancelReason||'')}</p>`;onSubmit=d=>append({...c,cancelled:title==='通報を取り消す',cancelReason:title==='通報を取り消す'?d.get('cancelReason').trim():''},crypto.randomUUID(),replaces);}
  if(['report','new-report','region'].includes(kind)){
   title=kind==='region'?'地域を変更':kind==='report'?'通報を訂正':'通報を追加';const sources=kind==='new-report'?[]:reportEditSources(row);const c=sources[0]?.change||picked;
   const revision=sources.length>1?`<label>${h('訂正する記録')}<select name="revision" required><option value="">${h('記録を選んでください')}</option>${sources.map(e=>`<option value="${e.eventId}">${escape(date(e.operatedAt))} — ${escape(e.change.description.slice(0,80))}</option>`).join('')}</select></label><p class="notice">${h('同時に更新された記録があります。選んだ記録だけ訂正します。')}</p>`:'';
   const region=`<label>${local('地域','Area')}<select name="region">${regionOptions(c?.regionIds||[])}</select></label>`;
   html=revision+(kind==='region'?region:field('category','種類（自由入力）',c?.category||'道路閉塞',{required:true})+field('description','内容',c?.description,{multiline:true,required:true})+region+`<input type="hidden" name="latitude" value="${escape(c?.latitude??'')}"><input type="hidden" name="longitude" value="${escape(c?.longitude??'')}"><div class="report-location"><span id="report-location-label"></span><button type="button" id="report-pick-location" class="text-button">${local('地図で場所を選ぶ','Choose location on map')}</button></div>`);
   onSubmit=d=>{const source=sources.length>1?sources.find(e=>e.eventId===d.get('revision')):sources[0];if(sources.length&&!source)throw Error(t('記録を選んでください'));const original=source?.change,eventId=crypto.randomUUID(),regionIds=d.get('region')?[d.get('region')]:[];
    if(kind==='region')return append({...original,regionIds},eventId,[source.eventId]);
    if(!d.get('latitude')||!d.get('longitude'))throw Error(local('地図で通報の場所を選んでください。','Choose the incident location on the map.'));
    return append({type:'REPORT',reportId:original?.reportId||eventId,category:d.get('category').trim(),description:d.get('description').trim(),latitude:Number(d.get('latitude')),longitude:Number(d.get('longitude')),regionIds,cancelled:original?.cancelled||false,cancelReason:original?.cancelReason||''},eventId,source?[source.eventId]:[]);
   };
  }
  editor.querySelector('h2').textContent=t(title);editor.querySelector('.sheet-body').innerHTML=`<form id="response-edit-form">${!["availability","new-report"].includes(kind)&&row&&!row.assignments.some(e=>e.change.memberId===member()?.memberId)?`<p class="case-caution">${h('あなたはこの案件の担当ではありません')}</p>`:''}${html}<p class="feedback" role="status"></p></form>`;editor.querySelector('.sheet-footer')?.remove();const footer=document.createElement('div');footer.className='sheet-footer';footer.innerHTML=`<button type="submit" form="response-edit-form">${kind==='new-report'||kind==='report'?local('通報を保存','Save report'):h('保存する')}</button>`;editor.append(footer);
  if(['report','region'].includes(kind)&&editor.querySelector('[name="revision"]'))editor.querySelector('[name="revision"]').onchange=e=>{const c=row.reports.find(r=>r.eventId===e.target.value)?.change;if(c){for(const key of ['category','description','latitude','longitude']){const field=editor.querySelector(`[name="${key}"]`);if(field)field.value=c[key];}editor.querySelector('[name="region"]').innerHTML=regionOptions(c.regionIds||[]);updateLocationLabel();}};

  const form=editor.querySelector('form');
  const baseline=JSON.stringify((row?.heads||[]).concat(row?.reports||[]).map(e=>e.eventId).sort());
  const draft=bindFormDraft(form,[s.id,s.group,s.incidentId,kind,kind==='new-report'?null:formTarget,record?.change?.requestId],baseline,()=>{
   const review=document.createElement('label');review.className='choice-row draft-review';
   const check=document.createElement('input');check.type='checkbox';check.required=true;
   review.append(check,language==='en'?'The shared record changed. Review your draft before saving.':'共有された記録が更新されています。入力内容を確認してから保存してください。');form.prepend(review);
  });
  function updateLocationLabel(){const label=form.querySelector('#report-location-label');if(label)label.textContent=form.elements.latitude.value&&form.elements.longitude.value?local('地図で選択済み','Location selected on map'):local('場所を選んでください','Choose a location');}
  if(picked&&form.elements.latitude){form.elements.latitude.value=picked.latitude;form.elements.longitude.value=picked.longitude;if(s.venue?.scenario&&picked.latitude>35&&picked.latitude<35.04&&picked.longitude>135.75&&picked.longitude<135.78)form.elements.region.value=demoCommunityFor(picked.longitude,s.venue.scenario.communityLongitudeCuts);draft.capture();}
  updateLocationLabel();
  const pick=form.querySelector('#report-pick-location');if(pick)pick.onclick=()=>{draft.capture();editor.close();caseSheet.close();tab('buildings');map.pickLocation(point=>{target=formTarget;edit(kind,record,point);},{language});};
  form.onsubmit=e=>{e.preventDefault();const d=new FormData(form);save(async()=>{await onSubmit(d);draft.clear();},form.querySelector('.feedback'));};
  if(!editor.open)editor.show();
 }
 let communitySignature="";
 function communityInput(){const s=snapshot(),v=s.venue;const fictional=new Set((v?.scenario?.people||[]).map(p=>p.deviceId));
  const communities=v?.scenario?[...v.scenario.communities,...(v.positionMode==='ACTUAL'?[{id:'venue',nameJa:'会場参加者',nameEn:'Venue participants'}]:[])]:[{id:'group',nameJa:groupName(),nameEn:groupName()}];
  const people=s.records.map(p=>({id:p.originId,name:p.name,communityId:personCommunity(s,p),latitude:p.homeLatitude??p.latitude,longitude:p.homeLongitude??p.longitude,status:personPresentation(p,s.events,s.view,s.id,Date.now(),null,s.roster).status,fictional:fictional.has(p.originId),buildingId:(v?.placements.find(x=>x.deviceId===p.originId)?.home||v?.scenario?.people.find(x=>x.deviceId===p.originId)?.home)?.buildingId||null}));
  const aggregateCases=[...people.filter(p=>p.status==='SOS').map(p=>({target:{kind:'PERSON',id:p.id},statusIcon:'SOS'})),...cases.filter(c=>c.target.kind==='REPORT')];
  const input={cases,aggregateCases,visiblePersonIds:visiblePeople(s).filter(p=>personPresentation(p,s.events,s.view,s.id,Date.now(),null,s.roster).disclosed).map(p=>p.originId),groupId:s.group,groupName:groupName(),incidentId:s.incidentId,demo:!!v,communities,people,heads:heads.filter(e=>e.groupId===s.group&&e.incidentId===s.incidentId),sourceIds:[...s.events.map(e=>e.eventId),...Object.values(s.view?.answers||{}).map(e=>e.eventId)]};
  return input;
 }
 function showSummary(force=true,communityId=summary.dataset.communityId||null){
  const input=communityInput();summary.dataset.incidentId=input.incidentId||'';summary.dataset.communityId=communityId||'';
  const signature=JSON.stringify([language,input,communityId]);if(!force&&signature===communitySignature)return;communitySignature=signature;
  showCommunityScreen({dialog:summary,input,language,store:getStore(),editable:canWrite()&&canTriage(member()),communityId,openCases:(category,communityId)=>{summary.close();areaFilter={category,communityId};filter='all';tab('response');renderResponse();},saveInjury:async(id,state)=>{const event=await append({type:'INJURY',target:{kind:'PERSON',id},state});await changed();return event;}}).catch(e=>{$('message').textContent=e.message;});
 }

 activity.onclick=()=>edit('availability');const beginReport=()=>{if(!canWrite())return;tab('buildings');map.pickLocation(position=>edit('new-report',null,position),{language});};$('report-add').onclick=()=>{if(canWrite()){target=null;edit('new-report');}};
 const mapLegend=document.querySelector('.map-legend');document.addEventListener('pointerdown',event=>{if(mapLegend.open&&!mapLegend.contains(event.target))mapLegend.open=false;},{capture:true});
 const legend=document.createElement('div');legend.className='people-legend';legend.innerHTML=['SOS','UNREPORTED','SAFE','RESCUED'].map(status=>`<span>${icon(status)}<span data-legend-status="${status}">${statusName(status)}</span></span>`).join('');document.querySelector('.map-legend summary').after(legend);const legendTitle=document.createElement('h3');legendTitle.className='legend-section-title';legendTitle.textContent=t('安否');legend.before(legendTitle);const observationLegend=document.createElement('div');observationLegend.className='map-observation-legend';legend.after(observationLegend);
 const mapAdd=document.createElement('button');mapAdd.id='map-report-add';mapAdd.className='map-report-add';mapAdd.type='button';mapAdd.textContent=t('通報を追加');mapAdd.onclick=beginReport;document.querySelector('.map-stage').append(mapAdd);
 $('map-show-safe').onchange=()=>renderMap();
 $('map-unknown').onclick=()=>{unknownOnly=true;$('filter').value=$('map-show-safe').checked?'ALL':'NONSAFE';tab('residents');renderPeople();};
 function renderMap(){legendTitle.textContent=t('安否');observationLegend.innerHTML=`<span><i class="registered-building-key"></i> ${local('住民登録のある建物','Buildings with registered residents')}</span>`+`<span>${observationIcon('impact')} ${h('衝撃を検知')}</span><span>${observationIcon('enclosed')} ${h('閉所を検知')}</span>`;const s=snapshot(),input=communityInput(),other=communityMapOverview(input,s.id),otherIds=new Set(other.map(r=>r.id)),otherPeople=new Set(input.people.filter(p=>otherIds.has(p.communityId)).map(p=>p.id));map.setRegisteredBuildings(placeBuildings(s.placeRows||[]).filter(b=>linkedBuildingResidents({registeredId:b.buildingId},s.placeRows||[],s.records).length).map(b=>b.locationReference?.plateauId).filter(Boolean));map.setCommunities(s.venue?.scenario?other:[],id=>showTown(id));for(const label of legend.querySelectorAll('[data-legend-status]'))label.textContent=t(statusLabels[label.dataset.legendStatus]);map.setShowSafe($('map-show-safe').checked);map.setPeople(visiblePeople(s).map(p=>{const position=positionFor(p.originId),v=personPresentation(p,s.events,s.view,s.id,Date.now(),position,s.roster);return {id:p.originId,status:v.status,label:v.name===p.name?p.name:t(v.name),position,impactAt:v.showObservations?p.impactAt:null,enclosedAt:v.showObservations?p.enclosedAt:null};}),id=>{selectPerson(id);renderPerson(id,true);});map.setReports(cases.flatMap(r=>(r.reports||[]).filter(e=>!e.change.cancelled).map(e=>({...e.change,id:r.target.id,label:t(r.title),urgency:urgencyFor(r)}))),id=>{target={kind:'REPORT',id};showCase();});$('map-unknown-count').textContent=String(visiblePeople(s).filter(p=>!positionFor(p.originId)&&($('map-show-safe').checked||!['SAFE','RESCUED'].includes(personPresentation(p,s.events,s.view,s.id,Date.now(),null,s.roster).status))).length);mapAdd.disabled=!canWrite();mapAdd.textContent=t('通報を追加');}

 $('response-export').onclick=async()=>{try{const s=snapshot(),csv=await getStore().exportCsv(s.group,s.incidentId,{}, {language,allowedResidentIds:cases.filter(c=>c.target.kind==='PERSON').map(c=>c.target.id)});const blob=new Blob([csv],{type:'text/csv;charset=utf-8'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='Help-Net-'+s.incidentId+'.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){$('message').textContent=e.message;}};
 const safetyForm=document.createElement('form');safetyForm.id='own-status-form';safetyForm.innerHTML='<label id="memo-label">SOSメモ<textarea id="own-memo" maxlength="500" rows="3"></textarea></label><p class="memo-count"></p><button id="memo-save" type="button" class="secondary">メモだけ保存する</button><button id="status-update" type="submit">安否を更新する</button><p id="own-status-feedback" class="feedback" role="status"></p>';document.querySelector('.safety-actions').after(safetyForm);
 let safetyChoice=null;
 for(const [button,value]of [['safe','SAFE'],['sos','SOS']])$(button).onclick=()=>{safetyChoice=value;renderSafety();};
 $('own-memo').oninput=()=>{memoDirty=true;safetyForm.querySelector('.memo-count').textContent=`${$('own-memo').value.length} / 500`;};
 async function saveMemo(answer){if(!answer||answer.safety!=='SOS')throw Error(t('災害・訓練を選択してください。'));const s=snapshot(),old=memos.find(m=>m.answerId===answer.eventId);await getStore().mergeMemo({groupId:s.group,incidentId:s.incidentId,authorId:s.id,answerId:answer.eventId,revision:(old?.revision||0)+1,updatedAt:Date.now(),text:$('own-memo').value.trim()});memoDirty=false;}
 $('memo-save').onclick=()=>save(()=>saveMemo(snapshot().view?.answers[snapshot().id]),$('own-status-feedback'));
 safetyForm.onsubmit=e=>{e.preventDefault();save(async()=>{const s=snapshot();const value=safetyChoice||s.view?.answers[s.id]?.safety;if(!value)throw Error(t('情報を登録してください。'));const answer=await appendScopedIncident(getStore(),{groupId:s.group,incidentId:s.incidentId,authorId:s.id},'ANSWER',{safety:value});if(value==='SOS')await saveMemo(answer);safetyChoice=null;await changed();tab('buildings');},$('own-status-feedback'));};
 function renderSafety(){const s=snapshot(),own=s.records.find(p=>p.originId===s.id)||s.roster?.members?.filter(m=>m.deviceIds.includes(s.id)).map(m=>({name:m.activityName}))[0],answer=s.view?.answers[s.id],choice=safetyChoice||answer?.safety;const memo=memos.find(m=>m.answerId===answer?.eventId);setHtml($('own'),own?`<strong>${escape(own.name)}</strong><p>${s.view?escape(date(s.view.start.createdAt)):h('災害・訓練を選択してください。')}</p>`:h('情報を登録してください。'));
  if(!memoDirty)$('own-memo').value=memo?.text||'';
  $('memo-label').firstChild.textContent=t('SOSメモ');$('memo-save').textContent=t('メモだけ保存する');$('status-update').textContent=t('安否を更新する');
  $('memo-label').hidden=choice!=='SOS';$('memo-save').hidden=choice!=='SOS';$('memo-save').disabled=!own||!s.view||s.view.closed||answer?.safety!=='SOS';$('status-update').disabled=!own||!s.view||s.view.closed||!choice;
  setHtml($('safe'),icon('SAFE')+`<span>${language==='en'?'I’m safe':'無事'}</span>`);setHtml($('sos'),icon('SOS')+`<span>${language==='en'?'I need help':'SOS'}</span>`);$('safe').setAttribute('aria-pressed',String(choice==='SAFE'));$('sos').setAttribute('aria-pressed',String(choice==='SOS'));
  safetyForm.querySelector('.memo-count').hidden=choice!=='SOS';safetyForm.querySelector('.memo-count').textContent=`${$('own-memo').value.length} / 500`;
 }
 applyLanguage();setInterval(()=>{if(!getStore())return;renderPeople();if(personSheet.open)renderPerson(personSheet.dataset.personId);if(incidentSheet.open)renderIncidentDetails();if(buildingSheet.open&&selectedMapBuilding)renderMapBuilding();},30000);
 return {render,renderResponse,renderPerson,buildingResidents:()=>{const s=snapshot();return visiblePeople(s).flatMap(p=>{const v=personPresentation(p,s.events,s.view,s.id,Date.now(),positionFor(p.originId),s.roster);return v.disclosed&&v.showHome?[{...p,name:v.name===p.name?p.name:t(v.name),safety:v.status}]:[];});},closeSheets:()=>{for(const d of [personSheet,caseSheet,editor,summary,settingsSheet,careEditor,incidentSheet,buildingSheet,townSheet,townDetails])d.close();}};
}

