"""Simplified building footprints, grouped by assessment profile, never rectangular cells."""
import collections
import json
from pathlib import Path
from shapely import from_wkb
from shapely.geometry import MultiPolygon, box
import mapbox_vector_tile
from tile_quantization import EXTENT, quantized_polygons

def parts(geometry):
    if geometry.geom_type == 'Polygon': return [geometry]
    if hasattr(geometry, 'geoms'): return [p for g in geometry.geoms for p in parts(g)]
    return []

def simplify_footprint(geometry, zoom):
    scale = 512 * 2**zoom
    # Less than 1/100 of a logical map pixel has no useful individual silhouette.
    if geometry.area * scale * scale < .01: return []
    simplified = geometry.simplify(.2 / scale, preserve_topology=True)
    # Simplification must not bridge courtyards, streets or vacant land outside the building.
    return parts(simplified.intersection(geometry))

def encode_footprints(rows, zoom, x, y):
    n = 2**zoom
    clip = box(x/n, y/n, (x+1)/n, (y+1)/n)
    groups = collections.defaultdict(list)
    count = 0
    for geometry, profile in rows:
        simplified = simplify_footprint(geometry, zoom)
        polygons = [p for g in simplified for p in quantized_polygons(g.intersection(clip),clip.bounds)]
        if polygons:
            count += 1
            groups[profile].extend(polygons)
    # Bounded geometry per feature prevents hit testing from returning a city-sized object.
    features = [{'geometry':MultiPolygon(polygons[i:i+128]), 'properties':{'p':profile}}
        for profile,polygons in sorted(groups.items()) for i in range(0,len(polygons),128)]
    data = mapbox_vector_tile.encode({'name':'footprints','features':features},default_options={
        'extents':EXTENT, 'y_coord_down':True,
    })
    return data, count

def build_overview(db, save, bounds, zooms=range(8,14)):
    from build_map_tiles import project, tile_range
    west,south,east,north=bounds
    a=project(west,north);b=project(east,south)
    stats={}
    for zoom in zooms:
        tiles=0;count=0
        for x,y in tile_range((*a,*b),zoom):
            n=2**zoom
            rows=db.execute('SELECT geom,props FROM buildings JOIN bounds USING(id) WHERE maxx>=? AND minx<=? AND maxy>=? AND miny<=?',(x/n,(x+1)/n,y/n,(y+1)/n))
            data,visible=encode_footprints(((from_wkb(g),json.loads(p)['p']) for g,p in rows),zoom,x,y)
            save(zoom,x,y,data);count+=visible;tiles+=1
        stats[str(zoom)]={'tiles':tiles,'simplifiedBuildingTileOccurrences':count}
        print('footprint overview',zoom,stats[str(zoom)],flush=True)
    return stats

def replace_existing_overview():
    """Reuses the preceding, verified source projection; leaves detailed tiles unchanged."""
    import hashlib, sqlite3
    from build_map_tiles import ASSETS
    dest=ASSETS/'kyoto-risk-tiles';source=ASSETS/'kyoto-building-assessment'
    manifest=json.loads((dest/'manifest.json').read_text(encoding='utf-8'))
    assert hashlib.sha256((source/'index.json').read_bytes()).hexdigest()==manifest['sourceIndexSha256']
    assert hashlib.sha256((source/'model.json').read_bytes()).hexdigest()==manifest['modelSha256']
    db=sqlite3.connect('file:.implementation/map-staging.sqlite?mode=ro',uri=True)
    assert db.execute('SELECT count(*) FROM buildings').fetchone()[0]==manifest['geometryBuildings']
    # Confirm staging still contains exactly the ID/profile pairs audited in the previous build.
    import gzip
    expected={}
    index=json.loads((source/'index.json').read_text(encoding='utf-8'))
    for chunk in index['chunks']:
        packed=(source/chunk['file']).read_bytes()
        assert hashlib.sha256(packed).hexdigest()==chunk['sha256']
        for row in json.loads(gzip.decompress(packed))['buildings']:
            expected[row['id']]=row['profile'] or '?'
    actual={bid:json.loads(props)['p'] for bid,props in db.execute('SELECT bid,props FROM buildings')}
    assert actual==expected
    del actual,expected
    outputs=[t for t in manifest['tiles'] if int(t['file'].split('/')[0])>=14]
    def save(z,x,y,data):
        rel=f'{z}/{x}/{y}.pbf';p=dest/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
        outputs.append({'file':rel,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    bounds=json.loads((dest/'tiles.json').read_text(encoding='utf-8'))['bounds']
    manifest['overviewStats']=build_overview(db,save,bounds)
    manifest['tiles']=outputs;manifest['bytes']=sum(t['bytes'] for t in outputs)
    manifest['overview']='simplified individual footprints grouped by assessment profile; no area average; sub-0.01-pixel-squared shapes omitted'
    manifest['overviewExtent']=EXTENT
    (dest/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    db.close()
    print('total bytes',manifest['bytes'],flush=True)

if __name__=='__main__':
    import argparse,sqlite3,hashlib
    parser=argparse.ArgumentParser()
    parser.add_argument('--zoom',type=int,choices=range(8,14))
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    if args.zoom is None:
        replace_existing_overview()
    else:
        assert args.output is not None
        from build_map_tiles import ASSETS
        manifest=json.loads((ASSETS/'kyoto-risk-tiles/manifest.json').read_text(encoding='utf-8'))
        assert hashlib.sha256((ASSETS/'kyoto-building-assessment/index.json').read_bytes()).hexdigest()==manifest['sourceIndexSha256']
        db=sqlite3.connect('file:.implementation/map-staging.sqlite?mode=ro',uri=True)
        assert db.execute('SELECT count(*) FROM buildings').fetchone()[0]==manifest['geometryBuildings']
        bounds=json.loads((ASSETS/'kyoto-risk-tiles/tiles.json').read_text(encoding='utf-8'))['bounds']
        outputs=[]
        def save(z,x,y,data):
            rel=f'{z}/{x}/{y}.pbf';p=args.output/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
            outputs.append({'file':rel,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
        stats=build_overview(db,save,bounds,[args.zoom])
        (args.output/'manifest.json').write_text(json.dumps({'tiles':outputs,'stats':stats}),encoding='utf-8')
        db.close()
