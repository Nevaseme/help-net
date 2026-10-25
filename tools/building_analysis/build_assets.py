"""Build reproducible offline PlanD assets from every pinned Kyoto mesh."""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
from damage import classify


def encode(data):
    return (json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode()


def build(cache, output):
    manifest=json.loads(Path(__file__).with_name('kyoto-sources.json').read_text(encoding='utf-8'))
    model=json.loads(Path(__file__).with_name('curves-2025.json').read_text(encoding='utf-8'))
    model['curves']['highrise-zero']={'full':[0]*21,'fullHalf':[0]*21}
    model['limitations']=['Independent PlanD estimate, not an official PLATEAU/IES simulation or building diagnosis.',
        'Shaking only; no tsunami, fire, liquefaction or slope failure.',
        'Conditional class-average damage rate, not earthquake occurrence probability.',
        'Retrofit information unavailable: no retrofit adjustment applied.',
        'Graphical approximation at 1pp resolution; rounding to zero does not establish no risk.',
        '16+ storeys: zero is an explicit user-selected Cabinet Office assumption, not a computed curve result.']
    output.mkdir(parents=True,exist_ok=True)
    (output/'model.json').write_bytes(encode(model))
    chunks=[]
    counts=Counter()
    reasons=Counter()
    profiles=Counter()
    seen=set()
    for item in manifest['files']['bldg']:
        with gzip.open(cache/(item['code']+'.json.gz'),'rt',encoding='utf-8') as stream:
            source=json.load(stream)
        if source['source']['url'] != item['url']:
            raise ValueError('Source URL mismatch')
        rows=[]
        bounds=[]
        for building in source['buildings']:
            if building['id'] in seen:
                raise ValueError('Duplicate building ID across meshes: '+building['id'])
            seen.add(building['id'])
            profile,why=classify(building['attributes'])
            geometry=building['geometry']
            bbox=None
            if geometry:
                pts=[p for polygon in geometry['coordinates'] for ring in polygon for p in ring]
                bbox=[min(p[0] for p in pts),min(p[1] for p in pts),max(p[0] for p in pts),max(p[1] for p in pts)]
                bounds.append(bbox)
            status='unknown' if profile is None else 'assumed' if profile=='highrise-zero' else 'estimated'
            counts[status]+=1
            counts['total']+=1
            if geometry is None: counts['missingGeometry']+=1
            reasons.update(why)
            profiles.update([profile or 'unknown'])
            rows.append({'id':building['id'],'attributes':building['attributes'],'raw':building['raw'],
                         'bbox':bbox,'geometry':geometry,'geometrySource':building['geometrySource'],
                         'profile':profile,'status':status,'reasons':why})
        # Android unwraps .gz assets and strips the suffix during packaging.
        filename=item['code']+'.json.gzip'
        payload=gzip.compress(encode({'mesh':item['code'],'buildings':rows}),mtime=0)
        (output/filename).write_bytes(payload)
        bbox=[min(b[0] for b in bounds),min(b[1] for b in bounds),max(b[2] for b in bounds),max(b[3] for b in bounds)] if bounds else None
        chunks.append({'mesh':item['code'],'file':filename,'bbox':bbox,'count':len(rows),
                       'sha256':hashlib.sha256(payload).hexdigest(),'source':source['source']})
    index={'schemaVersion':1,'cityCode':'26100','datasetYear':2025,'modelId':model['modelId'],
           'modelSha256':hashlib.sha256((output/'model.json').read_bytes()).hexdigest(),
           'coverage':'PLATEAU Kyoto 2025 published building meshes; not all land or all real buildings in Kyoto.',
           'catalogUrl':'https://www.geospatial.jp/ckan/dataset/plateau-26100-kyoto-shi-2025',
           'attribution':'京都市・国土交通省 Project PLATEAU（2025年度）を加工。被害率は内閣府（2025年）公開図をPlanDで数値化した独自試算。',
           'counts':dict(counts),'reasons':dict(reasons),'profiles':dict(profiles),'chunks':chunks}
    (output/'index.json').write_bytes(encode(index))
    print(json.dumps({'counts':dict(counts),'reasons':dict(reasons),'meshes':len(chunks)},ensure_ascii=False,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--cache',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    build(args.cache,args.output)
