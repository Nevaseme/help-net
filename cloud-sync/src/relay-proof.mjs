import {ApiError,requireValue,canonical} from './validation.mjs';

function base64(value,max) {
 requireValue(typeof value==='string'&&value.length>0&&value.length<=max&&value.length%4===0&&/^[A-Za-z0-9+/]+={0,2}$/.test(value),'invalid_relay_proof',403);
 let binary;try{binary=atob(value);}catch{throw new ApiError(403,'invalid_relay_proof');}
 requireValue(btoa(binary)===value,'invalid_relay_proof',403);
 return Uint8Array.from(binary,c=>c.charCodeAt(0));
}

// Android SHA256withECDSA emits ASN.1 DER; WebCrypto verifies fixed-width r || s.
function derToRaw(bytes) {
 const valid=ok=>requireValue(ok,'invalid_relay_proof',403);
 valid(bytes.length>=8&&bytes.length<=72&&bytes[0]===0x30&&bytes[1]===bytes.length-2);
 let offset=2;const raw=new Uint8Array(64);
 for(let index=0;index<2;index++){
  valid(bytes[offset++]===2);const length=bytes[offset++];
  valid(length>=1&&length<=33&&offset+length<=bytes.length);
  let value=bytes.subarray(offset,offset+length);offset+=length;
  valid((value[0]&128)===0);
  if(value[0]===0){valid(value.length>1&&(value[1]&128)!==0);value=value.subarray(1);}
  valid(value.length<=32);raw.set(value,index*32+32-value.length);
 }
 valid(offset===bytes.length);return raw;
}

export async function importRelayKey(input) {
 try{
  requireValue(input&&typeof input.keyId==='string'&&/^[a-f0-9]{64}$/.test(input.keyId));
  const spki=base64(input.publicKeySpki,684);
  const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',spki)),b=>b.toString(16).padStart(2,'0')).join('');
  requireValue(digest===input.keyId);
  return await crypto.subtle.importKey('spki',spki,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);
 }catch{throw new ApiError(400,'invalid_relay_key');}
}

export async function verifyRelayProof(item,groupId,registration) {
 try{
  const proof=item.relayProof;
  requireValue(proof&&proof.version===1&&proof.keyId===registration.keyId&&typeof proof.body==='string'&&new TextEncoder().encode(proof.body).length<=25000,'invalid_relay_proof',403);
  const signed=JSON.parse(proof.body);
  requireValue(signed?.domain==='pland-relay-1'&&signed.groupId===groupId&&signed.collection===item.collection&&canonical(signed.record)===canonical(item.record),'invalid_relay_proof',403);
  const key=await importRelayKey(registration),signature=derToRaw(base64(proof.signature,96));
  requireValue(await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},key,signature,new TextEncoder().encode(proof.body)),'invalid_relay_proof',403);
 }catch{throw new ApiError(403,'invalid_relay_proof');}
}
