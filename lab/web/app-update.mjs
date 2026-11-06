// Activate downloaded assets without reloading an open document or touching records.
export async function setupAppUpdates({version}={}){
 if(!('serviceWorker' in navigator))return;
 let hasSeenController=!!navigator.serviceWorker.controller;const dirtyForms=new Map();
 const fingerprint=form=>JSON.stringify([...form.elements].filter(e=>e.name||e.id).filter(e=>!['button','submit','password','file'].includes(e.type)).map(e=>[e.name||e.id,e.value,e.checked]));
 document.addEventListener('focusin',e=>{const form=e.target.closest?.('form');if(form&&!dirtyForms.has(form))dirtyForms.set(form,fingerprint(form));});
 const notice=document.createElement('aside');notice.className='update-notice';notice.hidden=true;notice.setAttribute('aria-label','アプリの更新');
 const label=document.createElement('span');label.setAttribute('role','status');
 const button=document.createElement('button');button.type='button';button.textContent='更新する';notice.append(label,button);document.body.append(notice);
 const editing=()=>[...dirtyForms].some(([f,initial])=>f.isConnected&&f.id!=='response-edit-form'&&fingerprint(f)!==initial);
 document.addEventListener('form-saved',e=>{const form=e.detail?.form;if(form&&dirtyForms.has(form))dirtyForms.set(form,fingerprint(form));if(!notice.hidden)render();});
 function render(){const en=document.documentElement.lang==='en';notice.setAttribute('aria-label',en?'App update':'アプリの更新');button.textContent=en?'Update':'更新する';const text=editing()?(en?'An update is available. Save your changes before updating.':'新しい版があります。入力内容を保存してから更新してください。'):(en?'An update is available.':'新しい版を利用できます。');if(label.textContent!==text)label.textContent=text;button.disabled=editing()||!!document.querySelector('[aria-busy="true"]');}
 new MutationObserver(render).observe(document.documentElement,{attributes:true,attributeFilter:['lang']});render();
 document.addEventListener('input',render);document.addEventListener('change',render);
 new MutationObserver(records=>{for(const r of records)if(r.attributeName==='data-status'&&r.target.dataset.status==='success'){const f=r.target.closest('form');if(f&&dirtyForms.has(f))dirtyForms.set(f,fingerprint(f));}if(!notice.hidden)render();}).observe(document.body,{subtree:true,attributes:true,attributeFilter:['data-status','aria-busy']});
 button.onclick=()=>{render();if(!button.disabled)location.reload();};
 navigator.serviceWorker.addEventListener('controllerchange',()=>{if(hasSeenController){notice.hidden=false;render();}hasSeenController=!!navigator.serviceWorker.controller;});
 // A restored document can miss controllerchange while it is suspended.
 // Compare its own version with the already-active worker as well.
 const checkActiveVersion=()=>{
  if(!version||!navigator.serviceWorker.controller)return;
  const channel=new MessageChannel(),timer=setTimeout(()=>channel.port1.close(),1500);
  channel.port1.onmessage=event=>{clearTimeout(timer);channel.port1.close();const active=String(event.data).match(/v(\d+)\.(\d+)\.(\d+)/)?.slice(1).map(Number),current=version.split('.').map(Number);
   if(active&&active.some((n,i)=>n>current[i]&&active.slice(0,i).every((p,j)=>p===current[j]))){notice.hidden=false;render();}
  };
  navigator.serviceWorker.controller.postMessage({type:'GET_VERSION'},[channel.port2]);
 };
 const registration=await navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'});
 registration.waiting?.postMessage({type:'ACTIVATE_UPDATE'});
 const check=()=>{checkActiveVersion();if(navigator.onLine&&document.visibilityState==='visible')registration.update().then(checkActiveVersion).catch(()=>{});};
 document.addEventListener('visibilitychange',check);window.addEventListener('online',check);
 window.addEventListener('pageshow',check);checkActiveVersion();
 await navigator.serviceWorker.ready;checkActiveVersion();return registration;
}
