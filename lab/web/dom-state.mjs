export function setText(el,value){if(el&&el.textContent!==String(value))el.textContent=String(value);}
export function setProperty(el,key,value){if(el&&el[key]!==value)el[key]=value;}
export function setHtml(el,value){if(el&&el.innerHTML!==value)el.innerHTML=value;}
