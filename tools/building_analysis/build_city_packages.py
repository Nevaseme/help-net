"""Build public city boundaries/address indexes. No resident/device records are read."""
import csv,gzip,hashlib,io,json,pathlib,urllib.parse,zipfile,re,unicodedata
from probe_address_city import fetch,CACHE,coords

ROOT=pathlib.Path('demo/data/cities')
ANDROID=pathlib.Path('android/app/src/main/assets/cities')
def encoded(value):return urllib.parse.quote(value)
def digest(data):return hashlib.sha256(data).hexdigest()
def emit(path,value):
 data=json.dumps(value,ensure_ascii=False,separators=(',',':')).encode();path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);return data
def key(address):
 return unicodedata.normalize('NFKC',address).replace('番地','-').replace('番','-').replace('号','').rstrip('-')

def main():
 catalog=[]
 for code,name,pref,prefcode in [('26100','京都市','京都府','26'),('22205','熱海市','静岡県','22'),('13103','港区','東京都','13')]:
  url=f'https://nlftp.mlit.go.jp/ksj/gml/data/N03/N03-2025/N03-20250101_{prefcode}_GML.zip'
  archive=zipfile.ZipFile(io.BytesIO(fetch(url,f'{prefcode}-boundaries-2025.zip')))
  features=json.loads(archive.read(next(n for n in archive.namelist() if n.endswith('.geojson'))))['features']
  features=[f for f in features if f['properties']['N03_004']==name];assert features
  polygons=[]
  for f in features:
   g=f['geometry'];polygons.extend(g['coordinates'] if g['type']=='MultiPolygon' else [g['coordinates']])
  points=list(coords(polygons));bounds=[min(p[0] for p in points),min(p[1] for p in points),max(p[0] for p in points),max(p[1] for p in points)]
  geometry={'type':'MultiPolygon','coordinates':polygons}
  rows=[];sources=[]
  for f in features:
   ward=f['properties'].get('N03_005') or '';city=name+ward
   if any(s['city']==city for s in sources):continue
   address_url='https://japanese-addresses-v2.geoloniamaps.com/api/ja/'+encoded(pref)+'/'+encoded(city)+'.json'
   raw=fetch(address_url,city+'.json');towns=json.loads(raw)['data']
   tables={kind:fetch(address_url[:-5]+'-'+encoded(kind)+'.txt',city+'-'+kind+'.txt') for kind in ['地番','住居表示'] if any(kind in t.get('csv_ranges',{}) for t in towns)}
   version=digest(raw)
   for t in towns:
    town=''.join(t.get(k,'') for k in ['oaza_cho','chome','koaza']);address=pref+city+town;point=t.get('point')
    meta={'matchLevel':'town','source':'ABR/Geolonia','sourceVersion':version,'municipalityCode':code,'town':town,'townId':t['machiaza_id']}
    if point and len(point)==2:rows.append([city+':'+t['machiaza_id'],address,point[0],point[1],'town',meta])
    for kind,table in tables.items():
     span=t.get('csv_ranges',{}).get(kind)
     if not span:continue
     lines=table[span['start']:span['start']+span['length']].decode().splitlines()
     for p in csv.DictReader(lines[1:]):
      if not p.get('lng') or not p.get('lat'):continue
      fields=['prc_num1','prc_num2','prc_num3'] if kind=='地番' else ['blk_num','rsdt_num','rsdt_num2']
      number='-'.join(p[k] for k in fields if p.get(k));level='parcel' if kind=='地番' else 'residential'
      if number:rows.append([city+':'+t['machiaza_id']+':'+kind+':'+number,address+number,float(p['lng']),float(p['lat']),level,{**meta,'matchLevel':level,'sourceVersion':digest(table)}])
   sources.append({'city':city,'url':address_url,'sha256':version,'tables':{k:digest(v) for k,v in tables.items()}})
   # Supplement missing coordinates, without upgrading block representative points.
   wardcode=f['properties']['N03_007'];isj_url=f'https://nlftp.mlit.go.jp/isj/dls/data/24.0a/{wardcode}-24.0a.zip'
   isj_raw=fetch(isj_url,wardcode+'-isj-24.0a.zip');isj=zipfile.ZipFile(io.BytesIO(isj_raw))
   csv_name=next(n for n in isj.namelist() if n.endswith('.csv'))
   existing={key(r[1]) for r in rows}
   for p in csv.DictReader(io.StringIO(isj.read(csv_name).decode('cp932'))):
    if p.get('更新前履歴フラグ')=='1' or not p.get('緯度') or not p.get('経度'):continue
    town=p['大字・丁目名']+p.get('小字・通称名','');number=p['街区符号・地番'];label=p['都道府県名']+p['市区町村名']+town+number
    if key(label) in existing:continue
    existing.add(key(label));rows.append(['isj:'+wardcode+':'+town+':'+number,label,float(p['経度']),float(p['緯度']),'block',{'matchLevel':'block' if p['住居表示フラグ']=='1' else 'parcel','source':'ISJ','sourceVersion':'24.0a','municipalityCode':code,'town':town}])
   sources.append({'city':city,'kind':'ISJ','url':isj_url,'sha256':digest(isj_raw)})
   print(code,city,len(rows),flush=True)
  unique={r[0]:r for r in rows};rows=sorted(unique.values(),key=lambda r:(r[1],r[0]))
  raw=json.dumps({'version':2,'municipalityCode':code,'source':'Geolonia japanese-addresses-v2 / デジタル庁ABR / 国土交通省位置参照情報','licenses':[{'source':'Geolonia','url':'https://github.com/geolonia/japanese-addresses-v2'},{'source':'ABR','url':'https://www.digital.go.jp/policies/base_registry_address_tos/'},{'source':'ISJ','url':'https://nlftp.mlit.go.jp/ksj/other/agreement.html'}],'rows':rows},ensure_ascii=False,separators=(',',':')).encode();data=gzip.compress(raw,mtime=0)
  folder=ROOT/code;folder.mkdir(parents=True,exist_ok=True);(folder/'addresses.json.gz').write_bytes(data)
  old=json.loads((folder/'city.json').read_text(encoding='utf8')) if (folder/'city.json').exists() else {}
  entry={**old,'code':code,'name':name,'prefecture':pref,'boundarySource':url,'bounds':bounds,'geometry':geometry,'addressCount':len(rows),'addresses':{'path':f'{code}/addresses.json.gz','sha256':digest(data),'bytes':len(data)},'addressSources':sources}
  emit(folder/'city.json',entry);emit(ANDROID/(code+'.json'),entry);(ANDROID/(code+'-addresses.json.gzip')).write_bytes(data)
  catalog.append({k:entry[k] for k in ['code','name','prefecture','bounds','addressCount','addresses']})
 emit(ROOT/'catalog.json',{'version':1,'cities':catalog});print(json.dumps(catalog,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
