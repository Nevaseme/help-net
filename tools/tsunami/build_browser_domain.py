"""Export measured node terrain to the separate cell-centered browser SWE model.

This explicit conversion is not an ANUGA result, nor evidence of model validity.
Review remains pending until baseline, boundary and independent solver checks pass.
"""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path

from tools.tsunami.engine import np, validate_scenario


def build_domain(scenario, control):
    from pyproj import Transformer
    s = validate_scenario(scenario)
    if s['kind'] != 'site-scenario' or s['terrain'].get('vertical_datum_status') != 'verified':
        raise ValueError('verified measured site terrain is required')
    g = s['grid']; nx, ny = g['nx'], g['ny']
    z = np.asarray(s['terrain']['bed_m'], dtype=float)
    if z.shape != (ny+1, nx+1):
        raise ValueError('explicit source terrain nodes required')
    # Bilinear field evaluated at regular cell centers. ANUGA independently uses
    # triangular cells and a different reconstruction; do not claim equivalence.
    centers = (z[:-1,:-1]+z[:-1,1:]+z[1:,:-1]+z[1:,1:])/4
    dx, dy = g['width_m']/nx, g['height_m']/ny
    x0, y0 = g['origin_m']
    gx, gy = control['reference_point_m']
    ix, iy = int((gx-x0)//dx), int((gy-y0)//dy)
    if not (0 <= ix < nx and 0 <= iy < ny):
        raise ValueError('reference point outside domain')
    baseline = control['baseline_m']
    if centers[iy,ix] >= baseline:
        raise ValueError('reference cell must initially contain sea water')
    transformer = Transformer.from_crs(g['crs'], 'EPSG:4326', always_xy=True)
    xx, yy = np.meshgrid(x0+np.arange(nx+1)*dx,y0+np.arange(ny+1)*dy)
    lon, lat = transformer.transform(xx.ravel(),yy.ravel())
    boundaries = {}
    for side,b in s['boundaries'].items():
        if b['type'] not in ('reflective','open_still_water','flather_stage','transmissive'):
            raise ValueError('boundary has no browser implementation')
        boundaries[side] = {'type': b['type']}
        if 'dry_edges' in b: boundaries[side]['dry_edges'] = b['dry_edges']
    dry_policy=s['boundaries'][control['boundary']].get('dry_edges')
    boundaries[control['boundary']] = {'type':'flather_stage', **(
        {'dry_edges':dry_policy} if dry_policy in ('reflective','transmissive') else {})}
    config = {'nx':nx,'ny':ny,'dx':dx,'dy':dy,'bed':centers.ravel().tolist(),
        'baseline_m':baseline,'duration_s':s['duration_s'],'manning_n':s['manning_n'],
        'reference_index':iy*nx+ix,'boundaries':boundaries,'cut_boundary_threshold_m':.1,
        'boundary_bed':{'left':((z[:-1,0]+z[1:,0])/2).tolist(),'right':((z[:-1,-1]+z[1:,-1])/2).tolist(),
                        'bottom':((z[0,:-1]+z[0,1:])/2).tolist(),'top':((z[-1,:-1]+z[-1,1:])/2).tolist()},
        'calibration':{key:control[key] for key in ('maximum_forcing_m','tolerance_m','max_iterations','pulse_duration_s','pulse_delay_s','boundary')}}
    provenance = {key:s['terrain'][key] for key in ('source','source_sha256','sources','vertical_datum','vertical_datum_status')}
    implementation = (Path(__file__).resolve().parents[2]/'lab/web/tsunami-solver.mjs').read_bytes()
    version = re.search(r"export const SOLVER_VERSION = '([^']+)'",implementation.decode()).group(1)
    solver = {'name':'PlanD Cartesian shallow-water','version':version,
              'implementation_sha256':hashlib.sha256(implementation).hexdigest()}
    identity = hashlib.sha256(json.dumps({'config':config,'provenance':provenance,'solver':solver},sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()
    return {'schema':'pland-tsunami-browser-domain-1','id':'atami-'+identity[:24],
        'config':config,'solver':solver,'nodes_lonlat':np.column_stack((lon,lat)).tolist(),
        'reference_point':list(transformer.transform(x0+(ix+.5)*dx,y0+(iy+.5)*dy)),
        'provenance':provenance,'review':{'status':'pending','real_world_validated':False},
        'conversion':{'source_grid_crs':g['crs'],'origin_m':g['origin_m'],
            'bed_sampling':'mean of four source nodes; bilinear cell-center elevation',
            'reference_point_requested_m':control['reference_point_m'],
            'reference_point_realised_m':[x0+(ix+.5)*dx,y0+(iy+.5)*dy]}}


def pair_domains(primary, secondary, reporting_bounds):
    """Pair aligned measured domains; keep review pending until evidence is checked.

    Each requested height will be solved twice by the client. The reporting
    window is a declared study area, not a claim that inundation ends there.
    """
    a,b=primary['config'],secondary['config']
    if primary['solver'] != secondary['solver']:
        raise ValueError('same solver implementation required')
    for key in ('dx','dy','baseline_m','duration_s','manning_n','calibration','boundaries'):
        if a[key] != b[key]:raise ValueError('same physical conditions required: '+key)
    if primary['conversion']['source_grid_crs'] != secondary['conversion']['source_grid_crs']:
        raise ValueError('same coordinate system required')
    ax,ay=primary['conversion']['origin_m'];bx,by=secondary['conversion']['origin_m']
    if (ax,ay,a['nx'],a['ny']) == (bx,by,b['nx'],b['ny']):
        raise ValueError('different domain extents required for sensitivity comparison')
    def integer(value):
        result=round(value)
        if abs(value-result)>1e-8:raise ValueError('grid alignment required')
        return result
    ox,oy=integer((ax-bx)/a['dx']),integer((ay-by)/a['dy'])
    x0,y0,x1,y1=reporting_bounds
    window=[integer((x0-ax)/a['dx']),integer((y0-ay)/a['dy']),
            integer((x1-ax)/a['dx']),integer((y1-ay)/a['dy'])]
    if not(0<=window[0]<window[2]<=a['nx'] and 0<=window[1]<window[3]<=a['ny']):
        raise ValueError('reporting window outside primary domain')
    mapping=[]
    for y in range(a['ny']):
        for x in range(a['nx']):
            sx,sy=x+ox,y+oy
            mapping.append(sy*b['nx']+sx if 0<=sx<b['nx'] and 0<=sy<b['ny'] else -1)
    for y in range(window[1],window[3]):
        for x in range(window[0],window[2]):
            i=y*a['nx']+x;j=mapping[i]
            if j<0 or abs(a['bed'][i]-b['bed'][j])>1e-8:
                raise ValueError('reporting window needs identical measured terrain in both domains')
    if mapping[a['reference_index']] != b['reference_index']:
        raise ValueError('same coastal reference cell required')
    result=copy.deepcopy(primary)
    result['primary_domain_id']=primary['id']
    result['reporting_window']=window
    result['comparison']={'domain_id':secondary['id'],'config':copy.deepcopy(b),
        'primary_to_secondary':mapping,'provenance':copy.deepcopy(secondary['provenance']),
        'conversion':copy.deepcopy(secondary['conversion'])}
    result['review']={'status':'pending','real_world_validated':False}
    result['study']={'reporting_bounds_m':list(reporting_bounds),
        'global_inundation_extent_complete':False,
        'boundary_sensitivity_limits':{'mean_wet_depth_difference_m':.1,
            'maximum_land_depth_difference_m':.5,'land_footprint_iou_minimum':.9,
            'wet_depth_threshold_m':.1},
        'interpretation':'Conditional fixed-duration wave in the central reporting area. '
            'Two domain sizes check sensitivity to truncation of the assumed uniform eastern wave source; '
            'this is not validation against an actual tsunami or a complete inundation extent.'}
    identity=hashlib.sha256(json.dumps(result,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()
    result['id']='atami-pair-'+identity[:24]
    return result


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('scenario',type=Path);p.add_argument('control',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args()
    result=build_domain(json.loads(a.scenario.read_text(encoding='utf-8-sig')),json.loads(a.control.read_text(encoding='utf-8-sig')))
    with a.output.open('x',encoding='utf-8') as f:json.dump(result,f,separators=(',',':'),ensure_ascii=False,allow_nan=False)
    print(json.dumps({'id':result['id'],'status':'pending','cells':result['config']['nx']*result['config']['ny']}))
