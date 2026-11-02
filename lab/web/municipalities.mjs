/** Municipalities with a city-wide data package. Never infer membership from GPS. */
export const MUNICIPALITIES=Object.freeze([
 Object.freeze({code:'26100',name:'京都市',prefecture:'京都府'}),
 Object.freeze({code:'22205',name:'熱海市',prefecture:'静岡県'}),
 Object.freeze({code:'13103',name:'港区',prefecture:'東京都'}),
]);
export const municipalityByCode=code=>MUNICIPALITIES.find(city=>city.code===code)??null;
export async function saveMunicipality(store,envelope){
 const value=envelope.municipality??null;
 if(value&&!municipalityByCode(value.code))throw Error('対応していない活動地域です。アプリを更新してください。');
 const previous=await store.read('meta','municipality:'+envelope.groupId);
 await store.update('meta','municipality:'+envelope.groupId,()=>value);
 if(JSON.stringify(previous)!==JSON.stringify(value)&&typeof window!=='undefined')window.dispatchEvent(new CustomEvent('municipality-change'));
}
