import {geocodeCandidates} from './city-addresses.mjs';
export const candidateLabel=c=>c.address+(['town','area','block'].includes(c.precision)?' 付近':'');
export async function selectAddressCandidate(query,code=null){
 const rows=await geocodeCandidates(query,code);if(!rows.length)return null;if(rows.length===1)return rows[0];
 return new Promise(resolve=>{const dialog=document.createElement('dialog');dialog.className='address-choice';const title=document.createElement('h2');title.textContent='場所を選んでください';dialog.append(title);
 const close=value=>{dialog.close();dialog.remove();resolve(value);};
 for(const c of rows){const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent=candidateLabel(c);button.onclick=()=>close(c);dialog.append(button);}
 const cancel=document.createElement('button');cancel.type='button';cancel.className='text-button';cancel.textContent='戻る';cancel.onclick=()=>close(null);dialog.append(cancel);dialog.oncancel=e=>{e.preventDefault();close(null);};document.body.append(dialog);dialog.showModal();});
}
