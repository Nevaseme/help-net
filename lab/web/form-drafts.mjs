// Local drafts are scoped to the actor, group, incident and edited record.
// They are never shared as saved response records.
const memory=new Map();
function read(key){try{return JSON.parse(sessionStorage.getItem(key))||memory.get(key);}catch{return memory.get(key);}}
const values=form=>[...form.elements].filter(e=>e.name&&!['submit','button','password','file'].includes(e.type)).map(e=>({name:e.name,value:e.value,checked:e.checked,type:e.type}));
export function bindFormDraft(form,scope,baseline,onConflict=()=>{}){
 const key='tasukeai-draft:'+JSON.stringify(scope),saved=read(key),initial=JSON.stringify(values(form));
 if(saved){for(const field of saved.fields)for(const el of form.elements)if(el.name===field.name&&el.type===field.type){if(['checkbox','radio'].includes(el.type)){if(el.value===field.value)el.checked=field.checked;}else el.value=field.value;}if(saved.baseline!==baseline)onConflict();}
 const capture=()=>{const fields=values(form);if(JSON.stringify(fields)===initial){clear();return;}const draft={fields,baseline:saved?.baseline??baseline};memory.set(key,draft);try{sessionStorage.setItem(key,JSON.stringify(draft));}catch{}};
 function clear(){memory.delete(key);try{sessionStorage.removeItem(key);}catch{}}
 form.addEventListener('input',capture);form.addEventListener('change',capture);
 return {clear,capture};
}
