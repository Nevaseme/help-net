"""Bounded official PLATEAU package; missing ages/structures are never inferred."""
import collections,gzip,hashlib,json,shutil
from pathlib import Path
from damage import classify

ROOT=Path('android/app/src/main/assets')
def encode(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode()
def main():
 manifest=json.loads(Path(__file__).with_name('toranomon-sources.json').read_text())
 dest=ROOT/'toranomon-building-assessment';dest.mkdir(exist_ok=True)
 model=(ROOT/'kyoto-building-assessment/model.json').read_bytes();(dest/'model.json').write_bytes(model)
 counts=collections.Counter();profiles=collections.Counter();reasons=collections.Counter();attributes=collections.Counter();seen={};chunks=[];inputs=[]
 w,s,e,n=manifest['bounds']
 for item in manifest['files']:
  payload=json.loads(gzip.decompress((Path('.implementation/toranomon-2025')/(item['code']+'.json.gz')).read_bytes()))
  assert payload['source']['url']==item['url'];inputs.append(payload['source']);rows=[]
  for b in payload['buildings']:
   if not b['geometry']:continue
   points=[p for polygon in b['geometry']['coordinates'] for ring in polygon for p in ring]
   bbox=[min(p[0] for p in points),min(p[1] for p in points),max(p[0] for p in points),max(p[1] for p in points)]
   if bbox[2]<w or bbox[0]>e or bbox[3]<s or bbox[1]>n:continue
   if b['id'] in seen:
    assert seen[b['id']]==b,'Conflicting published duplicates: '+b['id']
    counts['duplicateSourceRecords']+=1;continue
   seen[b['id']]=b
   profile,why=classify(b['attributes']);status='unknown' if profile is None else 'assumed' if profile=='highrise-zero' else 'estimated'
   rows.append(dict(b,bbox=bbox,profile=profile,status=status,reasons=why));counts[status]+=1;counts['total']+=1;profiles[profile or 'unknown']+=1;reasons.update(why)
   for k in ['year','floors','structureCode']:attributes[k]+=b['attributes'][k] is not None
  if not rows:continue
  file=item['code']+'.json.gzip';data=gzip.compress(encode({'mesh':item['code'],'buildings':rows}),mtime=0);(dest/file).write_bytes(data)
  bounds=[min(r['bbox'][i] for r in rows) if i<2 else max(r['bbox'][i] for r in rows) for i in range(4)]
  chunks.append({'mesh':item['code'],'file':file,'bbox':bounds,'count':len(rows),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'source':payload['source']})
 index={'schemaVersion':1,'cityCode':'13103','datasetYear':2025,'modelId':json.loads(model)['modelId'],'modelSha256':hashlib.sha256(model).hexdigest(),'coverage':manifest['description'],'requestedBounds':manifest['bounds'],'attribution':'東京都・国土交通省 Project PLATEAU（2025年度）を加工。内閣府公開図を用いたPlanD独自試算。','counts':dict(counts),'attributesAvailable':dict(attributes),'profiles':dict(profiles),'reasons':dict(reasons),'chunks':chunks,'inputs':inputs}
 (dest/'index.json').write_bytes(encode(index))
 web=Path('demo/data/cities/13103/buildings');web.mkdir(parents=True,exist_ok=True)
 for file in ['index.json','model.json']+[c['file'] for c in chunks]:shutil.copyfile(dest/file,web/file)
 print(json.dumps({k:index[k] for k in ['counts','attributesAvailable','profiles','requestedBounds']},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
