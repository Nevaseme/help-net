/** Pure adapter for immutable ANUGA bundles. No fetch, storage, interpolation or hazard classes.
 * Inputs scenario/mesh/summary are original UTF-8 JSON texts, not reserialized objects:
 * their hashes bind triangle ordering and conditions to the supplied manifest.
 * Hash consistency is not proof that the manifest publisher or coastal model is trustworthy.
 */
const requireValue=(ok,message)=>{if(!ok)throw new Error(message);};
const finite=(x,name,min=-Infinity)=>requireValue(typeof x==='number'&&Number.isFinite(x)&&x>=min,`Invalid ${name}`);
const text=(x,name)=>requireValue(typeof x==='string'&&x.trim().length>0,`Missing ${name}`);
const hash=x=>typeof x==='string'&&/^[a-f0-9]{64}$/.test(x);
const pair=(p,name)=>{requireValue(Array.isArray(p)&&p.length===2,`Invalid ${name}`);p.forEach(x=>finite(x,name));};
const array=(a,length,name)=>requireValue(Array.isArray(a)&&a.length===length,`Mismatched ${name}`);
const collection=features=>({type:'FeatureCollection',features});

async function readJson(raw,record,file) {
 requireValue(record?.file===file&&hash(record.sha256)&&Number.isSafeInteger(record.bytes)&&record.bytes>0,`Invalid ${file} manifest reference`);
 requireValue(typeof raw==='string',`Missing ${file} JSON text`);
 const bytes=new TextEncoder().encode(raw);
 requireValue(bytes.length===record.bytes,`Mismatched ${file} bytes`);
 const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');
 requireValue(digest===record.sha256,`Mismatched ${file} sha256`);
 return JSON.parse(raw);
}

/** Return all initially-dry cells (including known zero) plus their positive-depth subset.
 * initially_dry is the solver's initial-depth < arrival_depth_m mask, not a surveyed
 * land/sea boundary. Missing cells reject the entire bundle; outside this mesh is unknown.
 */
