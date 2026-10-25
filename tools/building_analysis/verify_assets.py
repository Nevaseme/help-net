"""Audit the packaged artifact independently of Android and the asset writer."""
import argparse
from collections import Counter
import gzip
import hashlib
import json
import math
import zipfile
from pathlib import Path


def verify(root):
    index=json.loads((root/'index.json').read_text(encoding='utf-8'))
    model_bytes=(root/'model.json').read_bytes()
    assert hashlib.sha256(model_bytes).hexdigest()==index['modelSha256']
    model=json.loads(model_bytes)
    assert model['modelId']==index['modelId']
    assert len(index['chunks'])==242
    counts=Counter()
    seen=set()
    for chunk in index['chunks']:
        raw=(root/chunk['file']).read_bytes()
        assert hashlib.sha256(raw).hexdigest()==chunk['sha256'],chunk['file']
        data=json.loads(gzip.decompress(raw))
        assert data['mesh']==chunk['mesh']
        assert len(data['buildings'])==chunk['count']==chunk['source']['buildings']
        for building in data['buildings']:
            assert building['id'] not in seen,building['id']
            seen.add(building['id'])
            counts['total']+=1
            counts[building['status']]+=1
            if building['status']=='unknown':
                assert building['profile'] is None and building['reasons']
            else:
                pair=model['curves'][building['profile']]
                assert all(0<=f<=h<=100 for f,h in zip(pair['full'],pair['fullHalf']))
                if building['status']=='assumed':
                    assert building['attributes']['floors']>=16
                    assert building['reasons']==['cabinet-highrise-zero-assumption']
                    assert set(pair['full']+pair['fullHalf'])=={0}
            if building['geometry'] is None:
                counts['missingGeometry']+=1
                assert building['bbox'] is None
                continue
            bounds=building['bbox']
            assert chunk['bbox'][0]<=bounds[0]<=bounds[2]<=chunk['bbox'][2]
            assert chunk['bbox'][1]<=bounds[1]<=bounds[3]<=chunk['bbox'][3]
            for polygon in building['geometry']['coordinates']:
                for ring in polygon:
                    assert len(ring)>=4 and ring[0]==ring[-1]
                    assert all(math.isfinite(x) and math.isfinite(y) and bounds[0]<=x<=bounds[2] and bounds[1]<=y<=bounds[3] for x,y in ring)
    assert dict(counts)==index['counts'],(counts,index['counts'])
    assert counts['total']==513815 # official 2025 Kyoto building model count
    report={'verified':True,'counts':dict(counts),'files':len(index['chunks'])+2,
            'packageBytes':sum((root/c['file']).stat().st_size for c in index['chunks'])+(root/'index.json').stat().st_size+len(model_bytes)}
    print(json.dumps(report,ensure_ascii=False,indent=2))


def verify_apk(apk):
    with zipfile.ZipFile(apk) as archive:
        prefix='assets/kyoto-building-assessment/'
        index=json.loads(archive.read(prefix+'index.json'))
        model=archive.read(prefix+'model.json')
        assert hashlib.sha256(model).hexdigest()==index['modelSha256']
        for chunk in index['chunks']:
            raw=archive.read(prefix+chunk['file'])
            assert hashlib.sha256(raw).hexdigest()==chunk['sha256']
            assert raw[:2]==b'\x1f\x8b', 'Gzip payload changed during Android packaging'
        print(json.dumps({'apkVerified':True,'files':len(index['chunks'])+2,'bytes':apk.stat().st_size}))


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('root',type=Path)
    parser.add_argument('--apk',type=Path)
    args=parser.parse_args()
    if args.apk: verify_apk(args.apk)
    else: verify(args.root)
