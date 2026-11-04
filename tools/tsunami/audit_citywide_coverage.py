"""Audit exact ALB node availability against the authoritative Atami boundary.

No missing node is filled and no wave is simulated. The municipality mask is a
point sample of N03 polygons at 20 m source nodes, not an exact land-area measure.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
DEPENDENCIES = ROOT / '.implementation' / 'tsunami-deps'
if DEPENDENCIES.is_dir():
    sys.path.insert(0, str(DEPENDENCIES))
os.environ.setdefault('MPLCONFIGDIR', str(ROOT / '.implementation' / 'tsunami-mpl'))
import numpy as np
from pyproj import Transformer
from matplotlib.path import Path as PolygonPath


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    started = time.monotonic()
    p = args.directory
    acquisition = json.loads((p / 'acquisition.json').read_text())
    west, south, east, north = acquisition['bounds_m']
    spacing = acquisition['spacing_m']
    nx, ny = round((east-west)/spacing), round((north-south)/spacing)
    xx, yy = np.meshgrid(west+np.arange(nx+1)*spacing, south+np.arange(ny+1)*spacing)
    bed = np.full((ny+1, nx+1), np.nan)
    for record in acquisition['sources']:
        xyz = np.load(p/(Path(record['path']).stem+'-20m.npz'))['xyz']
        ij = np.rint((xyz[:, :2]-[west, south])/spacing).astype(int)
        keep = (ij[:, 0]>=0)&(ij[:, 0]<=nx)&(ij[:, 1]>=0)&(ij[:, 1]<=ny)
        ij, z = ij[keep], xyz[keep, 2]
        old = bed[ij[:, 1], ij[:, 0]]
        if np.any(np.isfinite(old)&(np.abs(old-z)>1e-9)):
            raise ValueError('Conflicting overlapping exact source nodes')
        bed[ij[:, 1], ij[:, 0]] = z
    data = json.loads((p/'atami-boundary.geojson').read_text(encoding='utf8'))
    transform = Transformer.from_crs('EPSG:6668', 'EPSG:6676', always_xy=True)
    points = np.column_stack((xx.ravel(), yy.ravel()))
    inside = np.zeros(len(points), dtype=bool)
    rings = []
    for feature in data['features']:
        geometry = feature['geometry']
        polygons = [geometry['coordinates']] if geometry['type']=='Polygon' else geometry['coordinates']
        for polygon in polygons:
            selected = np.zeros(len(points), dtype=bool)
            for index, ring in enumerate(polygon):
                lonlat = np.array(ring)
                xy = np.column_stack(transform.transform(lonlat[:, 0], lonlat[:, 1]))
                rings.append(xy)
                hit = PolygonPath(xy).contains_points(points)
                if index == 0:
                    selected = hit
                else:
                    selected &= ~hit
            inside |= selected
    inside = inside.reshape(bed.shape)
    vertices = np.concatenate(rings)
    city_bounds = [float(vertices[:,0].min()), float(vertices[:,1].min()), float(vertices[:,0].max()), float(vertices[:,1].max())]
    if city_bounds[0]<west or city_bounds[1]<south or city_bounds[2]>east or city_bounds[3]>north:
        raise ValueError('Acquisition envelope does not contain the whole city')
    valid = np.isfinite(bed)
    cell_valid = valid[:-1,:-1]&valid[1:,:-1]&valid[:-1,1:]&valid[1:,1:]
    city_nodes = int(inside.sum())
    missing_city = int((inside&~valid).sum())
    # Smallest grid-aligned rectangle containing all N03 city vertices. Its sea
    # between Hatsushima and the mainland cannot be omitted by a rectangular SWE.
    i0,j0=np.floor((np.array(city_bounds[:2])-[west,south])/spacing).astype(int)
    i1,j1=np.ceil((np.array(city_bounds[2:])-[west,south])/spacing).astype(int)
    city_rect = valid[j0:j1+1,i0:i1+1]
    rect_cells = int((i1-i0)*(j1-j0))
    # A measured seabed connection is absent if there is no 4-neighbor path
    # through present below-TP0 samples from the preserved downtown gauge.
    gauge_xy = [52660.5,-100209.5]
    gi,gj=np.rint((np.array(gauge_xy)-[west,south])/spacing).astype(int)
    wet=valid&(bed<0)
    from collections import deque
    connected=np.zeros(wet.shape,dtype=bool)
    queue=deque([(gj,gi)]) if wet[gj,gi] else deque()
    if queue: connected[gj,gi]=True
    while queue:
        y,x=queue.popleft()
        for dy,dx in ((-1,0),(1,0),(0,-1),(0,1)):
            a,b=y+dy,x+dx
            if 0<=a<=ny and 0<=b<=nx and wet[a,b] and not connected[a,b]:
                connected[a,b]=True; queue.append((a,b))
    regions = {
        'izumi_izusan_north': [52800.5,-98999.5,55600.5,-94799.5],
        'central_atami': [50800.5,-102599.5,54000.5,-98399.5],
        'taga': [50800.5,-106199.5,54000.5,-102599.5],
        'ajiro_south': [51600.5,-109199.5,55200.5,-105599.5],
        'hatsushima': [60000.5,-107399.5,62400.5,-105299.5],
    }
    regional=[]
    for name,bounds in regions.items():
        mask=(xx>=bounds[0])&(xx<=bounds[2])&(yy>=bounds[1])&(yy<=bounds[3])
        regional.append({'name':name,'diagnostic_bounds_m':bounds,'required_rectangle_nodes':int(mask.sum()),
                         'present_nodes':int((mask&valid).sum()),'missing_nodes':int((mask&~valid).sum()),
                         'present_below_tp0_nodes':int((mask&wet).sum()),
                         'below_tp0_nodes_connected_to_original_gauge':int((mask&connected).sum()),
                         'municipal_nodes':int((mask&inside).sum()),
                         'municipal_nodes_missing_alb':int((mask&inside&~valid).sum())})
    report = {
        'schema':'pland-citywide-terrain-coverage-1', 'status':'blocked_before_wave_calculation',
        'source_tiles':len(acquisition['sources']), 'source_bytes':acquisition['all_source_bytes'],
        'new_download_bytes':acquisition['downloaded_bytes'],
        'acquisition_sha256':hashlib.sha256((p/'acquisition.json').read_bytes()).hexdigest(),
        'boundary_geojson_sha256':hashlib.sha256((p/'atami-boundary.geojson').read_bytes()).hexdigest(),
        'municipality_code':'22205','municipal_bounds_epsg6676':city_bounds,
        'all_municipal_vertices_inside_acquisition_envelope':True,
        'spacing_m':spacing,'municipal_node_count':city_nodes,
        'municipal_nodes_present':city_nodes-missing_city,'municipal_nodes_missing':missing_city,
        'municipal_node_coverage_fraction':(city_nodes-missing_city)/city_nodes,
        'full_city_rectangle':{'bounds_m':[west+i0*spacing,south+j0*spacing,west+i1*spacing,south+j1*spacing],
                               'cells':rect_cells,'required_nodes':int(city_rect.size),
                               'present_nodes':int(city_rect.sum()),'missing_nodes':int((~city_rect).sum()),
                               'native_mesh_triangle_count':rect_cells*4,'native_triangle_limit':250000,
                               'browser_cell_limit':1000000,'passes_exact_terrain_requirement':bool(city_rect.all())},
        'regional_diagnostics':regional,'original_gauge_m':gauge_xy,
        'connectivity_reference_node_m':[float(west+gi*spacing),float(south+gj*spacing)],
        'connectivity_test':'4-neighbor exact 20m source nodes with z<0, starting at the nearest source node to the requested gauge; this diagnostic is not a hydrodynamic connectivity proof',
        'wave_cases_run':0,'missing_node_fill_used':False,'published_bundle_modified':False,
        'limitations':['ALB-only coverage; absent inland data may be supplied by separately verified LP terrain.',
                       'Absence in this catalog is not a claim that no other bathymetry exists.',
                       'Region boxes are diagnostic windows, not exhaustive municipal subdivisions.',
                       '20m exact-node sampling can lose narrow channels and coastal structures.',
                       'Independent regional gauges would change the meaning of the input coastal height.'],
        'required_next':['Acquire and reconcile measured offshore bathymetry connecting mainland and Hatsushima, plus missing inland terrain.',
                         'Use an adequate regional mesh and common wave forcing while retaining the original gauge, or explicitly approve local-reference semantics.',
                         'Run two-domain checks and all 36 heights; no publication until all pass.'],
        'audit_elapsed_s':time.monotonic()-started}
    (p/'coverage-audit.json').write_text(json.dumps(report,indent=2),encoding='utf8')
    np.savez_compressed(p/'citywide-coverage-20m.npz',bed=bed,municipal_mask=inside,connected_wet_nodes=connected,bounds=np.array(acquisition['bounds_m']))
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.colors import ListedColormap
    fig,ax=plt.subplots(figsize=(10,10))
    image=np.where(~valid,np.nan,np.where(bed<0,0,1))
    ax.imshow(image,origin='lower',extent=[west/1000,east/1000,south/1000,north/1000],cmap=ListedColormap(['#42a5cf','#e0bb86']),vmin=0,vmax=1,interpolation='nearest')
    for ring in rings: ax.plot(ring[:,0]/1000,ring[:,1]/1000,color='#222222',linewidth=.6)
    ax.plot(gauge_xy[0]/1000,gauge_xy[1]/1000,'r*',markersize=10,label='Preserved coastal reference')
    for name,b in regions.items(): ax.text((b[0]+b[2])/2000,(b[1]+b[3])/2000,name.replace('_',' '),fontsize=8)
    ax.set(title='Atami citywide terrain audit (20 m exact source nodes)\nBlue: ALB below TP0 | tan: ALB land | white: missing | black: N03 city boundary',xlabel='EPSG:6676 easting (km)',ylabel='EPSG:6676 northing (km)')
    ax.legend(loc='upper left');fig.tight_layout();fig.savefig(p/'coverage-audit.png',dpi=170);plt.close(fig)
    print(json.dumps(report,indent=2))


if __name__ == '__main__':
    main()