export async function parseTsunamiResult(input) {
 const m=input?.manifest;
 requireValue(m?.schema==='pland-tsunami-result-1','Unsupported result schema');
 text(m.id,'scenario id');text(m.title,'title');
 requireValue(['benchmark','site-scenario'].includes(m.kind),'Invalid scenario kind');
 requireValue(m.solver?.name==='ANUGA'&&m.solver?.algorithm==='DE1','Unsupported solver');text(m.solver.version,'solver version');
 requireValue(m.units?.depth==='m'&&m.units?.momentum==='m2/s'&&m.units?.time==='s'&&m.units?.coordinates==='m','Unsupported units');
 requireValue(m.validation?.status==='experimental-unvalidated'&&m.validation.real_world_validated===false,'Unsupported validation claim');
 requireValue(Array.isArray(m.limitations)&&m.limitations.length>0,'Missing limitations');m.limitations.forEach(x=>text(x,'limitation'));
 finite(m.arrival_depth_m,'arrival depth',Number.MIN_VALUE);
 const [s,mesh,summary]=await Promise.all([readJson(input.scenario,m.scenario,'scenario.json'),readJson(input.mesh,m.mesh,'mesh.json'),readJson(input.summary,m.summary_json,'summary.json')]);
 requireValue(s.schema==='pland-tsunami-scenario-1'&&s.id===m.id&&s.kind===m.kind&&s.title===m.title,'Mismatched scenario');
 // Raw rejected runs remain on disk for diagnosis; they must not enter the
 // display/export path merely because the gauge reached the requested height.
 requireValue(!['rejected_boundary_screen','failed_not_accepted'].includes(s.publication_status),
  'Rejected calculation cannot be used as a display result');
 requireValue(s.boundary_audit?.inundation_reaches_closed_cut_boundary!==true&&
  (s.boundary_audit?.supercritical_characteristic_boundary_observed??s.boundary_audit?.supercritical_open_boundary_observed)!==true&&
  s.boundary_audit?.supercritical_open_boundary_observed_in_frames!==true,
  'Boundary audit rejected this calculation for display');
 text(s.forcing_source,'forcing source');text(s.terrain?.source,'terrain source');text(s.terrain?.vertical_datum,'vertical datum');
 requireValue(['synthetic','measured'].includes(s.terrain.kind),'Invalid terrain kind');
 if(s.kind==='site-scenario') {
  text(s.source_reference,'wave source reference');
  requireValue(s.terrain.kind==='measured'&&s.terrain.vertical_datum_status==='verified'&&hash(s.terrain.source_sha256),'Unverified site terrain provenance');
  requireValue(Array.isArray(s.terrain.sources)&&s.terrain.sources.length>0,'Missing terrain sources');
  for(const source of s.terrain.sources) requireValue(/^https?:\/\//.test(source.url)&&hash(source.sha256),'Invalid terrain source URL/hash');
 }
 requireValue(s.grid?.crs===mesh.crs&&mesh.crs!=='LOCAL_METRES','Mismatched geographic mesh CRS');text(mesh.crs,'CRS');
 requireValue(s.arrival_depth_m===m.arrival_depth_m,'Mismatched arrival threshold');
 finite(s.duration_s,'duration',Number.MIN_VALUE);finite(s.manning_n,'roughness',0);
 requireValue(s.boundaries&&typeof s.boundaries==='object','Missing boundary conditions');requireValue(s.initial&&typeof s.initial==='object','Missing initial conditions');
 const c=s.coastal_control;
 requireValue(c?.schema==='pland-coastal-control-1'&&c.real_world_validated===false,'Missing experimental coastal control');text(c.definition,'coastal height definition');
 for(const key of ['target_height_m','achieved_height_m','forcing_amplitude_m','pulse_delay_s'])finite(c[key],key,0);
 finite(c.baseline_m,'baseline');finite(c.residual_m,'residual');finite(c.tolerance_m,'tolerance',Number.MIN_VALUE);finite(c.pulse_duration_s,'pulse duration',Number.MIN_VALUE);
 requireValue(Math.abs(c.achieved_height_m-c.target_height_m-c.residual_m)<=1e-9&&Math.abs(c.residual_m)<=c.tolerance_m+1e-9,'Inconsistent coastal calibration');
 requireValue(['left','right','top','bottom'].includes(c.boundary),'Missing inlet direction');text(c.pulse_shape,'pulse shape');pair(c.reference_point_m,'reference point');pair(c.realised_reference_point_m,'realised reference point');
 requireValue(Array.isArray(mesh.nodes_lonlat)&&mesh.nodes_lonlat.length>=3,'Missing geographic vertices');
 for(const p of mesh.nodes_lonlat){pair(p,'lonlat');requireValue(Math.abs(p[0])<=180&&Math.abs(p[1])<=90,'Invalid lonlat bounds');}
 requireValue(Array.isArray(mesh.triangles)&&mesh.triangles.length>0,'Missing triangles');const count=mesh.triangles.length;
 array(summary.max_depth_m,count,'max_depth_m');array(summary.initially_dry,count,'initially_dry');array(summary.arrival_time_s,count,'arrival_time_s');array(mesh.bed_elevation_m,count,'bed elevations');
 for(let i=0;i<count;i++) {
  finite(summary.max_depth_m[i],'max_depth_m',0);finite(mesh.bed_elevation_m[i],'bed elevation');
  requireValue(typeof summary.initially_dry[i]==='boolean','Invalid initially_dry');
  if(summary.arrival_time_s[i]!==null){finite(summary.arrival_time_s[i],'arrival time',0);requireValue(summary.arrival_time_s[i]<=s.duration_s,'Arrival after simulation');}
 }
 requireValue(Number.isSafeInteger(c.reference_triangle)&&c.reference_triangle>=0&&c.reference_triangle<count&&!summary.initially_dry[c.reference_triangle],'Invalid coastal reference triangle');
 // summary depths are rounded to one micrometre by engine.write_bundle.
 requireValue(Math.abs(summary.max_depth_m[c.reference_triangle]+mesh.bed_elevation_m[c.reference_triangle]-c.baseline_m-c.achieved_height_m)<=1.000001e-6,'Mismatched achieved coastal height');
 const features=[];
 for(let i=0;i<count;i++) {
  const t=mesh.triangles[i];array(t,3,'triangle');requireValue(new Set(t).size===3&&t.every(n=>Number.isSafeInteger(n)&&n>=0&&n<mesh.nodes_lonlat.length),'Invalid triangle indices');
  const points=t.map(n=>[...mesh.nodes_lonlat[n]]),[a,b,d]=points;
  const signed=(b[0]-a[0])*(d[1]-a[1])-(d[0]-a[0])*(b[1]-a[1]);requireValue(signed!==0,'Degenerate triangle');
  if(!summary.initially_dry[i])continue;
  if(signed<0)points.reverse();points.push([...points[0]]);
  features.push({type:'Feature',id:i,properties:{scenario_id:m.id,triangle_index:i,bed_elevation_m:mesh.bed_elevation_m[i],max_depth_m:summary.max_depth_m[i],initially_dry:true,arrival_time_s:summary.arrival_time_s[i]},geometry:{type:'Polygon',coordinates:[points]}});
 }
 const {bed_m,...terrain}=s.terrain;
 return {land:collection(features),inundation:collection(features.filter(f=>f.properties.max_depth_m>0)),metadata:structuredClone({id:m.id,title:m.title,kind:m.kind,solver:m.solver,validation:m.validation,limitations:m.limitations,terrain,forcing_source:s.forcing_source,source_reference:s.source_reference??null,grid:s.grid,initial:s.initial,boundaries:s.boundaries,duration_s:s.duration_s,manning_n:s.manning_n,arrival_depth_m:m.arrival_depth_m,coastal_control:c,triangle_count:count,land_mask_definition:'initial depth below arrival_depth_m; not a surveyed coastline',coverage:'only declared mesh cells; outside is unknown'})};
}

/** Explicit display extraction; the legacy initially_dry collections stay unchanged.
 * Ground is a model elevation test, not a measured coastline or observed damage area.
 * Zero/subthreshold depths remain in ground; absent display polygons do not make
 * locations outside the computed domain dry or safe. No rounding or new threshold.
 */
export function extractGroundInundation(result) {
 const baseline=result?.metadata?.coastal_control?.baseline_m,threshold=result?.metadata?.arrival_depth_m;
 finite(baseline,'initial water level');finite(threshold,'display depth threshold',Number.MIN_VALUE);
 requireValue(result?.land?.type==='FeatureCollection'&&Array.isArray(result.land.features),'Missing initially-dry model cells');
 const ground=[];
 for(const feature of result.land.features) {
  const p=feature?.properties;
  finite(p?.bed_elevation_m,'cell bed elevation');finite(p?.max_depth_m,'cell maximum depth',0);
  requireValue(p.initially_dry===true,'Expected initially-dry model cell');
  if(p.bed_elevation_m>=baseline)ground.push(feature);
 }
 return {ground:collection(ground),inundation:collection(ground.filter(f=>f.properties.max_depth_m>=threshold)),metadata:{...structuredClone(result.metadata),initial_water_level_m:baseline,minimum_max_depth_m:threshold,definition:'initially-dry model cells with bed_elevation_m >= initial_water_level_m and max_depth_m >= minimum_max_depth_m',surveyed_coastline:false,outside_domain:'unknown'}};
}

/** An exact target match only. Filter by site/conditions before calling; never pick
 * a nearest height or silently choose between differing scenarios at the same height. */
export function selectTsunamiResult(results,heightM) {
 finite(heightM,'requested coastal height',0);requireValue(Array.isArray(results),'Expected computed results');
 const heights=results.map(r=>{const h=r?.metadata?.coastal_control?.target_height_m;finite(h,'computed target height',0);return h;});
 const matches=results.filter((_,i)=>heights[i]===heightM);
 if(matches.length===1)return {status:'available',requested_height_m:heightM,result:matches[0]};
 return {status:'unavailable',reason:matches.length?'ambiguous_conditions':'not_computed',requested_height_m:heightM,available_heights_m:[...new Set(heights)].sort((a,b)=>a-b)};
}
