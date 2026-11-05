import {translateUI as t,bindStaticUiLanguage,observeUiLanguage} from './web1617-english.mjs';
import {mapMunicipality} from './municipalities.mjs';
import {clearableSearch,dismissKeyboard} from './search-controls.mjs';
import {selectAddressCandidate} from './address-selection.mjs';
import {demoHomeFromPoint} from './venue-demo.mjs';
import {searchCityLocations,normalizeAddress} from './city-addresses.mjs';
import {pickBuilding} from './building-picker.mjs';
import {BUILDING_ACTIONS,placeBuildings,placeUnits,homeFor,validHome,buildingCare,currentBuildingCare} from './places.mjs';

export function createPlaceUi({$,esc,time,store,identity,snapshot,saves,changed,selectResident,visibleResidents=()=>snapshot().residents}){
 let selected=null,editing=null,editingUnit=null,context='',currentGroup='',locationReference=null,searchCity=null,confirmed=false,lookupGeneration=0,searchCandidate=null;
 const filter=document.createElement('label');filter.textContent='登録済みの建物を絞り込む';const searchInput=document.createElement('input');searchInput.id='building-search';searchInput.type='search';filter.append(searchInput);
 const searchState=document.createElement('p');searchState.id='building-search-state';searchState.setAttribute('role','status');const searchResults=document.createElement('div');searchResults.id='building-search-results';searchResults.className='address-results';$('building-list').before(filter,searchState,searchResults);let searchTimer,searchGeneration=0;
 searchInput.oninput=()=>render();clearableSearch(searchInput);searchInput.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();dismissKeyboard();}};
 const key=(kind,target='')=>{const s=snapshot();return `place:${s.group}:${kind==='CARE'?s.incidentId:''}:${kind}:${target}`;};
 const resetCare=()=>{for(const field of ['building-scope','building-unresolved','building-note'])$(field).value='';$('building-action').value='CHECKING';$('building-correction').value='';};
 const homeLabel=(rows,person)=>{const h=homeFor(rows,person);if(!h)return t('建物・住戸は未登録');if(!h.buildingId)return t('関連付けなし');if(!validHome(rows,person))return t('建物・住戸の詳細を受信待ち');return placeBuildings(rows).find(b=>b.buildingId===h.buildingId).label+' / '+(placeUnits(rows,h.buildingId).find(u=>u.unitId===h.unitId)?.label||t('住戸未指定'));};
 function feedback(){
  const s=snapshot(),own=s.residents.some(r=>r.originId===identity()),busy=saves.busy;
  for(const el of $('buildings').querySelectorAll('button,input,textarea,select'))el.disabled=busy;
  $('building-open').disabled=!own||busy;$('building-unlink').disabled=!own||busy||!homeFor(s.rows,identity())?.buildingId;
  for(const el of $('building-form').querySelectorAll('button,input'))el.disabled=!own||busy;
  for(const el of $('building-care-form').querySelectorAll('button,input,textarea,select'))el.disabled=!own||!s.view||s.view.closed||busy;
  for(const id of ['home-save','unit-save'])$(id).disabled=!own||busy;
  for(const [id,k]of [['building-save-feedback',key('BUILDING',editing||'')],['home-feedback',key('HOME')],['home-detail-feedback',key('HOME')],['unit-feedback',key('UNIT',selected||'')],['building-care-feedback',key('CARE',selected||'')]]){const f=saves.get(k);$(id).textContent=f.text;$(id).dataset.status=f.status;$(id).setAttribute('aria-busy',String(f.status==='pending'));}
 }
 function render(){
  const s=snapshot(),nextContext=s.group+'|'+s.incidentId;
  if(currentGroup!==s.group){searchGeneration++;clearTimeout(searchTimer);searchInput.value='';searchResults.replaceChildren();searchState.textContent='';selected=null;editing=null;editingUnit=null;$('unit-name').value='';$('building-form').hidden=true;currentGroup=s.group;}
  if(context!==nextContext){resetCare();context=nextContext;}
  const buildings=placeBuildings(s.rows);$('home-current').textContent=t('自分の登録：')+homeLabel(s.rows,identity());
  $('building-list').innerHTML=buildings.length?buildings.filter(b=>normalizeAddress(b.label+(b.locationReference?.address||'')).includes(normalizeAddress(searchInput.value))).map(b=>{const members=visibleResidents().filter(r=>validHome(s.rows,r.originId)?.buildingId===b.buildingId),care=currentBuildingCare(buildingCare(s.rows,b.buildingId,s.incidentId));return `<button class="resident" data-building="${b.buildingId}"><strong>${esc(b.label)}</strong>${b.locationReference?`<small>${esc(b.locationReference.address)}</small>`:''}<span class="building-status">${esc(care.map(e=>t(BUILDING_ACTIONS[e.action])).join(' / ')||t('建物の対応記録なし'))}</span><small>${document.documentElement.lang==='en'?`Registered residents ${members.length} · SOS ${members.filter(r=>r.safety==='SOS').length}`:`登録住民 ${members.length}人・SOS ${members.filter(r=>r.safety==='SOS').length}人`}</small><small>${esc(t('対応範囲・未確認事項は詳細で確認'))}</small></button>`;}).join(''):`<p>${esc(t('登録済みの建物はありません。住所・地図から追加できます。'))}</p>`;
  for(const button of $('building-list').querySelectorAll('[data-building]'))button.onclick=()=>{if(selected!==button.dataset.building)resetCare();selected=button.dataset.building;editingUnit=null;$('unit-name').value='';render();$('building-detail').scrollIntoView({block:'start'});};
  const b=buildings.find(x=>x.buildingId===selected);$('building-detail').hidden=!b;if(!b){feedback();return;}
  $('building-name').textContent=b.label;$('building-info').textContent=b.locationReference?.address||t('登録者：')+b.authorName;$('building-edit').hidden=b.authorId!==identity();
  const units=placeUnits(s.rows,selected),old=$('home-unit').value;$('home-unit').innerHTML=`<option value="">${esc(t('住戸未指定'))}</option>`+units.map(u=>`<option value="${u.unitId}">${esc(u.label)}</option>`).join('');if(units.some(u=>u.unitId===old))$('home-unit').value=old;
  $('unit-edits').innerHTML=units.filter(u=>u.authorId===identity()).map(u=>`<button type="button" class="secondary" data-edit-unit="${u.unitId}">${esc(u.label)}${esc(t(' の住戸名を訂正'))}</button>`).join('');
  for(const button of $('unit-edits').querySelectorAll('[data-edit-unit]'))button.onclick=()=>{const unit=units.find(u=>u.unitId===button.dataset.editUnit);editingUnit=unit.unitId;$('unit-name').value=unit.label;$('unit-save').textContent=t('住戸の訂正を保存');$('unit-name').focus();};
  $('unit-save').textContent=editingUnit?t('住戸の訂正を保存'):t('住戸を追加');
  const members=visibleResidents().filter(r=>validHome(s.rows,r.originId)?.buildingId===selected);
  $('building-members').innerHTML=members.length?members.map(r=>`<button class="resident" data-member="${r.originId}"><strong>${esc(r.name)}・${esc(r.safety==='SAFE'?t('無事'):r.safety==='SOS'?'SOS':r.safety==='RESCUED'?t('救助済み'):t('応答なし'))}</strong><small>${esc(homeLabel(s.rows,r.originId))}・${esc(r.floor)}・${esc(t('寝室：'))}${esc(r.room)}</small></button>`).join(''):`<p>${esc(t('関連付け済みの住民情報はありません。空き家とは判定しません。'))}</p>`;
  for(const button of $('building-members').querySelectorAll('[data-member]'))button.onclick=()=>selectResident(button.dataset.member);
  const history=buildingCare(s.rows,selected,s.incidentId).sort((a,b)=>b.createdAt-a.createdAt||a.eventId.localeCompare(b.eventId)),heads=currentBuildingCare(history),active=new Set(heads.map(e=>e.eventId));
  $('building-history').innerHTML=history.length?history.map(e=>`<div class="event"><strong>${esc(t(BUILDING_ACTIONS[e.action]))}${active.has(e.eventId)?'':t('（訂正前）')}</strong><p>${esc(t('確認範囲：'))}${esc(e.scope)}<br>${esc(t('未確認事項：'))}${esc(e.unresolved)}</p><p>${esc(e.note)}</p><small>${esc(e.authorName)} · ${esc(time(e.createdAt))}</small></div>`).join(''):`<p>${esc(t('この災害の建物対応記録はありません'))}</p>`;
  if(!saves.busy){const correction=$('building-correction').value;$('building-correction').innerHTML=`<option value="">${esc(t('新しい記録'))}</option>`+heads.filter(e=>e.authorId===identity()).map(e=>`<option value="${e.eventId}">${esc(t(BUILDING_ACTIONS[e.action]))}・${esc(time(e.createdAt))}${esc(t('を訂正'))}</option>`).join('');if(heads.some(e=>e.eventId===correction))$('building-correction').value=correction;}
  $('building-care-form').hidden=!s.view||s.view.closed||!s.residents.some(r=>r.originId===identity());feedback();
 }
 async function save(kind,data,label,onSaved=()=>{}){
  const s=snapshot(),authorId=identity(),target=kind==='CARE'||kind==='UNIT'?data.buildingId:kind==='BUILDING'?data.buildingId||'':'';
  const operationKey=key(kind,target),g=s.group,incidentId=s.incidentId;
  await saves.run(operationKey,async()=>{
   const own=await store().get(g,authorId)||s.residents.find(r=>r.originId===authorId);if(!own)throw Error(t('先に自分の情報を登録してください'));if(kind==='HOME'&&data.buildingId){const b=placeBuildings(s.rows).find(b=>b.buildingId===data.buildingId);if(!b)throw Error(t('建物が未受信です'));const room=placeUnits(s.rows,b.buildingId).find(u=>u.unitId===data.unitId)?.label||'';
    if(s.venue&&s.venue.positionMode!=='ACTUAL'){const home=demoHomeFromPoint({latitude:b.latitude,longitude:b.longitude,plateauId:b.locationReference?.plateauId,searchReference:b.locationReference?.searchReference},{},{address:b.locationReference?.address||b.label,floor:'',room,displayName:own.name});home.buildingId=b.buildingId;await store().appendDeviceRecord({groupId:g,authorId,kind:'DEMO_HOME',sessionId:s.venue.sessionId,home});}
    else await store().saveProfileWithHome({...own,latitude:b.latitude,longitude:b.longitude,floor:'',room},{...b.locationReference,address:b.locationReference?.address||b.label,buildingId:b.buildingId,unitId:data.unitId});onSaved(data);return;
   }
   if(kind==='HOME'&&s.venue&&s.venue.positionMode!=='ACTUAL')throw Error(t('訓練の住居は別の建物を選んで変更してください'));
   const eventId=crypto.randomUUID();
   const candidate={groupId:g,eventId,authorId,authorName:own.name,createdAt:Date.now(),kind,...data};
   if(kind==='BUILDING')candidate.buildingId=data.buildingId||eventId;if(kind==='UNIT')candidate.unitId=data.unitId||eventId;if(kind==='CARE')candidate.incidentId=incidentId;
   const saved=await store().appendPlace(candidate);onSaved(saved);
  },changed,label);await changed();
 }
 const run=fn=>{try{return Promise.resolve(fn()).catch(e=>{$('message').textContent=e.message;});}catch(e){$('message').textContent=e.message;}};
 function openForm(b=null){searchCandidate=null;searchCity=b?.locationReference?.plateauId?'26100':null;editing=b?.buildingId||null;const own=snapshot().residents.find(r=>r.originId===identity());$('building-label').value=b?.label||'';$('building-lat').value=b?.latitude??own?.latitude??'';$('building-lon').value=b?.longitude??own?.longitude??'';$('building-address').value=b?.locationReference?.address||'';refreshBuildingAddress();locationReference=b?.locationReference||null;confirmed=!!b;$('building-location-state').textContent=b?t('建物の位置を選択済み'):'';$('building-address-results').replaceChildren();$('building-form').hidden=false;feedback();}
 $('building-open').onclick=()=>{openForm();run(chooseOnMap);};$('building-edit').onclick=()=>openForm(placeBuildings(snapshot().rows).find(b=>b.buildingId===selected));$('building-cancel').onclick=()=>{$('building-form').hidden=true;};
 $('building-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;if(!confirmed){$('building-location-state').textContent=t('地図で建物の位置を選んでください');return;}const data={buildingId:editing,label:$('building-label').value.trim(),latitude:Number($('building-lat').value),longitude:Number($('building-lon').value),locationReference:{address:$('building-address').value.trim(),plateauId:locationReference?.plateauId??null,dataset:locationReference?.dataset??null,...(locationReference?.searchReference?{searchReference:locationReference.searchReference}:{})}};run(()=>save('BUILDING',data,'建物情報',saved=>{selected=saved.buildingId;$('building-form').hidden=true;}));};
 async function chooseOnMap(query='',candidate=null){dismissKeyboard();const group=snapshot().group,city=await mapMunicipality(store(),group);const result=await pickBuilding({latitude:candidate?.latitude??(Number($('building-lat').value)||35.0145),longitude:candidate?.longitude??(Number($('building-lon').value)||135.7625),code:candidate?.municipalityCode||searchCity||city?.code,query,initialCandidate:candidate||searchCandidate,initialAddress:$('building-address').value});if(!result||snapshot().group!==group)return;if(result.candidate){searchCity=result.candidate.municipalityCode;searchCandidate=result.candidate;if(result.candidate.precision==='poi')$('building-label').value=result.candidate.address;else $('building-address').value=result.candidate.address;}$('building-address').value=result.address;refreshBuildingAddress();$('building-lat').value=result.latitude;$('building-lon').value=result.longitude;locationReference={plateauId:result.plateauId,dataset:result.plateauId?(result.longitude>139?'plateau-tokyo-2025-toranomon':'plateau-26100-kyoto-shi-2025'):null,...(result.searchReference?{searchReference:result.searchReference}:{})};confirmed=true;$('building-location-state').textContent=t('建物の位置を選択済み');}
 $('building-pick').onclick=()=>run(chooseOnMap);
 const refreshBuildingAddress=clearableSearch($('building-address'));$('building-address').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();$('building-address-search').click();}};
 $('building-address').oninput=()=>{lookupGeneration++;$('building-address-results').replaceChildren();};
 $('building-address-search').onclick=()=>run(async()=>{dismissKeyboard();const query=$('building-address').value,c=await selectAddressCandidate(query,(await mapMunicipality(store(),snapshot().group))?.code);if(!c){$('building-location-state').textContent=t('場所が見つかりません。住所を詳しく入力してください');return;}await chooseOnMap(query,c);});

 $('unit-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;const data={buildingId:selected,unitId:editingUnit,label:$('unit-name').value.trim()};run(()=>save('UNIT',data,'住戸',()=>{editingUnit=null;$('unit-name').value='';}));};
 $('unit-cancel').onclick=()=>{editingUnit=null;$('unit-name').value='';$('unit-save').textContent=t('住戸を追加');};
 $('home-save').onclick=()=>{const data={buildingId:selected,unitId:$('home-unit').value||null};run(()=>save('HOME',data,'自分の住居'));};
 $('building-unlink').onclick=()=>run(()=>save('HOME',{buildingId:null,unitId:null},'住居の関連付け解除'));
 $('building-correction').onchange=()=>{const e=snapshot().rows.find(x=>x.eventId===$('building-correction').value);if(!e){resetCare();return;}$('building-action').value=e.action;$('building-scope').value=e.scope;$('building-unresolved').value=e.unresolved;$('building-note').value=e.note;};
 $('building-care-form').onsubmit=e=>{e.preventDefault();if(saves.busy)return;const data={buildingId:selected,action:$('building-action').value,scope:$('building-scope').value.trim(),unresolved:$('building-unresolved').value.trim(),note:$('building-note').value.trim(),supersedes:$('building-correction').value||null};run(()=>save('CARE',data,'建物の対応記録',saved=>{$('building-correction').innerHTML+=`<option value="${saved.eventId}">${esc(t('保存した記録を訂正'))}</option>`;$('building-correction').value=saved.eventId;}));};
 bindStaticUiLanguage($('property'));observeUiLanguage(render);return {render,feedback,homeLabel};
}
