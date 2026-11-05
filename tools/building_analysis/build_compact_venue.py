"""Build fixed venue fixtures from PLATEAU footprints and reviewed OSM road lines.

Run with a GeoJSON feature-array of transportation centerlines as the argument.
No run-time relocation; old sessions and the reviewed address catalogue are untouched.
"""
import gzip, hashlib, json, math, pathlib, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
REGIONS = [
 ('demo-west','御所南西町内会','Gosho South West',(135.758,35.01145,135.75845,35.01215),18,3,7),
 ('demo-central','御所南中央町内会','Gosho South Central',(135.7636,35.0143,135.7644,35.01495),28,10,14),
 ('demo-east','御所南東町内会','Gosho South East',(135.7644,35.01435,135.76515,35.015),18,3,7)]
def inside(point, geometry):
 def ring_hit(r):
  x,y=point;hit=False
  for a,b in zip(r,r[1:]+r[:1]):
   if (a[1]>y)!=(b[1]>y) and x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:hit=not hit
  return hit
 polys=geometry['coordinates'] if geometry['type']=='MultiPolygon' else [geometry['coordinates']]
 return any(ring_hit(p[0]) and not any(ring_hit(h) for h in p[1:]) for p in polys)
def distance(a,b):return math.hypot((a[0]-b[0])*91000,(a[1]-b[1])*111195)
def spread(pool,count):
 pool=sorted(set(pool));assert len(pool)>=count,(len(pool),count)
 chosen=[pool.pop(len(pool)//2)]
 while len(chosen)<count:
  p=max(pool,key=lambda p:min(distance(p,q) for q in chosen));chosen.append(p);pool.remove(p)
 return chosen
def position(p):return {'latitude':p[1],'longitude':p[0]}
def build(roads):
 buildings=[];sources=[]
 for path in sorted((ROOT/'demo/data/cities/26100/buildings').glob('523546*.gzip')):
  raw=path.read_bytes();sources.append({'asset':path.name,'sha256':hashlib.sha256(raw).hexdigest()})
  buildings+=json.loads(gzip.decompress(raw))['buildings']
 known={h['plateauId']:h for h in json.loads((ROOT/'cloud-sync/src/venue-buildings.json').read_text(encoding='utf-8'))['homes']}
 result={'version':1,'sources':sources,'communities':[],'households':[],'people':[],'slots':[],'roads':{}}
 for region,ja,en,(w,s,e,n),household_count,families,road_count in REGIONS:
  boundary=[[w,s],[e,s],[e,n],[w,n]]
  result['communities'].append({'id':region,'nameJa':ja,'nameEn':en,'latitude':(s+n)/2,'longitude':(w+e)/2,'boundary':boundary})
  selected=sorted([b for b in buildings if w<=(b['bbox'][0]+b['bbox'][2])/2<e and s<=(b['bbox'][1]+b['bbox'][3])/2<n],key=lambda b:b['id'])
  homes=[];interiors={}
  for i,b in enumerate(selected):
   a,c,d,g=b['bbox'];points=[]
   for y in range(1,41):
    for x in range(1,41):
     p=(max(a,w)+(min(d,e)-max(a,w))*x/41,max(c,s)+(min(g,n)-max(c,s))*y/41)
     if inside(p,b['geometry']):points.append(p)
   interiors[b['id']]=spread(points,64)
   base=known.get(b['id'],{})
   homes.append({'buildingId':b['id'].removeprefix('bldg_'),'plateauId':b['id'],'address':base.get('address',f'{en}, building {i+1:02}'),**{k:base[k] for k in ['addressEvidence','buildingUse'] if k in base},'addressPrecision':base.get('addressPrecision','building-reference-not-postal-address'),**position(interiors[b['id']][0]),'floor':'1','room':'','attributes':b['attributes']})
  # Extra households use reviewed apartment buildings, never a fabricated footprint.
  apartments=[h for h in homes if known.get(h['plateauId'],{}).get('buildingUse')=='apartments']
  assert apartments,region
  for i in range(household_count-len(homes)):homes.append(dict(apartments[i%len(apartments)]))
  family_ids=set(h['buildingId'] for h in sorted(homes,key=lambda h:-(h['attributes'].get('floorAreaM2') or 0))[:families])
  family_indices=sorted(range(len(homes)),key=lambda i:(homes[i]['buildingId'] not in family_ids,-(homes[i]['attributes'].get('floorAreaM2') or 0),i))[:families]
  residents=[];interior_slots={}
  for i,h in enumerate(homes):
   h={**h,'householdId':f'{region}-home-{i+1:02}'};result['households'].append(h)
   for j in range(3 if i in family_indices else 1):
    k=interior_slots.get(h['plateauId'],0);interior_slots[h['plateauId']]=k+1
    residents.append({'communityId':region,'home':h,'position':position(interiors[h['plateauId']][k%64]),'safety':'SAFE','injury':'UNINJURED','note':'','observations':{'impact':False,'enclosed':False}})
  pool=[]
  for f in roads:
   geom=f['geometry']
   if f['properties'].get('class') not in ['minor','tertiary','secondary','primary','service'] or geom['type'] not in ['LineString','MultiLineString']:continue
   for line in [geom['coordinates']] if geom['type']=='LineString' else geom['coordinates']:
    for a,b in zip(line,line[1:]):
     if not (max(a[0],b[0])>=w and min(a[0],b[0])<=e and max(a[1],b[1])>=s and min(a[1],b[1])<=n):continue
     steps=max(1,math.ceil(distance(a,b)/2))
     for i in range(steps+1):
      p=(a[0]+(b[0]-a[0])*i/steps,a[1]+(b[1]-a[1])*i/steps)
      if w<p[0]<e and s<p[1]<n and not any(inside(p,x['geometry']) for x in buildings if x['bbox'][0]<=p[0]<=x['bbox'][2] and x['bbox'][1]<=p[1]<=x['bbox'][3]):pool.append(p)
  road_points=spread(pool,road_count+(16 if region=='demo-central' else 1));result['roads'][region]=list(map(position,road_points))
  for i,p in enumerate(road_points[:road_count]):residents[-1-i]['position']=position(p)
  for i,status in enumerate(['SOS','UNREPORTED']):residents[i].update(safety=status,injury='UNKNOWN',note='I need help.' if status=='SOS' else '',observations={'impact':i==0,'enclosed':i==1})
  if region=='demo-central':
   for i,days in [(2,7),(3,3)]:residents[i].update(positionUnknown=True,lastKnownAgeDays=days,lastKnownPosition=residents[i]['position'],position=None,safety='SOS' if i==2 else 'UNREPORTED',injury='UNKNOWN')
   # Exactly 3/4 indoors for the expected 32 attendees; keep the established capacity.
   for i in range(64):
    h=dict(apartments[i%len(apartments)]);h['householdId']=f'attendee-home-{i+1:02}'
    p=position(interiors[h['plateauId']][(i//len(apartments)+12)%64]) if i%4!=3 else position(road_points[road_count+i//4])
    result['slots'].append({**p,'home':h,'communityId':region})
  result['people']+=residents
  print(region,'buildings',len(selected),'households',len(homes),'people',len(residents),'road locations',road_count)
 result['blockedRoadPoint']=result['roads']['demo-west'][-1]
 assert len(result['people'])==96 and len(result['households'])==64 and len(result['slots'])==64
 return result
if __name__=='__main__':
 source=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else pathlib.Path(__file__).with_name('venue-compact-roads.json')
 roads=json.loads(source.read_text(encoding='utf-8'))
 result=build(roads['features'] if isinstance(roads,dict) else roads)
 (ROOT/'cloud-sync/src/venue-compact-layout.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
