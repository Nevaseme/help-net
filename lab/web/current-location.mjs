/** Local blue dot only. Never writes a home, safety position or shared record. */
export function currentLocationControl({button,onFix,onCenter,onMessage,isVisible,getDemoLocation=()=>null,geolocation=navigator.geolocation}){
 let watch=null,fix=null,centerNext=false,epoch=0,usingDemo=false;
 const en=()=>document.documentElement.lang==='en';
 const text=(ja,enText)=>en()?enText:ja;
 const clear=()=>{epoch++;if(watch!==null)geolocation?.clearWatch(watch);watch=null;centerNext=false;};
 function start(center=false){
  if(!isVisible()||document.hidden)return;
  const assigned=getDemoLocation();if(!!assigned!==usingDemo){clear();fix=null;onFix(null);usingDemo=!!assigned;}if(assigned){clear();fix={latitude:assigned.latitude,longitude:assigned.longitude,accuracy:0,acquiredAt:Date.now()};onFix(fix);onMessage('');if(center)onCenter(fix);return;}
  centerNext||=center;
  if(!geolocation){onMessage(text('現在地を取得できません','Current location is unavailable'));return;}
  if(watch!==null){if(center&&fix&&Date.now()-fix.acquiredAt<30000){onCenter(fix);centerNext=false;}return;}
  const request=++epoch;
  watch=geolocation.watchPosition(p=>{
   if(request!==epoch)return;
   const c=p.coords;if(!Number.isFinite(c.latitude)||!Number.isFinite(c.longitude)||!Number.isFinite(c.accuracy)||c.accuracy<0||Date.now()-p.timestamp>120000)return;
   fix={latitude:c.latitude,longitude:c.longitude,accuracy:c.accuracy,acquiredAt:p.timestamp};onFix(fix);onMessage('');
   if(centerNext){centerNext=false;onCenter(fix);}
  },e=>{if(request!==epoch)return;clear();fix=null;onFix(null);onMessage(e.code===1?text('現在地を表示するには位置情報を許可してください','Allow location access to show your location'):text('現在地を取得できません。位置情報をオンにして再試行してください','Unable to find your location. Turn on location services and try again.'));},{enableHighAccuracy:true,maximumAge:15000,timeout:15000});
 }
 async function resume(){
  if(document.hidden||!isVisible()){clear();onFix(null);return;}
  if(getDemoLocation()){start();return;}
  if(usingDemo){clear();fix=null;onFix(null);usingDemo=false;}
  // Do not prompt every time the map opens; a deliberate button press requests permission.
  try{if((await navigator.permissions?.query({name:'geolocation'}))?.state==='granted')start();}catch{}
 }
 const refreshLanguage=()=>{button.setAttribute('aria-label',text('現在地','My location'));button.title=text('現在地','My location');};
 button.onclick=()=>start(true);refreshLanguage();
 document.addEventListener('visibilitychange',resume);window.addEventListener('pagehide',clear);
 const timer=setInterval(()=>{if(fix&&Date.now()-fix.acquiredAt>120000){fix=null;onFix(null);}},15000);
 return {resume,refreshLanguage,cancelCenter(){centerNext=false;},dispose(){clear();clearInterval(timer);document.removeEventListener('visibilitychange',resume);window.removeEventListener('pagehide',clear);}};
}
