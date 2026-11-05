/** Offline root-finding only. Historical summaries propose one forcing; every
 * authoritative response, baseline and returned field comes from fresh simulate.
 * The frozen browser solver and its ordinary calibrator are never modified. */
import {simulate,calibrateHeight} from '../../lab/web/tsunami-solver.mjs';
export const HINTED_CONTROLLER_VERSION='pland-offline-hinted-calibration-1';
const sides=['left','right','bottom','top'];
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const number=(x,name,min=-Infinity)=>{if(!finite(x)||x<min)throw Error(`Invalid ${name}`);return x;};

/** Proposes a forcing only: the two historical responses are NOT live brackets. */
export function selectHistoricalGuess(points,target,maximum,tolerance){
  const sorted=points.filter(p=>finite(p.forcing_amplitude_m)&&p.forcing_amplitude_m>=0&&p.forcing_amplitude_m<=maximum&&finite(p.achieved_height_m)&&p.achieved_height_m>=0)
    .sort((a,b)=>a.forcing_amplitude_m-b.forcing_amplitude_m);
  for(let i=1;i<sorted.length;i++){
    if(sorted[i].forcing_amplitude_m===sorted[i-1].forcing_amplitude_m&&Math.abs(sorted[i].achieved_height_m-sorted[i-1].achieved_height_m)>1e-9)throw Error('Conflicting historical responses at the same forcing');
    if(sorted[i].achieved_height_m+tolerance<sorted[i-1].achieved_height_m)throw Error('Nonmonotone historical response');
  }
  const low=sorted.filter(p=>p.achieved_height_m<=target).sort((a,b)=>b.achieved_height_m-a.achieved_height_m)[0];
  const high=sorted.filter(p=>p.achieved_height_m>=target).sort((a,b)=>a.achieved_height_m-b.achieved_height_m)[0];
  if(!low||!high)return null;
  const amplitude=high.achieved_height_m===low.achieved_height_m?low.forcing_amplitude_m:
    low.forcing_amplitude_m+(high.forcing_amplitude_m-low.forcing_amplitude_m)*(target-low.achieved_height_m)/(high.achieved_height_m-low.achieved_height_m);
  if(!finite(amplitude)||amplitude<=0||amplitude>maximum)return null;
  return {forcing_amplitude_m:amplitude,source_points:[low,high],use:'initial forcing proposal only; neither historical point is an authoritative bracket'};
}

