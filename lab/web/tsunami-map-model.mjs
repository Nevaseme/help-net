const check=(ok,text)=>{if(!ok)throw Error(text);};
const finite=x=>typeof x==='number'&&Number.isFinite(x);
const point=p=>Array.isArray(p)&&p.length===2&&p.every(finite)&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;
const polygon=g=>g?.type==='Polygon'&&Array.isArray(g.coordinates)&&g.coordinates.length===1&&g.coordinates[0].length>=4&&g.coordinates[0].every(point)&&g.coordinates[0][0].every((x,i)=>x===g.coordinates[0].at(-1)[i]);
export function validateTsunamiMap(data){
 check(data?.schema==='pland-tsunami-map-1'&&Array.isArray(data.cases)&&data.cases.length>0,'津波結果の形式を確認できません');
 const ids=new Set();
 for(const c of data.cases){
  check(typeof c.id==='string'&&!ids.has(c.id),'津波結果のIDが重複しています');ids.add(c.id);
  for(const key of ['target_height_m','achieved_height_m','grid_spacing_m','duration_s','threshold_m','inundated_area_m2','max_ground_depth_m'])check(finite(c[key])&&c[key]>=0,'津波結果の数値が不正です');
  check(c.grid_spacing_m>0&&c.threshold_m>0&&finite(c.residual_m)&&Math.abs(c.achieved_height_m-c.target_height_m-c.residual_m)<1e-8,'海岸の波高が一致しません');
  check(polygon(c.coverage)&&c.reference?.type==='Point'&&point(c.reference.coordinates),'計算範囲の座標が不正です');
  check(c.features?.type==='FeatureCollection'&&Array.isArray(c.features.features),'浸水範囲がありません');
  check(finite(c.conditions?.baseline_m)&&Array.isArray(c.limitations)&&c.limitations.length>0,'計算条件がありません');
  for(const f of c.features.features){const p=f.properties;check(polygon(f.geometry)&&finite(p?.max_depth_m)&&p.max_depth_m>=c.threshold_m&&finite(p?.bed_elevation_m)&&p.bed_elevation_m>=c.conditions.baseline_m,'表示対象に海域・欠損・閾値未満のセルがあります');}
 }
 check(ids.has(data.default_case_id),'既定の津波結果がありません');return data;
}
export function tsunamiDepthColor(depth){if(!finite(depth)||depth<.01)return null;return depth<.5?'#ffffb2':depth<1?'#fed976':depth<2?'#feb24c':depth<3?'#fd8d3c':'#e31a1c';}
export function insideTsunamiCoverage([x,y],geometry){let inside=false;const ring=geometry.coordinates[0];for(let i=0,j=ring.length-1;i<ring.length;j=i++){const [xi,yi]=ring[i],[xj,yj]=ring[j];if((yi>y)!==(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;}return inside;}
