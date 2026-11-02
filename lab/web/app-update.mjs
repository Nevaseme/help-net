// Activate downloaded assets without reloading an open document or touching records.
export async function setupAppUpdates(){
 if(!('serviceWorker' in navigator))return;
 let hasSeenController=!!navigator.serviceWorker.controller;const dirtyForms=new Map();
 const fingerprint=form=>JSON.stringify([...form.elements].filter(e=>e.name||e.id).filter(e=>!['button','submit','password','file'].includes(e.type)).map(e=>[e.name||e.id,e.value,e.checked]));
 document.addEventListener('focusin',e=>{const form=e.target.closest?.('form');if(form&&!dirtyForms.has(form))dirtyForms.set(form,fingerprint(form));});
 const notice=document.createElement('aside');notice.className='update-notice';notice.hidden=true;notice.setAttribute('aria-label','アプリの更新');
 const label=document.createElement('span');label.setAttribute('role','status');
 const button=document.createElement('button');button.type='button';button.textContent='更新する';notice.append(label,button);document.body.append(notice);
 const editing=()=>[...dirtyForms].some(([f,initial])=>f.isConnected&&f.id!=='response-edit-form'&&fingerprint(f)!==initial);
 document.addEventListener('form-saved',e=>{const form=e.detail?.form;if(form&&dirtyForms.has(form))dirtyForms.set(form,fingerprint(form));if(!notice.hidden)render();});
 function render(){const text=editing()?'新しい版があります。入力内容を保存してから更新してください。':'新しい版を利用できます。';if(label.textContent!==text)label.textContent=text;button.disabled=editing()||!!document.querySelector('[aria-busy="true"]');}
 document.addEventListener('input',render);document.addEventListener('change',render);
 new MutationObserver(records=>{for(const r of records)if(r.attributeName==='data-status'&&r.target.dataset.status==='success'){const f=r.target.closest('form');if(f&&dirtyForms.has(f))dirtyForms.set(f,fingerprint(f));}if(!notice.hidden)render();}).observe(document.body,{subtree:true,attributes:true,attributeFilter:['data-status','aria-busy']});
 button.onclick=()=>{render();if(!button.disabled)location.reload();};
 navigator.serviceWorker.addEventListener('controllerchange',()=>{if(hasSeenController){notice.hidden=false;render();}hasSeenController=!!navigator.serviceWorker.controller;});
 const registration=await navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'});
 registration.waiting?.postMessage({type:'ACTIVATE_UPDATE'});
 const check=()=>{if(navigator.onLine&&document.visibilityState==='visible')registration.update().catch(()=>{});};
 document.addEventListener('visibilitychange',check);window.addEventListener('online',check);
 await navigator.serviceWorker.ready;return registration;
}
