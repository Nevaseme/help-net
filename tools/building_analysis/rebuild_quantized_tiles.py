"""Rebuild the existing tile inventory from verified staging, then publish atomically per file.

Run from the repository root with the map-requirements environment. Does not modify
the building model, assessment inputs, or staging database. A failed build leaves
the shipped inventory unchanged. Run verify_map_tiles.py after this command.
"""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import sqlite3
import time
import mapbox_vector_tile
from shapely import from_wkb
from shapely.geometry import shape
from build_map_tiles import ASSETS, tile_geometry
from footprint_overview import encode_footprints
from tile_quantization import EXTENT, polygon_parts

STAGED = Path('.implementation/quantized-tiles')

def rebuild(relative):
    z, x, tail = relative.split('/')
    z, x, y = int(z), int(x), int(tail[:-4])
    n = 2**z
    buffer = 0 if z < 14 else .002
    with sqlite3.connect('file:.implementation/map-staging.sqlite?mode=ro', uri=True) as db:
        rows = db.execute('SELECT bid,geom,props FROM buildings JOIN bounds USING(id) WHERE maxx>=? AND minx<=? AND maxy>=? AND miny<=?',
            ((x-buffer)/n, (x+1+buffer)/n, (y-buffer)/n, (y+1+buffer)/n))
        if z < 14:
            data, _ = encode_footprints(((from_wkb(g), json.loads(p)['p']) for _, g, p in rows), z, x, y)
        else:
            data = tile_geometry(((bid, from_wkb(g), json.loads(p)) for bid, g, p in rows), z, x, y)
    # Test what the client actually decodes, after winding and integer encoding.
    # Separate exterior polygons may touch; each exterior and its own holes must
    # be valid. Never interpret a neighbor as a courtyard.
    features = polygons = 0
    for layer in mapbox_vector_tile.decode(data).values():
        assert layer['extent'] == EXTENT
        for feature in layer['features']:
            parts = polygon_parts(shape(feature['geometry']))
            assert parts and all(p.is_valid and p.area > 0 for p in parts), relative
            features += 1
            polygons += len(parts)
    path = STAGED/relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return {'file': relative, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'validatedFeatures': features, 'validatedPolygons': polygons}

def main():
    dest = ASSETS/'kyoto-risk-tiles'
    source = ASSETS/'kyoto-building-assessment'
    manifest = json.loads((dest/'manifest.json').read_text(encoding='utf8'))
    assert hashlib.sha256((source/'index.json').read_bytes()).hexdigest() == manifest['sourceIndexSha256']
    assert hashlib.sha256((source/'model.json').read_bytes()).hexdigest() == manifest['modelSha256']
    with sqlite3.connect('file:.implementation/map-staging.sqlite?mode=ro', uri=True) as db:
        assert db.execute('SELECT count(*) FROM buildings').fetchone()[0] == manifest['geometryBuildings']
    started = time.monotonic()
    STAGED.mkdir(parents=True, exist_ok=True)
    results = []
    with concurrent.futures.ProcessPoolExecutor(max_workers=3) as pool:
        futures = {pool.submit(rebuild, t['file']): t['file'] for t in manifest['tiles']}
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
            if len(results)%100 == 0 or len(results)==len(futures):
                print(f'{len(results)}/{len(futures)} tiles validated; {time.monotonic()-started:.1f}s', flush=True)
    for row in results:
        path = dest/row['file']
        # replace uses the validated staged file on the same filesystem.
        (STAGED/row['file']).replace(path)
    manifest['tiles'] = sorted(results, key=lambda r: r['file'])
    manifest['bytes'] = sum(r['bytes'] for r in results)
    manifest['overviewExtent'] = manifest['extent'] = EXTENT
    manifest['quantization'] = 'GEOS topology-preserving valid_output at native 8192 grid; polygon validity checked after MVT roundtrip'
    manifest['geometryValidation'] = {'tiles': len(results), 'features': sum(r['validatedFeatures'] for r in results),
        'polygons': sum(r['validatedPolygons'] for r in results), 'invalidPolygons': 0}
    # The prior overview counters predate topology-preserving quantization and
    # would incorrectly include collapsed subpixel fragments.
    manifest.pop('overviewStats', None)
    (dest/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(manifest['geometryValidation']), flush=True)

if __name__ == '__main__': main()
