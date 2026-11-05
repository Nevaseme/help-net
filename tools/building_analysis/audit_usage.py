"""Read-only audit of original Kyoto uses joined to shipped model IDs; no asset edits."""
import argparse, collections, gzip, hashlib, json, re
from pathlib import Path

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--cache',type=Path,required=True)
    ap.add_argument('--assets',type=Path,required=True)
    ap.add_argument('--out',type=Path,required=True)
    a=ap.parse_args()
    idx=json.loads((a.assets/'index.json').read_text(encoding='utf-8'))
    model=json.loads((a.assets/'model.json').read_text(encoding='utf-8'))
    counts=collections.Counter(); uses={}; meshes=[]; fields=collections.Counter(); spaces=set(); eligible=collections.Counter()
    pattern=re.compile(rb'<bldg:Building\b[^>]*gml:id="([^"]+)"[^>]*>(.*?)</bldg:Building>',re.S)
    selected=re.compile(rb'<(?:[\w]+:)?([\w]*(?:[Rr]enov|[Rr]etrofit|[Ss]eismic|[Rr]epair|[Cc]ultural|[Hh]eritage)[\w]*)\b')
    for n,item in enumerate(idx['chunks'],1):
        packed=(a.assets/item['file']).read_bytes()
        assert hashlib.sha256(packed).hexdigest()==item['sha256']
        rows=json.loads(gzip.decompress(packed))['buildings']
        byid={r['id']:r for r in rows}; seen=set(); meshcounts=collections.Counter(); meshuses={}
        raw=(a.cache/(item['mesh']+'.gml')).read_bytes()
        for match in pattern.finditer(raw):
            ident=match[1].decode(); body=match[2]
            assert ident in byid and ident not in seen
            seen.add(ident); r=byid[ident]; profile=r['profile']
            pct=None if profile is None else model['curves'][profile]['full'][12]
            level='unknown' if pct is None else 'low' if pct<10 else 'medium' if pct<40 else 'high'
            found=re.findall(rb'<bldg:usage\b([^>]*)>([^<]*)</bldg:usage>',body)
            use=found[0][1].decode() if len(found)==1 else 'missing-or-multiple'
            if found:
                spaces.update(x.decode() for x in re.findall(rb'codeSpace="([^"]+)"',found[0][0]))
            for name in set(selected.findall(body)): fields[name.decode()]+=1
            counts[level]+=1; meshcounts[level]+=1
            uses.setdefault(use,collections.Counter())[level]+=1
            meshuses.setdefault(use,collections.Counter())[level]+=1
            attrs=r['attributes']; floors=attrs.get('floors')
            if attrs.get('structureCode')=='601':
                eligible['wood']+=1
                if attrs.get('year') is not None: eligible['wood-year']+=1
                if floors in (1,2):
                    eligible['wood-1or2-storeys']+=1
                    if attrs.get('year') is not None:
                        eligible['wood-year-1or2-storeys']+=1
                        eligible['wood-year-1or2-storeys-use-'+use]+=1
            if attrs.get('year') is not None: eligible['known-year']+=1
            if floors is not None: eligible['known-floors']+=1
        assert seen==set(byid), (item['mesh'],len(seen),len(byid))
        meshes.append({'mesh':item['mesh'],'counts':meshcounts,'uses':meshuses})
        if n%40==0: print(f'{n}/{len(idx["chunks"])} meshes checked',flush=True)
    result={'intensity':6.2,'thresholdsPercent':[10,40],'counts':counts,'uses':uses,'codeSpaces':sorted(spaces),'matchingSpecialTags':fields,'eligibility':eligible,'meshes':meshes,'limitations':['Uses are not cultural-property designations.','1-2 storeys is an input-screening candidate, not a validated model applicability rule.','At 6.2 rates read the exact table sample (index 12).']}
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='meshes'},ensure_ascii=False,indent=2))
if __name__=='__main__': main()
