// Keep handle gestures local; scrolling the panel body never dismisses it.
export function wireSheetHandle(sheet){
 const old=sheet.querySelector('.sheet-handle');if(!old||old.tagName==='BUTTON')return;
 const handle=document.createElement('button');handle.type='button';handle.className='sheet-handle';handle.setAttribute('aria-label',document.documentElement.lang==='en'?'Close panel':'パネルを閉じる');old.replaceWith(handle);handle.onclick=()=>sheet.close();let start=null;
 handle.onpointerdown=e=>{if(e.button!==0)return;start=e.clientY;handle.setPointerCapture(e.pointerId);};
 handle.onpointerup=e=>{if(start!==null&&e.clientY-start>40)sheet.close();start=null;};handle.onpointercancel=()=>{start=null;};
}
