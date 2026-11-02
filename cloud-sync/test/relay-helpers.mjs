export async function relayKey(namedCurve='P-256') {
 const pair=await crypto.subtle.generateKey({name:'ECDSA',namedCurve},true,['sign','verify']);
 const spki=new Uint8Array(await crypto.subtle.exportKey('spki',pair.publicKey));
 const keyId=Buffer.from(await crypto.subtle.digest('SHA-256',spki)).toString('hex');
 return {pair,registration:{keyId,publicKeySpki:Buffer.from(spki).toString('base64')}};
}
export function derSignature(raw) {
 const integer=bytes=>{let value=Buffer.from(bytes);while(value.length>1&&value[0]===0)value=value.subarray(1);if(value[0]&128)value=Buffer.concat([Buffer.from([0]),value]);return Buffer.concat([Buffer.from([2,value.length]),value]);};
 const value=Buffer.concat([integer(raw.subarray(0,32)),integer(raw.subarray(32))]);
 return Buffer.concat([Buffer.from([48,value.length]),value]);
}
export async function signedItem(key,collection,record,bodyOverride) {
 const body=bodyOverride??JSON.stringify({domain:'pland-relay-1',groupId:record.groupId,collection,record});
 const signature=await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},key.pair.privateKey,new TextEncoder().encode(body));
 return {collection,record,relayProof:{version:1,keyId:key.registration.keyId,body,signature:derSignature(new Uint8Array(signature)).toString('base64')}};
}
