// Appearance is local to this installation; system is the default.
const appearanceMedia=matchMedia('(prefers-color-scheme: dark)');
let appearance='system';try{appearance=localStorage.getItem('tasukeai-appearance')||'system';}catch{}
if(!['system','light','dark'].includes(appearance))appearance='system';
function applyAppearance(){
 document.documentElement.dataset.appearance=appearance;
 document.documentElement.dataset.theme=appearance==='system'?(appearanceMedia.matches?'dark':'light'):appearance;
 document.querySelector('meta[name="theme-color"]')?.setAttribute('content',document.documentElement.dataset.theme==='dark'?'#142630':'#ffffff');
}
window.addEventListener('appearance-change',e=>{if(!['system','light','dark'].includes(e.detail))return;appearance=e.detail;try{localStorage.setItem('tasukeai-appearance',appearance);}catch{}applyAppearance();});
appearanceMedia.addEventListener('change',applyAppearance);applyAppearance();
// Keep the approved four tabs reachable while a non-modal detail sheet is open.
// Visible bounds follow browser chrome, the software keyboard and text scaling.
const root=document.documentElement;
// Pointer/programmatic focus is not a selection. Tab navigation retains its indicator.
root.dataset.inputModality='pointer';
document.addEventListener('pointerdown',()=>{root.dataset.inputModality='pointer';},true);
document.addEventListener('keydown',event=>{if(['Tab','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key))root.dataset.inputModality='keyboard';},true);
const height=selector=>document.querySelector(selector)?.getBoundingClientRect().height||0;
function measure(){
 const vv=window.visualViewport,visible=vv?.height||innerHeight;
 const keyboard=Math.max(0,innerHeight-visible-(vv?.offsetTop||0));
 const nav=height('.app-nav'),header=height('.app-header'),context=height('.app-context'),toolbar=height('.map-toolbar');
 root.style.setProperty('--nav-height',`${nav}px`);
 root.style.setProperty('--header-height',`${header}px`);
 root.style.setProperty('--sheet-bottom',`${Math.max(nav,keyboard)}px`);
 root.style.setProperty('--sheet-height',`${Math.max(140,innerHeight-Math.max(nav,keyboard)-header)}px`);
 root.style.setProperty('--map-height',`${Math.max(200,innerHeight-nav-header-context-toolbar)}px`);
}
const resize=new ResizeObserver(measure);
for(const el of document.querySelectorAll('.app-nav,.app-header,.app-context,.map-toolbar'))resize.observe(el);
window.addEventListener('resize',measure);window.visualViewport?.addEventListener('resize',measure);measure();

const scrim=document.createElement('div');scrim.className='sheet-scrim';scrim.hidden=true;scrim.setAttribute('aria-hidden','true');document.body.append(scrim);
const origins=new WeakMap(),order=[];
function syncSheets(){
 const open=order.filter(d=>d.open),top=open.at(-1);
 scrim.hidden=!top;
 for(const el of document.querySelectorAll('main>section,main>.incident-context,.app-context'))el.inert=!!top;
 for(const d of document.querySelectorAll('dialog'))d.inert=d.open&&d!==top;
 scrim.onclick=()=>top?.close();
}
function setup(d){
 if(d.dataset.focusManaged)return;d.dataset.focusManaged='true';
 const close=d.close.bind(d);
 const bringToFront=()=>{const at=order.indexOf(d);if(at>=0)order.splice(at,1);order.push(d);d.inert=false;};
 // Start at the heading rather than automatically highlighting the close button.
 // Keyboard users can still Tab to every control, with its normal focus ring.
 const focusHeading=()=>{const heading=d.querySelector('h2,h3');if(heading){heading.tabIndex=-1;heading.focus({preventScroll:true});}};
 for(const method of ['show','showModal']){const show=d[method].bind(d);d[method]=()=>{if(!d.open)origins.set(d,document.activeElement);show();bringToFront();syncSheets();focusHeading();};}
 // Dynamically inserted dialogs may already have opened before this observer runs.
 if(d.open){bringToFront();syncSheets();focusHeading();}
 d.close=(...args)=>{const restore=d.contains(document.activeElement);close(...args);syncSheets();const origin=origins.get(d);if(restore){const destination=origin?.isConnected&&!origin.closest('[inert]')?origin:document.querySelector('.app-nav [aria-pressed="true"]');destination?.focus({preventScroll:true});}};
 d.addEventListener('close',()=>{if(!d.open){const at=order.indexOf(d);if(at>=0)order.splice(at,1);}syncSheets();});
}
for(const d of document.querySelectorAll('dialog'))setup(d);
new MutationObserver(records=>{for(const r of records)for(const node of r.addedNodes)if(node.nodeType===1){if(node.matches('dialog'))setup(node);node.querySelectorAll('dialog').forEach(setup);}}).observe(document.body,{childList:true,subtree:true});

