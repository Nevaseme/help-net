"""Inspect public address completeness and official city bounds without changing app data."""
import collections, hashlib, json, pathlib, urllib.parse, urllib.request, zipfile, io, math, csv
import xml.etree.ElementTree as ET

CACHE=pathlib.Path('.implementation/address-city');CACHE.mkdir(parents=True,exist_ok=True)
def fetch(url,name):
 p=CACHE/name
 if not p.exists():
  request=urllib.request.Request(url,headers={'User-Agent':'Tasukeai-DataAudit/1.0 (non-commercial prototype)'})
  with urllib.request.urlopen(request,timeout=90) as r:p.write_bytes(r.read())
 return p.read_bytes()
def coords(g):
 if isinstance(g,list) and g and isinstance(g[0],(int,float)):yield g
 elif isinstance(g,list):
  for v in g:yield from coords(v)
def tile(lon,lat,z):
 n=2**z;return (math.floor((lon+180)/360*n),math.floor((1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*n))

def main():
 result={'addresses':[]}
 for ward in ['中京区','上京区']:
  city='京都市'+ward
  url='https://japanese-addresses-v2.geoloniamaps.com/api/ja/'+urllib.parse.quote('京都府')+'/'+urllib.parse.quote(city)+'.json'
  raw=fetch(url,city+'.json');data=json.loads(raw)['data']
  parcel_url=url[:-5]+'-'+urllib.parse.quote('地番')+'.txt'
  parcels=fetch(parcel_url,city+'-地番.txt')
  parcel_rows=[]
  for town in data:
   span=town.get('csv_ranges',{}).get('地番')
   if not span:continue
   chunk=parcels[span['start']:span['start']+span['length']].decode('utf-8').splitlines()
   if len(chunk)>1:parcel_rows.extend(csv.DictReader(chunk[1:]))
  row={'city':city,'url':url,'sha256':hashlib.sha256(raw).hexdigest(),'towns':len(data),'withPoint':sum(bool(x.get('point')) for x in data),'withResidentialRange':sum('住居表示' in x.get('csv_ranges',{}) for x in data),'withParcelRange':sum('地番' in x.get('csv_ranges',{}) for x in data),'parcelRecords':len(parcel_rows),'parcelWithPoint':sum(bool(x.get('lng')) and bool(x.get('lat')) for x in parcel_rows),'sample':data[:2],'parcelSample':parcel_rows[:2]};result['addresses'].append(row)
 url='https://nlftp.mlit.go.jp/ksj/gml/data/N03/N03-2025/N03-20250101_26_GML.zip'
 raw=fetch(url,'kyoto-pref-boundaries-2025.zip');z=zipfile.ZipFile(io.BytesIO(raw));name=next(n for n in z.namelist() if n.endswith('.geojson'));data=json.loads(z.read(name))
 city=[f for f in data['features'] if f['properties'].get('N03_004')=='京都市'];points=[p for f in city for p in coords(f['geometry']['coordinates'])]
 assert city and points,'Kyoto municipality missing'
 bounds=[min(p[0] for p in points),min(p[1] for p in points),max(p[0] for p in points),max(p[1] for p in points)]
 tileCounts={}
 for zoom in range(8,19):
  left,top=tile(bounds[0],bounds[3],zoom);right,bottom=tile(bounds[2],bounds[1],zoom);tileCounts[str(zoom)]=(right-left+1)*(bottom-top+1)
 result['boundary']={'source':url,'sha256':hashlib.sha256(raw).hexdigest(),'municipality':'京都市','wards':sorted(set(f['properties'].get('N03_005') for f in city)),'features':len(city),'bbox':bounds,'boundingRectangleTileCounts':tileCounts}
 (CACHE/'kyoto-city.geojson').write_text(json.dumps({'type':'FeatureCollection','features':city},ensure_ascii=False),encoding='utf-8')
 source=json.loads(pathlib.Path('tools/building_analysis/kyoto-sources.json').read_text(encoding='utf-8'))
 sample=next(f for f in source['files']['bldg'] if f['code']=='52354611')
 raw=fetch(sample['url'],'sample-buildings.gml')
 b='{http://www.opengis.net/citygml/building/2.0}';g='{http://www.opengis.net/gml}'
 total=0;with_address=0;address_values=collections.Counter();other=collections.Counter()
 for _,e in ET.iterparse(io.BytesIO(raw),events=('end',)):
  if e.tag!=b+'Building':continue
  total+=1;a=e.findall(b+'address')
  if a:
   with_address+=1
   for v in a:address_values.update([' / '.join(x.strip() for x in v.itertext() if x.strip())])
  for v in e.iter():
   key=v.tag.split('}')[-1]
   if 'address' in key.lower() or 'location' in key.lower():other.update([key])
  e.clear()
 result['plateauSample']={'mesh':sample['code'],'url':sample['url'],'buildings':total,'withAddress':with_address,'distinctAddresses':len(address_values),'addressExamples':address_values.most_common(3),'relatedTags':dict(other)}
 style=json.loads(fetch('https://tiles.openfreemap.org/styles/positron','positron.json'))
 result['mapSources']=style['sources']
 for key,value in style['sources'].items():
  if value.get('type')=='vector' and value.get('url'):
   info=json.loads(fetch(value['url'],'tilejson-'+key+'.json'));result['mapSources'][key]['inspection']={k:info.get(k) for k in ['minzoom','maxzoom','tiles']}
 (CACHE/'audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
