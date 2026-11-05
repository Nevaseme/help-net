import test from 'node:test';
import assert from 'node:assert/strict';
import {usDateTime,usTime} from '../web/date-time.mjs';
import {personPresentation} from '../web/web1617-ui.mjs';
test('dates use US month/day/year and 12-hour clock in local time',()=>{const day=new Date(2026,10,5,15,30);assert.equal(usDateTime(day),'11/05/2026 3:30 PM');assert.equal(usTime(day),'3:30 PM');assert.equal(usTime(new Date(2026,0,1,0,5)),'12:05 AM');});
test('no-response disclosure uses the adjusted drill start from the first snapshot',()=>{const now=1800000000000,person={originId:'resident',safety:'UNREPORTED',name:'Test resident',fictional:true};const view={start:{createdAt:now-4*60000}};const shown=personPresentation(person,[],view,'viewer',now);assert.equal(shown.disclosed,true);assert.equal(shown.showHome,true);assert.equal(personPresentation(person,[],{start:{createdAt:now-179999}},'viewer',now).disclosed,false);});

test('sheet follows the pointer and returns after a short drag; long drag closes after animation',async t=>{
 const {wireSheetHandle}=await import('../web/sheet-handle.mjs');let handle,animation,closed=0;
 const old={tagName:'DIV',replaceWith:x=>handle=x},closeButton={setAttribute:()=>{}},sheet={style:{},querySelector:s=>s==='.sheet-handle'?old:closeButton,addEventListener:()=>{},getBoundingClientRect:()=>({height:400}),close:()=>closed++,animate:(frames)=>{animation={frames,cancel(){}};return animation;}};
 const previousDocument=globalThis.document,previousMedia=globalThis.matchMedia;t.after(()=>{globalThis.document=previousDocument;globalThis.matchMedia=previousMedia;});
 globalThis.document={documentElement:{lang:'en'},createElement:()=>({setAttribute(){},setPointerCapture(){}})};globalThis.matchMedia=()=>({matches:false});
 wireSheetHandle(sheet);handle.onpointerdown({button:0,clientY:100,pointerId:1,preventDefault(){}});handle.onpointermove({clientY:160});assert.equal(sheet.style.transform,'translateY(60px)');handle.onpointerup();assert.equal(animation.frames[1].transform,'translateY(0px)');animation.onfinish();assert.equal(closed,0);
 handle.onpointerdown({button:0,clientY:100,pointerId:1,preventDefault(){}});handle.onpointermove({clientY:240});assert.equal(sheet.style.transform,'translateY(140px)');handle.onpointerup();assert.equal(closed,0);animation.onfinish();assert.equal(closed,1);
});
