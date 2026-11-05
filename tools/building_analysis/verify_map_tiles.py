"""Audit every finest-level MVT ID and all wide-zoom footprint schemas."""
from pathlib import Path
import collections,gzip,hashlib,json
from mapbox_vector_tile.decoder import TileData
import mapbox_vector_tile
from shapely.geometry import shape
from tile_quantization import EXTENT, polygon_parts

def feature_properties(layer):
    values=[TileData.parse_value(v) for v in layer.values]
    for feature in layer.features:
        assert len(feature.tags)%2==0
        props={layer.keys[k]:values[v] for k,v in zip(feature.tags[::2],feature.tags[1::2])}
        assert feature.type==3 and len(feature.geometry)>0
        yield props

def verify():
    root=Path('android/app/src/main/assets')
    source=root/'kyoto-building-assessment';dest=root/'kyoto-risk-tiles'
    manifest=json.loads((dest/'manifest.json').read_text(encoding='utf-8'))
    idx=json.loads((source/'index.json').read_text(encoding='utf-8'))
    assert hashlib.sha256((source/'index.json').read_bytes()).hexdigest()==manifest['sourceIndexSha256']
    expected={}
    for c in idx['chunks']:
        for r in json.loads(gzip.decompress((source/c['file']).read_bytes()))['buildings']:
            if r.get('geometry'):expected[r['id']]=r['profile'] or '?'
    actual={};overview_features=0;validated_polygons=0
    for t in manifest['tiles']:
        data=(dest/t['file']).read_bytes()
        assert hashlib.sha256(data).hexdigest()==t['sha256']
        for layer in mapbox_vector_tile.decode(data).values():
            assert layer['extent']==EXTENT, 'Validate at the renderer native grid'
            for feature in layer['features']:
                parts=polygon_parts(shape(feature['geometry']))
                assert parts and all(p.is_valid and p.area>0 for p in parts), f"Invalid encoded polygon: {t['file']}"
                validated_polygons+=len(parts)
        if t['file'].startswith('16/'):
            layers=TileData(data).tile.layers
            assert len(layers)==1 and layers[0].name=='buildings'
            for p in feature_properties(layers[0]):
                bid=p['bid']
                assert bid in expected and p['p']==expected[bid]
                actual[bid]=p['p']
        if int(t['file'].split('/')[0])<14:
            layers=TileData(data).tile.layers
            assert len(layers)==1 and layers[0].name=='footprints'
            for p in feature_properties(layers[0]):
                assert set(p)=={'p'} and p['p'] in manifest['profileCounts']
                overview_features+=1
    missing=sorted(expected.keys()-actual.keys())
    result={'expectedGeometryBuildings':len(expected),'renderableUniqueBuildings':len(actual),'missingCount':len(missing),'missingIds':missing,
        'overviewProfileFeatures':overview_features,'overviewSourceLayer':'footprints','bytes':manifest['bytes'],'tiles':len(manifest['tiles']),
        'validatedPolygons':validated_polygons,'invalidPolygons':0,'nativeGridExtent':EXTENT}
    Path('.implementation/map-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))
    assert not missing,'Buildings lost during geometry quantization; inspect verification result'

if __name__=='__main__':verify()
