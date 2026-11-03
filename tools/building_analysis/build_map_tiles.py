"""Offline MVT pyramid. No model fitting; uses the shipped, verified assessment data.

PYTHONPATH=.implementation/map-python python tools/building_analysis/build_map_tiles.py
"""
import collections
import gzip
import hashlib
import json
import math
from pathlib import Path
import sqlite3
import mapbox_vector_tile
from shapely import from_wkb, make_valid
from shapely.geometry import MultiPolygon, Polygon, box
from tile_quantization import EXTENT, quantized_polygons

ASSETS = Path('android/app/src/main/assets')

def project(lon, lat):
    return (lon + 180) / 360, (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2

def tile_range(bounds, z):
    n = 2 ** z
    ax, ay, bx, by = bounds
    for x in range(max(0, math.floor(ax*n)), min(n-1, math.floor(bx*n)) + 1):
        for y in range(max(0, math.floor(ay*n)), min(n-1, math.floor(by*n)) + 1):
            yield x, y

def polygons(g):
    if g.geom_type == 'Polygon': return [g]
    if hasattr(g, 'geoms'): return [p for c in g.geoms for p in polygons(c)]
    return []

def tile_geometry(rows, z, x, y, layer='buildings'):
    n = 2 ** z
    # Buffer prevents cracks at tile edges. Coordinates use downward-positive Mercator y.
    bounds = (x/n, y/n, (x+1)/n, (y+1)/n)
    clip = box((x-.002)/n, (y-.002)/n, (x+1.002)/n, (y+1.002)/n)
    features = []
    for bid, geom, props in rows:
        parts = quantized_polygons(geom.intersection(clip), bounds)
        if not parts: continue
        g = parts[0] if len(parts)==1 else MultiPolygon(parts)
        features.append({'geometry':g, 'properties':dict(props, bid=bid)})
    return mapbox_vector_tile.encode({'name':layer, 'features':features}, default_options={
        'extents':EXTENT, 'y_coord_down':True,
    })

def build(source=None,dest=None,staging=None,attribution=None):
    source = source or ASSETS/'kyoto-building-assessment'
    dest = dest or ASSETS/'kyoto-risk-tiles'
    dest.mkdir(exist_ok=True)
    index = json.loads((source/'index.json').read_text(encoding='utf-8'))
    model_bytes=(source/'model.json').read_bytes()
    assert hashlib.sha256(model_bytes).hexdigest()==index['modelSha256']
    model=json.loads(model_bytes)
    curves={p:c['full'] for p,c in model['curves'].items()}
    dbpath=staging or Path('.implementation/map-staging.sqlite');dbpath.parent.mkdir(exist_ok=True)
    db=sqlite3.connect(dbpath)
    db.executescript('DROP TABLE IF EXISTS buildings; DROP TABLE IF EXISTS bounds; CREATE TABLE buildings(id INTEGER PRIMARY KEY,bid TEXT,geom BLOB,props TEXT); CREATE VIRTUAL TABLE bounds USING rtree(id,minx,maxx,miny,maxy);')
    tiles={z:set() for z in range(14,17)}
    count=0; no_geometry=0; repaired=0; profiles=collections.Counter()
    for j,chunk in enumerate(index['chunks']):
        packed=(source/chunk['file']).read_bytes()
        assert hashlib.sha256(packed).hexdigest()==chunk['sha256']
        for r in json.loads(gzip.decompress(packed))['buildings']:
            count+=1
            if not r.get('geometry'): no_geometry+=1;continue
            parts=[Polygon([project(*pt) for pt in rings[0]], [[project(*pt) for pt in ring] for ring in rings[1:]]) for rings in r['geometry']['coordinates'] if rings]
            geom=MultiPolygon(parts)
            if not geom.is_valid: geom=make_valid(geom);repaired+=1
            ps=polygons(geom)
            if not ps: no_geometry+=1;continue
            geom=MultiPolygon(ps)
            profile=r['profile'] or '?';profiles[profile]+=1
            a=r['attributes']
            props={'p':profile,'s':r['status'],'year':a.get('year') or 0,'floors':a.get('floors') or 0,'structure':a.get('structureCode') or ''}
            db.execute('INSERT INTO buildings VALUES(?,?,?,?)',(count,r['id'],geom.wkb,json.dumps(props)))
            minx,miny,maxx,maxy=geom.bounds
            db.execute('INSERT INTO bounds VALUES(?,?,?,?,?)',(count,minx,maxx,miny,maxy))
            for z in tiles: tiles[z].update(tile_range(geom.bounds,z))
        db.commit()
        if (j+1)%40==0:print('indexed',j+1,'meshes',count,'buildings',flush=True)
    assert count==sum(c['count'] for c in index['chunks'])
    outputs=[]
    def save(z,x,y,data):
        rel=f'{z}/{x}/{y}.pbf'; p=dest/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
        outputs.append({'file':rel,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    for z in tiles:
        for x,y in sorted(tiles[z]):
            n=2**z
            rows=db.execute('SELECT bid,geom,props FROM buildings JOIN bounds USING(id) WHERE maxx>=? AND minx<=? AND maxy>=? AND miny<=?',((x-.002)/n,(x+1.002)/n,(y-.002)/n,(y+1.002)/n))
            save(z,x,y,tile_geometry(((bid,from_wkb(g),json.loads(p)) for bid,g,p in rows),z,x,y))
        print('encoded zoom',z,len(tiles[z]),'tiles',flush=True)
    bounds=[min(c['bbox'][i] for c in index['chunks']) if i<2 else max(c['bbox'][i] for c in index['chunks']) for i in range(4)]
    from footprint_overview import build_overview, EXTENT as OVERVIEW_EXTENT
    overview_stats=build_overview(db,save,bounds)
    # Missing tiles in the small rectangular envelope are valid empty tiles, avoiding fallback/error churn.
    for z in range(14,17):
        existing={(int(o['file'].split('/')[1]),int(o['file'].split('/')[2].split('.')[0])) for o in outputs if o['file'].startswith(f'{z}/')}
        for x in range(min(x for x,y in existing),max(x for x,y in existing)+1):
            for y in range(min(y for x,y in existing),max(y for x,y in existing)+1):
                if (x,y) not in existing:save(z,x,y,tile_geometry([],z,x,y,'overview' if z<14 else 'buildings'))
    bounds=[min(c['bbox'][i] for c in index['chunks']) if i<2 else max(c['bbox'][i] for c in index['chunks']) for i in range(4)]
    tilejson={'tilejson':'2.2.0','tiles':['asset://'+dest.name+'/{z}/{x}/{y}.pbf'],'minzoom':8,'maxzoom':16,'bounds':bounds,'attribution':attribution or '京都市・国土交通省 PLATEAU / 内閣府 / PlanD独自試算'}
    (dest/'tiles.json').write_text(json.dumps(tilejson,ensure_ascii=False),encoding='utf-8')
    metadata={'buildings':count,'geometryBuildings':count-no_geometry,'withoutGeometry':no_geometry,'repairedGeometry':repaired,'profileCounts':profiles,'modelSha256':index['modelSha256'],'sourceIndexSha256':hashlib.sha256((source/'index.json').read_bytes()).hexdigest(),'tiles':outputs,'bytes':sum(t['bytes'] for t in outputs),'overview':'simplified individual footprints grouped by assessment profile; no area average; sub-0.01-pixel-squared shapes omitted','overviewStats':overview_stats,'overviewExtent':OVERVIEW_EXTENT,'extent':EXTENT}
    (dest/'manifest.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
    db.close()
    print({k:v for k,v in metadata.items() if k not in ('tiles','profileCounts')},flush=True)

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path);parser.add_argument('--dest',type=Path);parser.add_argument('--staging',type=Path);parser.add_argument('--attribution')
    args=parser.parse_args();build(args.source,args.dest,args.staging,args.attribution)