export function calibrateHeightHinted(config,targetHeight,{hint,onProgress,isCancelled}={}){
  number(targetHeight,'target coastal height',0);
  const c=config.calibration??{},maximum=number(c.maximum_forcing_m,'maximum forcing',Number.MIN_VALUE);
  const tolerance=Math.min(number(c.tolerance_m,'height tolerance',Number.MIN_VALUE),targetHeight===0?.001:targetHeight/4);
  if(tolerance===0)throw Error('Requested height is below representable calibration precision');
  if(!Number.isInteger(c.max_iterations)||c.max_iterations<1||c.max_iterations>20)throw Error('Invalid calibration iteration limit');
  if(!sides.includes(c.boundary))throw Error('Missing forcing boundary');
  const period=number(c.pulse_duration_s,'pulse duration',Number.MIN_VALUE),delay=number(c.pulse_delay_s??0,'pulse delay',0);
  if(period+delay>(config.duration_s??900))throw Error('Pulse exceeds simulation duration');
  if(config.reference_index===undefined)throw Error('Missing coastal reference cell');
  if(config.initial_stage_m!==undefined||config.initial_xmomentum!==undefined||config.initial_ymomentum!==undefined)throw Error('Calibration requires baseline still water');
  if(config.datum_verified===false)throw Error('Terrain vertical datum is unverified');
  for(const side of sides)if(side!==c.boundary&&(config.boundaries?.[side]?.amplitude_m??0)!==0)throw Error('Only one forced side is supported');
  const started=performance.now(),runs=[];
  let last,reason,baselineDrift;
  const progress=data=>onProgress?.({...data,controller:HINTED_CONTROLLER_VERSION});
  const fallback=why=>{
    if(isCancelled?.())throw Error('Cancelled');
    const output=calibrateHeight(config,targetHeight,{isCancelled,onProgress:p=>progress({...p,phase:'fallback'})});
    return {...output,controller:{version:HINTED_CONTROLLER_VERSION,strategy:'ordinary-calibrator-fallback',fallback_reason:why,
      initial_hint:hint??null,hinted_attempt_runs:runs,total_fresh_simulations:runs.length+output.calibration.runs.length,
      elapsed_wall_ms:performance.now()-started,final_field_fresh:true}};
  };
  if(targetHeight===0||!finite(hint?.forcing_amplitude_m)||hint.forcing_amplitude_m<=0||hint.forcing_amplitude_m>maximum)return fallback(targetHeight===0?'zero_target':'no_valid_initial_hint');
  const run=amplitude=>{
    const trial={...config,boundaries:{...config.boundaries,[c.boundary]:{...config.boundaries?.[c.boundary],type:'flather_stage',amplitude_m:amplitude,delay_s:delay,period_s:period}}};
    last=simulate(trial,{isCancelled,onProgress:p=>progress({...p,phase:'hinted',run:runs.length+1,forcing_amplitude_m:amplitude})});
    const achieved=last.actualGaugeHeight;
    runs.push({forcing_amplitude_m:amplitude,achieved_height_m:achieved,elapsed_wall_ms:last.diagnostics.elapsed_wall_ms,steps:last.diagnostics.steps});
    progress({phase:'hinted',run:runs.length,run_complete:true,forcing_amplitude_m:amplitude,achieved_height_m:achieved});
    const sorted=[...runs].sort((a,b)=>a.forcing_amplitude_m-b.forcing_amplitude_m);
    if(sorted.some((r,i)=>i&&r.achieved_height_m+tolerance<sorted[i-1].achieved_height_m))reason='nonmonotone_response';
    if(!finite(achieved)||!finite(last.diagnostics.relative_mass_residual)||last.diagnostics.relative_mass_residual>1e-8)reason='mass_balance_failure';
    return achieved;
  };
  let achieved;
  try{
    achieved=run(0);baselineDrift=last.diagnostics.maximum_depth_change_m;
    if(Math.max(Math.abs(achieved),baselineDrift)>Math.min(.001,tolerance))reason='baseline_not_stationary';
    let used=0,amplitude=hint.forcing_amplitude_m;
    while(!reason&&used<c.max_iterations){
      achieved=run(amplitude);used++;
      if(reason||Math.abs(achieved-targetHeight)<=tolerance)break;
      const freshLow=runs.filter(r=>r.achieved_height_m<targetHeight).sort((a,b)=>b.forcing_amplitude_m-a.forcing_amplitude_m)[0];
      const freshHigh=runs.filter(r=>r.achieved_height_m>=targetHeight).sort((a,b)=>a.forcing_amplitude_m-b.forcing_amplitude_m)[0];
      if(used===1){
        // One local exploratory step uses this NEW response, not historical heights.
        // A small straddling attempt avoids clipping a near-root secant against zero.
        const slope=achieved/amplitude;
        if(!(slope>0)){reason='unusable_fresh_response';break;}
        const increment=Math.max(2*Math.abs(targetHeight-achieved)/slope,.01*amplitude);
        amplitude=Math.min(maximum,Math.max(0,amplitude+Math.sign(targetHeight-achieved)*increment));
      }else if(freshLow&&freshHigh){
        const fraction=(targetHeight-freshLow.achieved_height_m)/(freshHigh.achieved_height_m-freshLow.achieved_height_m);
        amplitude=freshLow.forcing_amplitude_m+(freshHigh.forcing_amplitude_m-freshLow.forcing_amplitude_m)*Math.min(.9,Math.max(.1,fraction));
      }else{
        amplitude=Math.min(maximum,Math.max(.1,amplitude*2));
      }
      if(runs.some(r=>r.forcing_amplitude_m===amplitude)){reason='stalled_fresh_bracket';break;}
    }
  }catch(error){if(isCancelled?.()||error.message==='Cancelled')throw error;return fallback(`hinted_simulation_error: ${error.message}`);}
  if(!reason&&Math.abs(achieved-targetHeight)>tolerance)reason='calibration_iteration_limit';
  if(!reason&&last.boundaryAudit.inundation_reaches_closed_cut_boundary)reason='inundation_reaches_closed_cut_boundary';
  if(!reason&&last.boundaryAudit.supercritical_open_boundary)reason='supercritical_open_boundary';
  if(reason)return fallback(reason);
  return {status:'accepted',reason:null,result:last,calibration:{target_height_m:targetHeight,achieved_height_m:achieved,residual_m:achieved-targetHeight,
    tolerance_m:tolerance,forcing_amplitude_m:runs.at(-1).forcing_amplitude_m,reference_index:config.reference_index,
    baseline_maximum_depth_drift_m:baselineDrift,elapsed_wall_ms:performance.now()-started,cpu_time_ms:null,runs,
    definition:'maximum model coastal stage rise above initial baseline at declared wet reference cell',real_world_validated:false},
    controller:{version:HINTED_CONTROLLER_VERSION,strategy:'historical-initial-guess-fresh-bracket',initial_hint:hint,
      total_fresh_simulations:runs.length,elapsed_wall_ms:performance.now()-started,final_field_fresh:true}};
}
