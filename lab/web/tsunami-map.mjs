import {placeMapLabel} from './map-label-layout.mjs';
import {currentLocationControl} from './current-location.mjs';
import {mapBuildingFeatures} from './building-details.mjs';
import {tsunamiDepthColor} from './tsunami-map-model.mjs';

/** One map for normal browsing and exact, previously checked height results. */
export function createTsunamiMap(){
 const $=id=>document.getElementById(id);let map,home=null,initialFocus=null,result=null,layers=[],generation=0,controller=null;
 let locationControl=null,myDot=null,myAccuracy=null,lastLanguage=null,demoLocation=null;
 let registeredIds=[];
 function highlightRegisteredBuildings(){if(!vectorMap)return;for(const source of ["kyoto-risk","toranomon-risk"]){if(!vectorMap.getSource(source))continue;const id=source+"-registered",filter=["in",["get","bid"],["literal",registeredIds]];if(!vectorMap.getLayer(id))vectorMap.addLayer({id,type:"line",source,"source-layer":"buildings",minzoom:14,filter,paint:{"line-color":"#216879","line-width":2}});else vectorMap.setFilter(id,filter);}}
 let tsunamiEnabled=false,heightTimer,vectorMap,selectBuilding=()=>{},buildingRequest=0;
 let statusKey='off',statusHeight=null,loadStarted=null;
 function status(key,height=null){statusKey=key;statusHeight=height;renderStatus();}
 function renderStatus(){const messages={off:local('津波表示はオフです。','Tsunami overlay is off.'),enter:local('津波の高さを入力してください','Enter the tsunami height'),range:local('0〜35m・1m刻みで入力してください','Enter a whole number from 0 to 35m'),loading:local('計算済みの結果を読み込んでいます…','Loading saved results…'),result:local(`津波高 ${statusHeight}m の条件計算を表示しています。`,`Showing results for a ${statusHeight}m tsunami.`),error:local('津波データを読み込めませんでした。通信を確認してもう一度お試しください。','Unable to load tsunami data. Check your connection and try again.')};$('tsunami-status').textContent=messages[statusKey]+(statusKey==='loading'&&loadStarted?local(`（経過 ${Math.floor((Date.now()-loadStarted)/1000)}秒）`,` (${Math.floor((Date.now()-loadStarted)/1000)}s)`):'');}
 function refreshLanguage(){renderStatus();locationControl?.refreshLanguage();$('tsunami-map').querySelectorAll('.leaflet-control-zoom-in,.leaflet-control-zoom-out').forEach(el=>{const label=el.classList.contains('leaflet-control-zoom-in')?local('拡大','Zoom in'):local('縮小','Zoom out');el.setAttribute('aria-label',label);el.setAttribute('title',label);});document.querySelector('.tsunami-legend')?.setAttribute('aria-label',local('最大浸水深の凡例','Maximum inundation depth legend'));if(lastLanguage!==document.documentElement.lang){map?.closePopup();lastLanguage=document.documentElement.lang;}}
 let communities=[],selectCommunity=()=>{},people=[],reports=[],caseLayers=[],selectPerson=()=>{},selectReport=()=>{},pick=null,showSafe=true,caseSignature=null;
 const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const en=()=>document.documentElement.lang==='en';
 const local=(ja,english)=>en()?english:ja;
 const validPosition=p=>Number.isFinite(p?.latitude)&&Number.isFinite(p?.longitude)&&Math.abs(p.latitude)<=85&&Math.abs(p.longitude)<=180;
 function drawCases(){
  if(!map||!map._loaded)return;const signature=JSON.stringify([people,reports,communities,showSafe,en(),map.getCenter(),map.getZoom()]);if(signature===caseSignature)return;caseSignature=signature;
  for(const layer of caseLayers)map?.removeLayer(layer);caseLayers=[];if(!map)return;
  const entries=[...people.filter(p=>validPosition(p.position)&&(showSafe||!['SAFE','RESCUED'].includes(p.status))).map(p=>({kind:'person',id:p.id,label:p.label,latitude:p.position.latitude,longitude:p.position.longitude})),...reports.filter(r=>!r.cancelled&&validPosition(r)).map(r=>({...r,kind:'report'}))];
  function selectEntry(entry){if(consumePick(entry))return;const same=entries.filter(e=>Math.abs(e.latitude-entry.latitude)<1e-6&&Math.abs(e.longitude-entry.longitude)<1e-6);
   if(same.length<2){(entry.kind==='person'?selectPerson:selectReport)(entry.id);return;}
   const list=document.createElement('div');list.className='map-location-list';for(const e of same){const b=document.createElement('button');b.type='button';b.textContent=e.label;b.onclick=()=>{map.closePopup();(e.kind==='person'?selectPerson:selectReport)(e.id);};list.append(b);}L.popup().setLatLng([entry.latitude,entry.longitude]).setContent(list).openOn(map);
  }
  for(const person of people.filter(p=>validPosition(p.position)&&(showSafe||!['SAFE','RESCUED'].includes(p.status)))){
   const symbol=person.status==='SOS'?'SOS':person.status==='UNREPORTED'?'?':`<img src="/web1617-${person.status==='RESCUED'?'rescued':'safe'}.svg" alt="">`;
   const marker=L.marker([person.position.latitude,person.position.longitude],{icon:L.divIcon({className:'person-map-marker',html:`<span class="person-symbol ${escape(person.status)}">${symbol}</span>${person.impactAt?`<span class="person-impact" aria-label="${local('衝撃を検知','Impact')}"><img src="/web1617-impact-collision.svg" alt=""></span>`:''}${person.enclosedAt?`<span class="person-enclosed" aria-label="${local('閉所を検知','Enclosed space')}"><img src="/web1617-enclosed.png" alt=""></span>`:''}`,iconSize:[44,44],iconAnchor:[22,22]}),title:person.label,keyboard:true});
   marker.on('click',()=>selectEntry({kind:'person',id:person.id,...person.position}));marker.addTo(map);caseLayers.push(marker);
  }
  const occupied=[{x:map.getSize().x-66,y:0,w:66,h:120},...entries.map(p=>{const c=map.latLngToContainerPoint([p.latitude,p.longitude]);return {x:c.x-26,y:c.y-26,w:52,h:52};})];
  for(const region of communities.filter(validPosition)){
   const label=en()?region.nameEn:region.nameJa;
   const html=`<span class="community-map-label"><strong>${escape(label)}</strong>${[['sos','SOS','SOS'],['unreported','応答なし','No response'],['safe','無事','Safe'],['rescued','救助済み','Rescued']].map(([key,ja,en])=>`<span>${local(ja,en)} ${region[key]}${local('人','')}</span>`).join('')}</span>`;
   const point=map.latLngToContainerPoint([region.latitude,region.longitude]),size=map.getSize();
   if(point.x<0||point.y<0||point.x>size.x||point.y>size.y)continue;
   const rect=placeMapLabel(point,size,occupied),dx=rect.x+rect.w/2-point.x,dy=rect.y+rect.h/2-point.y;occupied.push(rect);
   if(dx||dy){const line=L.polyline([[region.latitude,region.longitude],map.containerPointToLatLng([point.x+dx,point.y+dy])],{color:'#52616a',weight:1,interactive:false});line.addTo(map);caseLayers.push(line);}
   const marker=L.marker([region.latitude,region.longitude],{icon:L.divIcon({className:'community-map-marker',html,iconSize:[136,112],iconAnchor:[68-dx,56-dy]}),title:label,keyboard:true,zIndexOffset:500});
   marker.on('click',()=>{if(!consumePick(region))selectCommunity(region.id);});marker.addTo(map);caseLayers.push(marker);
  }
  for(const report of reports.filter(r=>!r.cancelled&&validPosition(r))){
   const marker=L.marker([report.latitude,report.longitude],{icon:L.divIcon({className:'report-map-marker',html:`<img src="${report.urgency==='HIGH'?'/web1617-report-emergency.png':['LOW','MEDIUM'].includes(report.urgency)?'/web1617-report-normal.png':'/web1617-report-unrated.svg'}" alt="">`,iconSize:[44,44],iconAnchor:[22,22]}),title:report.label,keyboard:true});
   marker.on('click',()=>selectEntry({...report,kind:'report'}));marker.addTo(map);caseLayers.push(marker);
  }
 }
 const picker=document.createElement('div');picker.id='map-pick-notice';picker.className='map-pick-notice';picker.hidden=true;picker.innerHTML='<p>通報する場所を地図上で選んでください</p><button type="button" class="secondary">中止</button>';$('tsunami-map').after(picker);
 function endPick(){pick=null;picker.hidden=true;$('tsunami-map').classList.remove('selecting-location');}
 function consumePick(position){if(!pick)return false;const callback=pick;endPick();callback({latitude:position.latitude,longitude:position.longitude});return true;}
 picker.querySelector('button').onclick=endPick;

 function clearResult(){for(const layer of layers)map?.removeLayer(layer);layers=[];delete $('tsunami-map').dataset.caseId;delete $('tsunami-map').dataset.cells;}
 function drawResult(){
  clearResult();if(!map||!result||!tsunamiEnabled)return;
  layers.push(L.geoJSON(result.coverage,{interactive:false,style:{color:'#155eef',weight:2,fill:false,dashArray:'6 4'}}).addTo(map));
  layers.push(L.geoJSON(result.geojson,{style:f=>({stroke:false,fillColor:tsunamiDepthColor(f.properties.max_depth_m),fillOpacity:.72}),onEachFeature:(feature,layer)=>layer.on('click',event=>{L.DomEvent.stopPropagation(event);if(consumePick({latitude:event.latlng.lat,longitude:event.latlng.lng}))return;L.popup().setLatLng(event.latlng).setContent(`${local('最大浸水深（計算）','Calculated maximum inundation depth')} ${Number(feature.properties.max_depth_m).toFixed(2)}m`).openOn(map);})}).addTo(map));
  $('tsunami-map').dataset.caseId=String(result.metadata?.id||result.heightM);$('tsunami-map').dataset.cells=String(result.geojson.features.length);
 }
 function center(){if(home)map.setView([home.latitude,home.longitude],15,{animate:false});else map.setView([35.5,138],6,{animate:false});}
 function initialize(){
  if(!globalThis.L)throw Error('地図を読み込めませんでした。通信があるときに開き直してください');
  map=L.map('tsunami-map',{preferCanvas:true,zoomSnap:.25,zoomAnimation:false,fadeAnimation:false,markerZoomAnimation:false,minZoom:4,maxZoom:19,scrollWheelZoom:false,zoomControl:false});
  L.control.zoom({position:'topright'}).addTo(map);
  map.on('click',async event=>{
   const point={latitude:event.latlng.lat,longitude:event.latlng.lng};if(consumePick(point))return;
   const request=++buildingRequest;if(map.getZoom()<14)return;
   let buildings=[];
   if(vectorMap){const layers=['kyoto-risk-buildings','toranomon-risk-buildings'].filter(id=>vectorMap.getLayer(id));if(layers.length)buildings=mapBuildingFeatures(vectorMap.queryRenderedFeatures(vectorMap.project([point.longitude,point.latitude]),{layers}));}
   try{if(!buildings.length){const {buildingsAt}=await import('./building-picker.mjs');buildings=await buildingsAt(point.latitude,point.longitude,point.longitude>139?'13103':'26100');}
    if(request===buildingRequest&&!$('buildings').hidden&&!pick&&buildings.length)selectBuilding(buildings);
   }catch{if(request===buildingRequest)$('tsunami-basemap-state').textContent=local('建物情報を取得できません。通信を確認してもう一度タップしてください。','Building information unavailable. Check your connection and tap again.');}
  });
  map.on('moveend zoomend',()=>{buildingRequest++;drawCases();});
  import('./vector-basemap.mjs').then(async({addVectorBasemap})=>{const layer=await addVectorBasemap(map);vectorMap=layer.getMaplibreMap();import('./building-risk.mjs').then(({installBuildingRisk})=>installBuildingRisk(layer.getMaplibreMap()).then(highlightRegisteredBuildings)).catch(()=>{$('tsunami-map').dataset.buildingRisk='unavailable';});layer.getMaplibreMap().on('error',()=>{$('tsunami-basemap-state').textContent='背景地図を取得できません。圏外で使うには市全域の地図を保存してください。';});layer.getMaplibreMap().on('idle',()=>{$('tsunami-basemap-state').textContent='';});}).catch(()=>{$('tsunami-basemap-state').textContent='背景地図を読み込めませんでした';});
  if(initialFocus)map.setView(initialFocus,15,{animate:false});else center();drawCases();locationControl=currentLocationControl({getDemoLocation:()=>demoLocation,button:$('tsunami-fit'),isVisible:()=>!$('buildings').hidden,onFix:fix=>{
   if(!fix){myDot?.remove();myAccuracy?.remove();myDot=null;myAccuracy=null;return;}
   const point=[fix.latitude,fix.longitude];
   if(!myAccuracy)myAccuracy=L.circle(point,{className:'my-location-accuracy',radius:fix.accuracy,color:'#2675df',weight:1,opacity:.25,fillOpacity:.12,interactive:false}).addTo(map);else myAccuracy.setLatLng(point).setRadius(fix.accuracy);
   if(!myDot)myDot=L.circleMarker(point,{className:'my-location-dot',radius:7,color:'#fff',weight:3,fillColor:'#2675df',fillOpacity:1,interactive:false}).addTo(map);else myDot.setLatLng(point);
  },onCenter:fix=>map.setView([fix.latitude,fix.longitude],Math.max(16,map.getZoom()),{animate:false}),onMessage:message=>$('current-location-status').textContent=message});
  map.on('dragstart zoomstart',()=>locationControl.cancelCenter());
  new MutationObserver(()=>locationControl.resume()).observe($('buildings'),{attributes:true,attributeFilter:['hidden']});locationControl.resume();
  for(const [cls,label] of [['.leaflet-control-zoom-in','拡大'],['.leaflet-control-zoom-out','縮小']]){const el=$('tsunami-map').querySelector(cls);el?.setAttribute('aria-label',label);el?.setAttribute('title',label);}refreshLanguage();
 }
 function scheduleHeight(){
  clearTimeout(heightTimer);controller?.abort();generation++;$('tsunami-height-form').setAttribute('aria-busy','false');
  const raw=$('tsunami-height').value.trim(),heightM=Number(raw);
  if(!raw||!Number.isInteger(heightM)||heightM<0||heightM>35){clearResult();result=null;status(raw?'range':'enter');$('tsunami-status').dataset.status='error';$('tsunami-height').setAttribute('aria-invalid',String(Boolean(raw)));return;}
  $('tsunami-height').removeAttribute('aria-invalid');
  clearResult();result=null;
  if(tsunamiEnabled){heightTimer=setTimeout(()=>$('tsunami-height-form').requestSubmit(),350);}
 }
 $('tsunami-height').oninput=scheduleHeight;
 $('map-tsunami').onclick=()=>{
  tsunamiEnabled=!tsunamiEnabled;
  clearTimeout(heightTimer);if(!tsunamiEnabled){controller?.abort();generation++;$('tsunami-height-form').setAttribute('aria-busy','false');}
  $('map-tsunami').setAttribute('aria-pressed',String(tsunamiEnabled));
  if(!tsunamiEnabled)status('off');
  else if(result)status('result',result.heightM);

  if(tsunamiEnabled&&!result)scheduleHeight();
  drawResult();
 };
 $('tsunami-height-form').onsubmit=async event=>{
  event.preventDefault();clearTimeout(heightTimer);const raw=$('tsunami-height').value.trim(),heightM=Number(raw);if(!raw||!Number.isInteger(heightM)||heightM<0||heightM>35){status('range');$('tsunami-status').dataset.status='error';return;}
  controller?.abort();controller=new AbortController();const epoch=++generation;
  loadStarted=Date.now();
  const showProgress=()=>{if(epoch===generation)status('loading');};
  const ticker=setInterval(showProgress,1000);
  clearResult();result=null;
  status('loading');$('tsunami-status').dataset.status='pending';$('tsunami-height-form').setAttribute('aria-busy','true');
  try{
   const {computePrecomputedHeight}=await import('./tsunami-precomputed.mjs');
   const calculated=await computePrecomputedHeight(heightM,{signal:controller.signal,onProgress:()=>{if(epoch===generation)showProgress();}});
   if(epoch!==generation)return;result=calculated;drawResult();
   status('result',heightM);$('tsunami-status').dataset.status='success';
  }catch(error){if(epoch!==generation)return;clearResult();result=null;status('error');$('tsunami-status').dataset.status='error';}
  finally{clearInterval(ticker);if(epoch===generation){$('tsunami-height-form').setAttribute('aria-busy','false');}}
 };
 return {refreshLanguage,setRegisteredBuildings(ids){if(JSON.stringify(ids)===JSON.stringify(registeredIds))return;registeredIds=ids;highlightRegisteredBuildings();},setDemoLocation(value){demoLocation=validPosition(value)?value:null;locationControl?.resume();},focusPerson(position){if(!validPosition(position))return;initialFocus=[position.latitude,position.longitude];if(!map)initialize();map.setView(initialFocus,Math.max(16,map.getZoom()),{animate:false});},setBuildingSelect(callback){selectBuilding=callback;},focusRegion(region){if(map)map.setView([region.latitude,region.longitude],16,{animate:false});},focusHome(){if(home){initialFocus=[home.latitude,home.longitude];if(map)map.setView(initialFocus,16,{animate:false});}},focusVenue(actual=false){initialFocus=actual?[35.6665,139.7495]:[35.0145,135.7625];if(map)map.setView(initialFocus,15,{animate:false});},setHome(value){home=value;},setCommunities(value,onSelect){communities=value;selectCommunity=onSelect||selectCommunity;drawCases();},setPeople(value,onSelect){people=value;selectPerson=onSelect||selectPerson;drawCases();},setReports(value,onSelect){reports=value;selectReport=onSelect||selectReport;drawCases();},setShowSafe(value){showSafe=value;drawCases();},pickLocation(callback,{language='ja'}={}){if(!map)initialize();pick=callback;picker.querySelector('p').textContent=language==='en'?'Select the incident location on the map':'通報する場所を地図上で選んでください';picker.querySelector('button').textContent=language==='en'?'Cancel':'中止';picker.hidden=false;$('tsunami-map').classList.add('selecting-location');},cancelPick:endPick,async show(){try{if(!map)initialize();locationControl?.resume();requestAnimationFrame(()=>{if(!$('buildings').hidden){const center=map.getCenter(),zoom=map.getZoom();map.invalidateSize({pan:false});map.setView(center,zoom,{animate:false});}});}catch(error){$('tsunami-basemap-state').textContent=error.message;}}};
}
