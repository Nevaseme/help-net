// Display only: keep stored timestamps and interchange formats unchanged.
export function usDateTime(value){const d=new Date(value);if(!Number.isFinite(d.getTime()))return '';return d.toLocaleDateString('en-US',{month:'2-digit',day:'2-digit',year:'numeric'})+' '+usTime(d);}
export function usTime(value){return new Date(value).toLocaleTimeString('en-US',{hour:'numeric',minute:'2-digit',hour12:true});}
