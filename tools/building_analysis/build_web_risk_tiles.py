from pathlib import Path
import json,gzip,hashlib,argparse
p=argparse.ArgumentParser();p.add_argument("--source",default="android/app/src/main/assets/kyoto-risk-tiles");p.add_argument("--dest",default="demo/data/cities/26100/risk");args=p.parse_args()
src=Path(args.source);dest=Path(args.dest);dest.mkdir(parents=True,exist_ok=True)
manifest=json.loads((src/'manifest.json').read_text());tiles=[]
for e in manifest['tiles']:
 raw=(src/e['file']).read_bytes();assert hashlib.sha256(raw).hexdigest()==e['sha256'];data=gzip.compress(raw,mtime=0);out=dest/(e['file']+'.gz');out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data);tiles.append({'file':e['file']+'.gz','bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
meta=json.loads((src/'tiles.json').read_text());meta.update({'tiles':tiles,'bytes':sum(x['bytes'] for x in tiles),'buildings':manifest['buildings'],'modelSha256':manifest['modelSha256']});(dest/'index.json').write_text(json.dumps(meta,separators=(',',':'),ensure_ascii=False),encoding='utf-8')
print({'tiles':len(tiles),'bytes':meta['bytes']})
