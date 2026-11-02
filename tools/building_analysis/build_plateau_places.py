"""Extract named Kyoto PLATEAU facilities using retained official GML and checked footprints."""
import argparse,gzip,hashlib,json
from pathlib import Path
import xml.etree.ElementTree as ET
from shapely.geometry import shape
B='{http://www.opengis.net/citygml/building/2.0}Building';G='{http://www.opengis.net/gml}'
def extract(raw,buildings,source):
 result=[]
 context=ET.iterparse(str(raw),events=('start','end'));_,root=next(context)
 for event,b in context:
  if event!='end' or b.tag!=B:continue
  identifier=b.get(G+'id');building=buildings.get(identifier)
  names=[n.text.strip() for n in b.findall(G+'name') if n.text and n.text.strip() and not n.get('codeSpace')]
  if names and building and building.get('geometry'):
   point=shape(building['geometry']).representative_point()
   for i,name in enumerate(dict.fromkeys(names)):
    result.append([f'plateau:{identifier}:{i}',name,round(point.x,7),round(point.y,7),'poi',{'source':'PLATEAU Kyoto','sourceVersion':'2025','matchLevel':'facility','plateauId':identifier,'sourceSha256':source['sha256']}])
  b.clear();root.clear()
 return result
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--cache',type=Path,required=True);args=parser.parse_args()
 index=json.loads(Path('android/app/src/main/assets/kyoto-building-assessment/index.json').read_text(encoding='utf8'));rows=[]
 for i,chunk in enumerate(index['chunks']):
  raw=args.cache/(chunk['mesh']+'.gml');source=chunk['source']
  with raw.open('rb') as f:assert hashlib.file_digest(f,'sha256').hexdigest()==source['sha256'],raw
  payload=json.load(gzip.open(Path('android/app/src/main/assets/kyoto-building-assessment')/chunk['file'],'rt',encoding='utf8'))
  rows+=extract(raw,{b['id']:b for b in payload['buildings']},source)
  if i%30==0:print(i+1,len(index['chunks']),len(rows),flush=True)
 out=Path('.implementation/address-city/26100-plateau-names.json');out.write_text(json.dumps(rows,ensure_ascii=False,separators=(',',':')),encoding='utf8');print('Named facilities',len(rows))
