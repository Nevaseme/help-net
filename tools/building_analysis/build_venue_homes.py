"""Reproducible demo homes: real footprints, nearby town labels, never invented parcel numbers."""
import gzip,json,math,pathlib,uuid
ROOT=pathlib.Path(__file__).resolve().parents[2]
ASSETS=ROOT/'android/app/src/main/assets'
def ring_contains(r,x,y):
 inside=False
 for (a,b),(c,d) in zip(r,r[1:]):
  if (b>y)!=(d>y) and x<(c-a)*(y-b)/(d-b)+a:inside=not inside
 return inside
def contains(g,x,y):return any(ring_contains(p[0],x,y) and not any(ring_contains(h,x,y) for h in p[1:]) for p in g['coordinates'])
def main():
 rows=json.load(gzip.open(ASSETS/'cities/26100-addresses.json.gzip','rt',encoding='utf8'))['rows']
 towns=[r for r in rows if r[4]=='town' and r[1].endswith('町') and 135.755<r[2]<135.77 and 35.008<r[3]<35.021]
 buildings=[]
 for path in sorted((ASSETS/'kyoto-building-assessment').glob('523546*.gzip')):
  for b in json.load(gzip.open(path,'rt',encoding='utf8'))['buildings']:
   w,s,e,n=b['bbox'];x,y=(w+e)/2,(s+n)/2
   if not (135.758<x<135.767 and 35.011<y<35.018):continue
   if not contains(b['geometry'],x,y):continue
   # Do not use enormous civic/temple footprints for a residential demo.
   area=(e-w)*91000*(n-s)*111000
   if not 35<=area<=700:continue
   town=min(towns,key=lambda r:((x-r[2])*91000)**2+((y-r[3])*111000)**2)
   distance=math.hypot((x-town[2])*91000,(y-town[3])*111000)
   if distance>100:continue
   buildings.append((b,x,y,town,distance))
 selected=[]
 for row in buildings:
  if all(math.hypot((row[1]-p[1])*91000,(row[2]-p[2])*111000)>35 for p in selected):selected.append(row)
  if len(selected)==64:break
 assert len(selected)==64,len(selected)
 homes=[dict(buildingId=b['id'].removeprefix('bldg_'),plateauId=b['id'],address=town[1]+'付近',latitude=y,longitude=x,floor='',room='',addressPrecision='nearby-town',addressDistanceM=round(distance,1)) for b,x,y,town,distance in selected]
 result={'version':1,'dataset':'plateau-26100-kyoto-shi-2025','addressSource':'Geolonia Japanese Addresses v2 (town representatives)','addressPrecision':'nearby-town: not a verified parcel-to-building correspondence','homes':homes}
 (ROOT/'cloud-sync/src/venue-buildings.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')
 print('64 distinct building interiors; nearest-town labels; no invented parcel numbers')
if __name__=='__main__':main()
