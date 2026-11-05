// The grip follows the pointer; panel contents remain independently scrollable.
export function wireSheetHandle(sheet){
 const old=sheet.querySelector('.sheet-handle');if(!old||old.tagName==='BUTTON')return;
 const handle=document.createElement('button');handle.type='button';handle.className='sheet-handle';handle.tabIndex=-1;
 handle.setAttribute('aria-label',document.documentElement.lang==='en'?'Drag to close':'下にドラッグして閉じる');old.replaceWith(handle);sheet.querySelector('header button')?.setAttribute('autofocus','');
 let start=null,offset=0,moved=false,animation=null;
 const reset=()=>{start=null;offset=0;sheet.style.transform='';sheet.style.willChange='';};
 const settle=close=>{const from=offset,to=close?sheet.getBoundingClientRect().height+80:0;reset();animation?.cancel();
  if(matchMedia('(prefers-reduced-motion: reduce)').matches){if(close)sheet.close();return;}
  animation=sheet.animate([{transform:`translateY(${from}px)`},{transform:`translateY(${to}px)`}],{duration:180,easing:'ease-out'});
  animation.onfinish=()=>{animation=null;if(close)sheet.close();};
 };
 handle.onpointerdown=e=>{if(e.button!==0)return;e.preventDefault();animation?.cancel();start=e.clientY;offset=0;moved=false;sheet.style.willChange='transform';handle.setPointerCapture(e.pointerId);};
 handle.onpointermove=e=>{if(start===null)return;offset=Math.max(0,e.clientY-start);moved||=Math.abs(e.clientY-start)>4;sheet.style.transform=`translateY(${offset}px)`;};
 handle.onpointerup=()=>{if(start===null)return;settle(offset>=Math.min(120,sheet.getBoundingClientRect().height*.25));};
 handle.onpointercancel=()=>settle(false);
 handle.onclick=e=>{e.preventDefault();if(!moved)sheet.close();moved=false;};
 sheet.addEventListener('close',()=>{animation?.cancel();animation=null;reset();});
}
