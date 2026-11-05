export function dismissKeyboard(){const el=document.activeElement;if(el?.matches('input,textarea,[contenteditable="true"]'))el.blur();}
export function clearableSearch(input){
 input.enterKeyHint='search';input.autocomplete='off';
 const wrap=document.createElement('span');wrap.className='search-input-wrap';input.before(wrap);wrap.append(input);
 const button=document.createElement('button');button.type='button';button.className='search-clear';button.setAttribute('aria-label','入力を消す');button.innerHTML='<img src="/close.svg" alt="">';wrap.append(button);
 const refresh=()=>{button.hidden=!input.value;};input.addEventListener('input',refresh);
 button.onclick=()=>{input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();};refresh();return refresh;
}
