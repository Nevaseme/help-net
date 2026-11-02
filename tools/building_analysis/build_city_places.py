"""Build offline facility-name lookup from the already downloaded public city tiles."""
import gzip,hashlib,json,math,re
from pathlib import Path
import mapbox_vector_tile
from shapely.geometry import shape,Point
root=Path('demo/data/cities');assets=Path('android/app/src/main/assets/cities')
for code in ['26100','22205','13103']:
 city_path=root/code/'city.json';city=json.loads(city_path.read_text(encoding='utf-8'));boundary=shape(city['geometry'])
 manifest=json.loads((root/code/'map-manifest.json').read_text(encoding='utf-8'));found={}
 for entry in manifest['resources']:
  match=re.search(r'/14/(\d+)/(\d+)\.pbf$',entry['url'])
  if not match:continue
  tx,ty=map(int,match.groups());raw=Path('.implementation/address-city/map-resources',hashlib.sha256(entry['url'].encode()).hexdigest()).read_bytes();assert hashlib.sha256(raw).hexdigest()==entry['sha256']
  layer=mapbox_vector_tile.decode(raw,default_options={'y_coord_down':True}).get('poi',{});extent=layer.get('extent',4096)
  for f in layer.get('features',[]):
   p=f['properties'];name=p.get('name:ja') or p.get('name') or ''
   if not name.strip() or f['geometry']['type']!='Point':continue
   x,y=f['geometry']['coordinates'];lon=(tx+x/extent)/2**14*360-180;lat=math.degrees(math.atan(math.sinh(math.pi*(1-2*(ty+y/extent)/2**14))))
   if not boundary.covers(Point(lon,lat)):continue
   key=f"poi:{f['id']}:{name}";found[key]=[key,name,round(lon,7),round(lat,7),'poi',{'source':'OSM/OpenFreeMap','sourceVersion':entry['url'].split('/planet/')[-1].split('/')[0],'matchLevel':'facility'}]
 plateau=Path('.implementation/address-city')/(code+'-plateau-names.json')
 if plateau.exists():
  for row in json.loads(plateau.read_text(encoding='utf8')):found[row[0]]=row
 rows=sorted(found.values(),key=lambda r:r[0]);body={'version':1,'municipalityCode':code,'attribution':'© OpenStreetMap contributors / OpenMapTiles / OpenFreeMap (ODbL); 京都市・国土交通省 PLATEAU 2025（収録時）','source':'map-manifest.json: zoom 14 poi point features','rows':rows};data=gzip.compress(json.dumps(body,ensure_ascii=False,separators=(',',':')).encode(),mtime=0)
 (root/code/'places.json.gz').write_bytes(data);(assets/(code+'-places.json.gzip')).write_bytes(data)
 city['places']={'path':code+'/places.json.gz','bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'count':len(rows),'attribution':body['attribution']};city_path.write_text(json.dumps(city,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
 (assets/(code+'.json')).write_text(json.dumps(city,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
 print(code,len(rows),len(data),[r for r in rows if r[1]=='京都タワー'])
