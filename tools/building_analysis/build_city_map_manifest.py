"""Pin a common map style and measure every required city tile/font before publishing.

At most two upstream requests; stop below the provider's 50 MB / 3000 tile limit.
Cached research bytes stay local. Published manifests reference the original resources.
"""
import concurrent.futures, hashlib, json, math, pathlib, threading, urllib.request, urllib.parse
from shapely.geometry import shape, box
from probe_address_city import tile

ROOT=pathlib.Path('demo/data/cities');CACHE=pathlib.Path('.implementation/address-city/map-resources');CACHE.mkdir(parents=True,exist_ok=True)
PUBLIC='https://nevaseme.github.io/safety_confirm/data/cities/'
lock=threading.Lock();total=0
def fetch(url):
 global total
 key=hashlib.sha256(url.encode()).hexdigest();p=CACHE/key
 if p.exists():data=p.read_bytes()
 else:
  req=urllib.request.Request(url,headers={'User-Agent':'Tasukeai-CityMap/1.0 (non-commercial research)'})
  with urllib.request.urlopen(req,timeout=45) as r:data=r.read(2_000_001)
  if len(data)>2_000_000:raise ValueError('Unexpectedly large resource')
  p.write_bytes(data)
 with lock:
  total+=len(data)
  if total>48_000_000:raise ValueError('City exceeds the upstream download allowance; do not publish as complete')
 return {'url':url,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()},data
def fields(data):
 def number(i):
  n=0;s=0
  while True:
   b=data[i];i+=1;n|=(b&127)<<s
   if b<128:return n,i
   s+=7
 i=0
 while i<len(data):
  tag,i=number(i);wire=tag&7
  if wire==2:n,i=number(i);value=data[i:i+n];i+=n
  elif wire==0:value,i=number(i)
  elif wire in (1,5):n=8 if wire==1 else 4;value=data[i:i+n];i+=n
  else:raise ValueError('Unsupported protobuf wire')
  yield tag>>3,wire,value
def glyph_ranges(data):
 # Decode only vector tile string values. Superset of the style's displayed labels.
 result={0,256}
 for n,w,layer in fields(data):
  if n!=3 or w!=2:continue
  for k,kw,value in fields(layer):
   if k!=4 or kw!=2:continue
   for v,vw,text in fields(value):
    if v==1 and vw==2:
     result.update(ord(c)//256*256 for c in text.decode('utf-8') if ord(c)<=65535)
 return result
def ll(x,y,z):return x/2**z*360-180,math.degrees(math.atan(math.sinh(math.pi*(1-2*y/2**z))))
def main():
 global total
 style=json.loads(pathlib.Path('.implementation/address-city/map-style.json').read_text())
 audit=json.loads(pathlib.Path('.implementation/address-city/audit.json').read_text(encoding='utf8'))
 source=audit['mapSources']['openmaptiles']['inspection']
 style['sources']['openmaptiles'].pop('url',None);style['sources']['openmaptiles'].update(source)
 style['sources']['openmaptiles']['attribution']='© OpenMapTiles · © OpenStreetMap contributors · OpenFreeMap'
 # Global low-zoom shading is not required for street-level offline navigation.
 style['layers']=[l for l in style['layers'] if l.get('source')!='ne2_shaded'];style['sources'].pop('ne2_shaded',None)
 (ROOT/'map-style.json').write_text(json.dumps(style,ensure_ascii=False,separators=(',',':')),encoding='utf8')
 fonts=sorted(set(f for l in style['layers'] for f in l.get('layout',{}).get('text-font',[]) if isinstance(f,str)))
 for code in ['26100','22205','13103']:
  total=0;city=json.loads((ROOT/code/'city.json').read_text(encoding='utf8'));geom=shape(city['geometry']);w,s,e,n=geom.bounds;urls=[]
  for z in range(8,15):
   left,top=tile(w,n,z);right,bottom=tile(e,s,z)
   for x in range(left,right+1):
    for y in range(top,bottom+1):
     a,b=ll(x,y,z);c,d=ll(x+1,y+1,z)
     if geom.intersects(box(a,d,c,b)):urls.append(source['tiles'][0].replace('{z}',str(z)).replace('{x}',str(x)).replace('{y}',str(y)))
  assert len(urls)<=3000
  resources=[];ranges=set()
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
   for i,(entry,data) in enumerate(pool.map(fetch,urls)):
    resources.append(entry);ranges.update(glyph_ranges(data))
    if i%50==0:print(code,i+1,len(urls),total,flush=True)
   extras=[style['sprite']+suffix for suffix in ['.json','.png','@2x.json','@2x.png']]
   # CJK is rendered with the installed system font on both platforms.
   ranges={r for r in ranges if not (0x3000<=r<=0x9fff or 0xac00<=r<=0xd7ff or 0xff00<=r<=0xffef)}
   extras += [style['glyphs'].replace('{fontstack}',urllib.parse.quote(font)).replace('{range}',f'{r}-{r+255}') for font in fonts for r in sorted(ranges)]
   resources.extend(entry for entry,_ in pool.map(fetch,extras))
  manifest={'version':1,'municipalityCode':code,'style':PUBLIC+'map-style.json','minZoom':8,'maxNativeZoom':14,'tileCount':len(urls),'resources':resources,'bytes':sum(r['bytes'] for r in resources),'attribution':'© OpenFreeMap / OpenMapTiles / OpenStreetMap contributors'}
  (ROOT/code/'map-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,separators=(',',':')),encoding='utf8')
  print(json.dumps({k:v for k,v in manifest.items() if k!='resources'},ensure_ascii=False),flush=True)
if __name__=='__main__':main()
