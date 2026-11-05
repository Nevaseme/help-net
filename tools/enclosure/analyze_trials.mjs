// Summarize labelled measurements; never infer burial or assign an SOS state.
import {readFile,writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
const finite=Number.isFinite;
function statistics(values){
 if(!values.length)return null;
 const mean=values.reduce((a,b)=>a+b,0)/values.length;
 return {count:values.length,min:Math.min(...values),max:Math.max(...values),mean,sd:Math.sqrt(values.reduce((a,b)=>a+(b-mean)**2,0)/values.length)};
}
export function summarizeChannel(channel,start,end){
 if(!finite(start)||!finite(end)||end<=start)throw Error('Invalid measurement window');
 const samples=(channel.samples||[]).filter(s=>finite(s.elapsedNanos)&&Array.isArray(s.values)&&s.values.length&&s.values.every(finite)).sort((a,b)=>a.elapsedNanos-b.elapsedNanos);
 const result={channel:channel.channel,state:channel.state,unit:channel.unit,sensor:channel.sensor,evicted:channel.evicted||0,window_seconds:(end-start)/1e9};
 if(channel.state!=='registered')return {...result,observed:null};
 if(channel.sensor?.reportingMode===1){
  // On-change sensors report transitions, not repeated samples. Hold a known
  // value forward only; never fill the interval before the first observation.
  let current=null,cursor=start,total=0,sum=0,square=0,min=Infinity,max=-Infinity,changes=0;
  function add(until){if(current!==null&&until>cursor){const dt=until-cursor;total+=dt;sum+=current*dt;square+=current*current*dt;min=Math.min(min,current);max=Math.max(max,current);}cursor=until;}
  for(const s of samples){if(s.elapsedNanos<=start){current=s.values[0];continue;}if(s.elapsedNanos>=end)break;add(s.elapsedNanos);current=s.values[0];changes++;}
  add(end);const mean=total?sum/total:null;
  return {...result,observed:total?{mean,min,max,sd:Math.sqrt(Math.max(0,square/total-mean*mean))}:null,covered_fraction:total/(end-start),changes,aggregation:'time-weighted forward hold of registered on-change observations'};
 }
 const selected=samples.filter(s=>s.elapsedNanos>=start&&s.elapsedNanos<end),vectors=selected.map(s=>s.values);
 const magnitudes=vectors.map(v=>Math.hypot(...v)),dimensions=vectors[0]?.length;
 const axisMeans=dimensions?Array.from({length:dimensions},(_,axis)=>vectors.reduce((n,v)=>n+v[axis],0)/vectors.length):null;
 return {...result,observed:statistics(magnitudes),axis_means:axisMeans,centered_vector_rms:axisMeans?Math.sqrt(vectors.reduce((n,v)=>n+v.reduce((s,x,i)=>s+(x-axisMeans[i])**2,0),0)/vectors.length):null,
  maximum_gap_seconds:selected.length>1?Math.max(...selected.slice(1).map((s,i)=>(s.elapsedNanos-selected[i].elapsedNanos)/1e9)):null,aggregation:'sample-weighted magnitude and centered vector variation'};
}
export function analyzeTrials(document){
 if(!Array.isArray(document?.trials))throw Error('Trials are missing');
 return {format:'pland-enclosure-labelled-comparison-1',classification:'not-configured',window:'8–12 seconds after sensor start; assumes instructed setup at5s and removal at15s',
  limitations:['Labels and placement times are supplied by the operator, not sensor ground truth','No detection accuracy or burial probability is calculated','On-change values held forward require an uninterrupted registered sensor; missing data remain unknown'],
  trials:document.trials.map(t=>({label:t.label,method:t.method,startedAt:t.startedAt,device:t.device,application:t.application,featureVersion:t.featureVersion,brightnessOverride:t.brightnessOverride,...(Array.isArray(t.channels)?{channels:t.channels.map(c=>summarizeChannel(c,t.startedElapsedNanos+8e9,t.startedElapsedNanos+12e9))}:{audio_features:t,interpretation:'Audio compared separately; source and microphone processing must match'})}))};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const [input,output]=process.argv.slice(2);if(!output)throw Error('Usage: analyze_trials.mjs private-trials.json NEW-private-summary.json');
 const result=analyzeTrials(JSON.parse(await readFile(input,'utf8')));await writeFile(output,JSON.stringify(result,null,2),{flag:'wx'});console.log(JSON.stringify({trials:result.trials.length,classification:result.classification}));
}
