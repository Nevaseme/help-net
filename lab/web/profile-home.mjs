import {translateUI as t,bindStaticUiLanguage,observeUiLanguage} from './web1617-english.mjs';
import {mapMunicipality} from './municipalities.mjs';
import {clearableSearch,dismissKeyboard} from './search-controls.mjs';
import {pickBuilding} from './building-picker.mjs';
import {selectAddressCandidate} from './address-selection.mjs';
import {geocodeCandidates} from './city-addresses.mjs';
import {demoAddressLabel} from './demo-address-display.mjs';
import {setText} from './dom-state.mjs';

/** Keep coordinates in the stored profile, while editing them with a map pin. */
export function attachProfileHome({store,getGroup,onChange}){
 const $=id=>document.getElementById(id),host=document.createElement('div');host.className='profile-home';
 host.innerHTML='<h3>住居の位置</h3><label>住所・建物名を検索<input id="home-address" type="search" placeholder="例：京都市左京区…" autocomplete="street-address"></label><div class="actions"><button id="home-search" type="button" class="secondary">住所を検索</button><button id="home-pick" type="button" class="text-button">地図で選ぶ</button></div><button id="home-use-current" type="button" class="text-button">現在地から選ぶ</button><p id="home-state" class="muted" role="status"></p>';
 $('lat').closest('.columns').before(host);$('lat').closest('.columns').hidden=true;
 for(const key of ['lat','lon']){$(key).required=false;}
 let working=false,pickedMetadata={},confirmedAddress='';const refreshSearch=clearableSearch($('home-address'));
 const summary=document.createElement('p');summary.className='home-selected';host.append(summary);$('profile-building').textContent='登録済みの建物・部屋から選ぶ';host.append($('profile-building'));
 const selected=()=>$('lat').value!==''&&$('lon').value!==''&&Number.isFinite(Number($('lat').value))&&Number.isFinite(Number($('lon').value));
 function render(){const english=document.documentElement.lang==='en';$('home-address').placeholder=english?'Example: Sakyo-ku, Kyoto':'例：京都市左京区…';const label=demoAddressLabel(confirmedAddress,english);if([confirmedAddress,demoAddressLabel(confirmedAddress,true)].includes($('home-address').value))$('home-address').value=label;summary.textContent=confirmedAddress?t('選択した住居：')+label:'';summary.hidden=!confirmedAddress;if(!working)setText($('home-state'),selected()?t('住居の位置を選択済みです。変更するときは地図で選び直せます。'):t('住所を検索するか、地図から住んでいる場所を選んでください。'));}
 async function choose(mode){
  if(working)return;dismissKeyboard();working=true;for(const b of host.querySelectorAll('button'))b.disabled=true;
  try{
   const city=await mapMunicipality(store,getGroup());
   let point=selected()?{latitude:Number($('lat').value),longitude:Number($('lon').value)}:city?.code==='13103'?{latitude:35.6665,longitude:139.7495}:city?.code==='22205'?{latitude:35.095,longitude:139.07}:{latitude:35.0145,longitude:135.7625};
   if(mode==='search'){
    const query=$('home-address').value.trim();if(!query)throw Error(t('住所や建物名を入力してください。'));setText($('home-state'),t('住所を探しています…'));
    const found=await selectAddressCandidate(query,city?.code);if(!found)throw Error(t('場所が見つかりませんでした。住所を詳しく入力するか、地図で選んでください。'));point=found;
   }else if(mode==='current'){
    if(!navigator.geolocation)throw Error(t('この端末では現在地を取得できません。住所検索か地図から選んでください。'));
    setText($('home-state'),t('現在地を確認しています…'));const position=await new Promise((resolve,reject)=>navigator.geolocation.getCurrentPosition(resolve,()=>reject(Error(t('現在地を取得できませんでした。住所検索か地図から選んでください。'))),{timeout:10000,maximumAge:60000}));point=position.coords;
   }
   const picked=await pickBuilding({...point,code:point.municipalityCode||city?.code||'26100',query:mode==='search'?$('home-address').value.trim():'',initialCandidate:mode==='search'?point:null,initialAddress:mode==='search'?'':$('home-address').value});
   if(picked){if(Math.abs(Number($('lat').value)-picked.latitude)>1e-6||Math.abs(Number($('lon').value)-picked.longitude)>1e-6||pickedMetadata.plateauId!==picked.plateauId){$('floor').value='';$('room').value='';}$('lat').value=picked.latitude;$('lon').value=picked.longitude;pickedMetadata={plateauId:picked.plateauId,...(picked.searchReference?{searchReference:picked.searchReference}:{})};$('home-address').value=picked.address;confirmedAddress=picked.address;refreshSearch();onChange?.();}
   working=false;render();
  }catch(e){setText($('home-state'),e.message);}finally{working=false;for(const b of host.querySelectorAll('button'))b.disabled=false;}
 }
 $('home-search').onclick=()=>choose('search');$('home-pick').onclick=()=>choose('map');$('home-use-current').onclick=()=>choose('current');
 $('home-address').onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();choose('search');}};
 async function warm(){const city=await mapMunicipality(store,getGroup());if(city?.code)await geocodeCandidates(' ',city.code);}for(const event of ['municipality-change','group-change','venue-map-change'])window.addEventListener(event,()=>warm().catch(()=>{}));warm().catch(()=>{});
 bindStaticUiLanguage(host);observeUiLanguage(render);render();return {render,selected,metadata:()=>pickedMetadata,address:()=>confirmedAddress,setHome(home){pickedMetadata=home?{plateauId:home.plateauId,...(home.searchReference?{searchReference:home.searchReference}:{})}:{};confirmedAddress=home?.address||'';$('home-address').value=confirmedAddress;refreshSearch();render();}};
}
