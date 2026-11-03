import {tsunamiDepthColor} from './tsunami-map-model.mjs';

/** One map for normal browsing and exact, previously checked height results. */
export function createTsunamiMap(){
 const $=id=>document.getElementById(id);let map,home=null,initialFocus=null,result=null,layers=[],generation=0,controller=null;
 let tsunamiEnabled=false,heightTimer;
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
   const marker=L.marker([person.position.latitude,person.position.longitude],{icon:L.divIcon({className:'person-map-marker',html:`<span class="person-symbol ${escape(person.status)}">${symbol}</span>${person.impactAt?`<span class="person-impact" aria-label="${local('衝撃を検知','Impact detected')}">!</span>`:''}${person.enclosedAt?`<span class="person-enclosed" aria-label="${local('閉所を検知','Enclosed space detected')}">▣</span>`:''}`,iconSize:[44,44],iconAnchor:[22,22]}),title:person.label,keyboard:true});
   marker.on('click',()=>selectEntry({kind:'person',id:person.id,...person.position}));marker.addTo(map);caseLayers.push(marker);
  }
  const occupied=entries.map(p=>{const c=map.latLngToContainerPoint([p.latitude,p.longitude]);return {x:c.x-26,y:c.y-26,w:52,h:52};});
  for(const region of communities.filter(validPosition)){
   const label=en()?region.nameEn:region.nameJa;
   const html=`<span class="community-map-label"><strong>${escape(label)}</strong><span>SOS ${region.sos} · ${local('応答なし','No response')} ${region.unreported}</span></span>`;
   const point=map.latLngToContainerPoint([region.latitude,region.longitude]),size=map.getSize();
   const options=[[0,0],[0,-90],[0,90],[-100,0],[100,0],[-100,-90],[100,-90],[-100,90],[100,90],[0,-155],[0,155]];
   const score=([x,y])=>{const r={x:point.x+x-86,y:point.y+y-29,w:172,h:58};return occupied.reduce((n,o)=>n+Math.max(0,Math.min(r.x+r.w,o.x+o.w)-Math.max(r.x,o.x))*Math.max(0,Math.min(r.y+r.h,o.y+o.h)-Math.max(r.y,o.y)),0)+(r.x<5||r.x+r.w>size.x-5||r.y<10||r.y+r.h>size.y-50?100000:0)+Math.hypot(x,y);};
   const [dx,dy]=options.sort((a,b)=>score(a)-score(b))[0];occupied.push({x:point.x+dx-86,y:point.y+dy-29,w:172,h:58});
   if(dx||dy){const line=L.polyline([[region.latitude,region.longitude],map.containerPointToLatLng([point.x+dx,point.y+dy])],{color:'#52616a',weight:1,interactive:false});line.addTo(map);caseLayers.push(line);}
   const marker=L.marker([region.latitude,region.longitude],{icon:L.divIcon({className:'community-map-marker',html,iconSize:[172,58],iconAnchor:[86-dx,29-dy]}),title:label,keyboard:true,zIndexOffset:500});
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
  layers.push(L.geoJSON(result.geojson,{style:f=>({stroke:false,fillColor:tsunamiDepthColor(f.properties.max_depth_m),fillOpacity:.72}),onEachFeature:(feature,layer)=>layer.on('click',event=>{L.DomEvent.stopPropagation(event);if(consumePick({latitude:event.latlng.lat,longitude:event.latlng.lng}))return;L.popup().setLatLng(event.latlng).setContent(`${local('最大浸水深（計算）','Calculated maximum depth')} ${Number(feature.properties.max_depth_m).toFixed(2)}m`).openOn(map);})}).addTo(map));
  $('tsunami-map').dataset.caseId=String(result.metadata?.id||result.heightM);$('tsunami-map').dataset.cells=String(result.geojson.features.length);
 }
 function center(){if(home)map.setView([home.latitude,home.longitude],15,{animate:false});else map.setView([35.5,138],6,{animate:false});}
 function initialize(){
  if(!globalThis.L)throw Error('地図を読み込めませんでした。通信があるときに開き直してください');
  map=L.map('tsunami-map',{preferCanvas:true,zoomSnap:.25,zoomAnimation:false,fadeAnimation:false,markerZoomAnimation:false,minZoom:4,maxZoom:19,scrollWheelZoom:false,zoomControl:false});
  L.control.zoom({position:'topright'}).addTo(map);
  map.on('click',event=>consumePick({latitude:event.latlng.lat,longitude:event.latlng.lng}));
  map.on('moveend zoomend',drawCases);
  import('./vector-basemap.mjs').then(async({addVectorBasemap})=>{const layer=await addVectorBasemap(map);import('./building-risk.mjs').then(({installBuildingRisk})=>installBuildingRisk(layer.getMaplibreMap())).catch(()=>{$('tsunami-map').dataset.buildingRisk='unavailable';});layer.getMaplibreMap().on('error',()=>{$('tsunami-basemap-state').textContent='背景地図を取得できません。圏外で使うには市全域の地図を保存してください。';});layer.getMaplibreMap().on('idle',()=>{$('tsunami-basemap-state').textContent='';});}).catch(()=>{$('tsunami-basemap-state').textContent='背景地図を読み込めませんでした';});
  if(initialFocus)map.setView(initialFocus,15,{animate:false});else center();drawCases();$('tsunami-fit').onclick=()=>{if(home)map.setView([home.latitude,home.longitude],15,{animate:false});else if(initialFocus)map.setView(initialFocus,15,{animate:false});else map.setView([35.5,138],6,{animate:false});};
  for(const [cls,label] of [['.leaflet-control-zoom-in','拡大'],['.leaflet-control-zoom-out','縮小']]){const el=$('tsunami-map').querySelector(cls);el?.setAttribute('aria-label',label);el?.setAttribute('title',label);}
 }
 function scheduleHeight(){
  clearTimeout(heightTimer);controller?.abort();generation++;$('tsunami-height-form').setAttribute('aria-busy','false');
  const raw=$('tsunami-height').value.trim(),heightM=Number(raw);
  if(!raw||!Number.isInteger(heightM)||heightM<0||heightM>35){clearResult();result=null;$('tsunami-status').textContent=raw?'0〜35m・1m刻みで入力してください':'津波の高さを入力してください';$('tsunami-status').dataset.status='error';$('tsunami-height').setAttribute('aria-invalid',String(Boolean(raw)));return;}
  $('tsunami-height').removeAttribute('aria-invalid');
  clearResult();result=null;
  if(tsunamiEnabled){heightTimer=setTimeout(()=>$('tsunami-height-form').requestSubmit(),350);}
 }
 $('tsunami-height').oninput=scheduleHeight;
 $('map-tsunami').onclick=()=>{
  tsunamiEnabled=!tsunamiEnabled;
  clearTimeout(heightTimer);if(!tsunamiEnabled){controller?.abort();generation++;$('tsunami-height-form').setAttribute('aria-busy','false');}
  $('map-tsunami').setAttribute('aria-pressed',String(tsunamiEnabled));
  if(!tsunamiEnabled)$('tsunami-status').textContent='津波表示はオフです。';
  else if(result)$('tsunami-status').textContent=`津波高 ${result.heightM}m の条件計算を表示しています。`;

  if(tsunamiEnabled&&!result)scheduleHeight();
  drawResult();
 };
 $('tsunami-height-form').onsubmit=async event=>{
  event.preventDefault();clearTimeout(heightTimer);const raw=$('tsunami-height').value.trim(),heightM=Number(raw);if(!raw||!Number.isInteger(heightM)||heightM<0||heightM>35){$('tsunami-status').textContent='津波高は0〜35mの範囲で、1m刻みで入力してください。';$('tsunami-status').dataset.status='error';return;}
  controller?.abort();controller=new AbortController();const epoch=++generation;
  const started=Date.now();let progressMessage='計算済みの結果を読み込んでいます…';
  const showProgress=()=>{if(epoch===generation)$('tsunami-status').textContent=`${progressMessage}（経過 ${Math.floor((Date.now()-started)/1000)}秒）`;};
  const ticker=setInterval(showProgress,1000);
  clearResult();result=null;
  $('tsunami-status').textContent=progressMessage;$('tsunami-status').dataset.status='pending';$('tsunami-height-form').setAttribute('aria-busy','true');
  try{
   const {computePrecomputedHeight}=await import('./tsunami-precomputed.mjs');
   const calculated=await computePrecomputedHeight(heightM,{signal:controller.signal,onProgress:progress=>{if(epoch===generation){progressMessage=progress.message||'読み込んでいます…';showProgress();}}});
   if(epoch!==generation)return;result=calculated;drawResult();
   $('tsunami-status').textContent=`津波高 ${heightM}m の条件計算を表示しています。`;$('tsunami-status').dataset.status='success';
  }catch(error){if(epoch!==generation)return;clearResult();result=null;$('tsunami-status').textContent=error.message||'計算を完了できませんでした。';$('tsunami-status').dataset.status='error';}
  finally{clearInterval(ticker);if(epoch===generation){$('tsunami-height-form').setAttribute('aria-busy','false');}}
 };
 return {focusHome(){if(home){initialFocus=[home.latitude,home.longitude];if(map)map.setView(initialFocus,16,{animate:false});}},focusVenue(actual=false){initialFocus=actual?[35.6665,139.7495]:[35.0145,135.7625];if(map)map.setView(initialFocus,15,{animate:false});},setHome(value){home=value;},setCommunities(value,onSelect){communities=value;selectCommunity=onSelect||selectCommunity;drawCases();},setPeople(value,onSelect){people=value;selectPerson=onSelect||selectPerson;drawCases();},setReports(value,onSelect){reports=value;selectReport=onSelect||selectReport;drawCases();},setShowSafe(value){showSafe=value;drawCases();},pickLocation(callback,{language='ja'}={}){if(!map)initialize();pick=callback;picker.querySelector('p').textContent=language==='en'?'Select the incident location on the map':'通報する場所を地図上で選んでください';picker.querySelector('button').textContent=language==='en'?'Cancel':'中止';picker.hidden=false;$('tsunami-map').classList.add('selecting-location');},cancelPick:endPick,async show(){try{if(!map)initialize();requestAnimationFrame(()=>{if(!$('buildings').hidden){const center=map.getCenter(),zoom=map.getZoom();map.invalidateSize({pan:false});map.setView(center,zoom,{animate:false});}});}catch(error){$('tsunami-basemap-state').textContent=error.message;}}};
}
