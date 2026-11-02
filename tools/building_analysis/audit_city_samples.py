"""Exploratory convenience samples, never citywide coverage estimates."""
import collections, concurrent.futures, hashlib, json, re, urllib.request
from pathlib import Path
from xml.etree import ElementTree as ET
manifest=Path('docs/superpowers/plans/data/2026-10-26-city-samples-input.json')
cache=Path('.implementation/collapse-research'); cache.mkdir(parents=True,exist_ok=True)
pattern=re.compile(rb'<bldg:Building\b[^>]*gml:id="([^"]+)"[^>]*>(.*?)</bldg:Building>',re.S)
def fetch(url):
    with urllib.request.urlopen(url,timeout=90) as r: return r.read()
def run(c,f):
    local=cache/(c['name']+'-'+f['code']+'.gml')
    data=local.read_bytes() if local.exists() else fetch(f['url']); local.write_bytes(data)
    counts=collections.Counter(); values={k:collections.Counter() for k in ['yearOfConstruction','storeysAboveGround','buildingStructureType','usage']}; spaces={}
    for match in pattern.finditer(data):
        body=match[2]; counts['total']+=1; row={}
        for tag in values:
            found=re.findall(rb'<[\w]+:'+tag.encode()+rb'\b([^>]*)>([^<]*)</[\w]+:'+tag.encode()+rb'>',body)
            v=found[0][1].decode() if len(found)==1 else None
            row[tag]=v; values[tag][str(v)]+=1
            if found:
                cs=re.search(rb'codeSpace="([^"]+)"',found[0][0])
                if cs: spaces[tag]=urllib.parse.urljoin(f['url'],cs[1].decode())
        def validnum(v,lo,hi):
            try: return float(v).is_integer() and lo<=float(v)<=hi
            except (ValueError,TypeError): return False
        year=validnum(row['yearOfConstruction'],2,2026); floors=validnum(row['storeysAboveGround'],1,300)
        structure=row['buildingStructureType'] in ('601','602','603','604')
        counts['validYear']+=year; counts['validFloors']+=floors; counts['supportedStructure']+=structure
        counts['yearStructureFloors']+=year and floors and structure
        counts['currentModelInputEligible']+=year and structure and (row['buildingStructureType']=='601' or floors)
    definitions={}
    for tag,url in spaces.items():
        raw=fetch(url); root=ET.fromstring(raw)
        definitions[tag]={n.findtext('{http://www.opengis.net/gml}name'):n.findtext('{http://www.opengis.net/gml}description') for n in root.iter('{http://www.opengis.net/gml}Definition')}
    return {'city':c['name'],'year':c['year'],'spec':c['spec'],'mesh':f['code'],'url':f['url'],'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'counts':counts,'topValues':{k:v.most_common(12) for k,v in values.items()},'codeDefinitions':definitions}
results=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    fs=[pool.submit(run,c,f) for c in json.loads(manifest.read_text(encoding='utf-8-sig')) for f in c['files']]
    for future in concurrent.futures.as_completed(fs):
        try:
            r=future.result(); results.append(r); print(json.dumps({k:v for k,v in r.items() if k in ['city','mesh','counts','topValues']},ensure_ascii=False),flush=True)
        except Exception as e: results.append({'error':str(e)}); print('ERROR',str(e),flush=True)
Path('docs/superpowers/plans/data/2026-10-26-city-samples-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
