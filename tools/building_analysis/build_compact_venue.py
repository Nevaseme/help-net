"""Build fixed venue fixtures from PLATEAU footprints and reviewed OSM road lines.

Run with a GeoJSON feature-array of transportation centerlines as the argument.
No run-time relocation; old sessions and the reviewed address catalogue are untouched.
"""
import gzip, hashlib, json, math, pathlib, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
REGIONS = [
 ('demo-west','御所南西町内会','Gosho South West',(135.7629,35.01365,135.7636,35.01425),24,8,11),
 ('demo-central','御所南中央町内会','Gosho South Central',(135.7636,35.01365,135.7643,35.01425),16,0,6),
 ('demo-east','御所南東町内会','Gosho South East',(135.7643,35.01365,135.7650,35.01425),24,8,11)]
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
 result={'version':2,'sources':sources,'communities':[],'households':[],'people':[],'slots':[],'roads':{},'reportPoints':{}}
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
  # Simulated households use every real footprint without inventing postal addresses.
  ordered_points=spread([(h['longitude'],h['latitude']) for h in homes],len(homes))
  unique_homes=[next(h for h in homes if (h['longitude'],h['latitude'])==p) for p in ordered_points]
  homes=list(unique_homes)
  assert household_count>=len(homes),(region,len(homes),household_count)
  for i in range(household_count-len(homes)):homes.append(dict(unique_homes[i%len(unique_homes)]))
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
  road_points=spread(pool,road_count+(16 if region=='demo-central' else 8));result['roads'][region]=list(map(position,road_points))
  for i,p in enumerate(road_points[:road_count]):residents[-1-i]['position']=position(p)
  if region=='demo-central':
   for i,days in [(2,7),(3,3)]:residents[i].update(positionUnknown=True,lastKnownAgeDays=days,lastKnownPosition=residents[i]['position'],position=None,safety='SOS' if i==2 else 'UNREPORTED',injury='UNKNOWN')
  known_indoor=[p for p in residents if p['position'] is not None and any(inside((p['position']['longitude'],p['position']['latitude']),b['geometry']) for b in selected)]
  used={h['plateauId']:0 for h in unique_homes}
  def indoor_position(home,away):
   candidates=[h for h in unique_homes if h['plateauId']!=home['plateauId']] if away else [home]
   h=min(candidates,key=lambda h:(used[h['plateauId']],-distance((h['longitude'],h['latitude']),(home['longitude'],home['latitude']))))
   key=h['plateauId'];p=position(interiors[key][used[key]]);used[key]+=1
   return p
  indoor_offset=sum(1 for p in result['people'] if p.get('_indoor'))
  for i,p in enumerate(known_indoor):
   ordinal=indoor_offset+i
   p['position']=indoor_position(p['home'],(ordinal+6)//10>(ordinal+5)//10);p['_indoor']=True
  first=known_indoor[0]
  second=max(known_indoor,key=lambda p:distance((p['position']['longitude'],p['position']['latitude']),(first['position']['longitude'],first['position']['latitude'])))
  for p,status in zip([first,second],['SOS','UNREPORTED']):p.update(safety=status,injury='UNKNOWN',note='I need help.' if status=='SOS' else '',observations={'impact':status=='SOS','enclosed':status=='UNREPORTED'})
  if region=='demo-central':
   # Homes cycle through ALL central footprints; 24/32 positions remain indoors.
   indoor_index=0
   for i in range(64):
    h=dict(unique_homes[i%len(unique_homes)]);h['householdId']=f'attendee-home-{i+1:02}'
    if i%4!=3:
     p=indoor_position(h,(indoor_index+6)//10>(indoor_index+5)//10);indoor_index+=1
    else:p=position(road_points[road_count+i//4])
    result['slots'].append({**p,'home':h,'communityId':region})
  # Choose only initial sample reports; never relocate user-created reports.
  critical=[(p['position']['longitude'],p['position']['latitude']) for p in residents if p['position'] and p['safety']!='SAFE']
  critical.extend((p['longitude'],p['latitude']) for p in result['reportPoints'].values())
  if region=='demo-west':
   report_point=max(road_points[road_count:],key=lambda p:min(distance(p,q) for q in critical))
   result['blockedRoadPoint']=position(report_point);category='ROAD_BLOCKED'
  else:
   report_point=max([interiors[h['plateauId']][min(used[h['plateauId']],63)] for h in unique_homes],key=lambda p:min(distance(p,q) for q in critical))
   category='WATER_OUTAGE' if region=='demo-central' else 'BUILDING_DAMAGE'
  result['reportPoints'][category]={**position(report_point),'communityId':region}
  result['people']+=residents
  print(region,'buildings',len(selected),'households',len(homes),'people',len(residents),'road locations',road_count)
 for p in result['people']:p.pop('_indoor',None)
 assert len(result['people'])==96 and len(result['households'])==64 and len(result['slots'])==64
 return result
if __name__=='__main__':
 source=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else pathlib.Path(__file__).with_name('venue-compact-roads.json')
 roads=json.loads(source.read_text(encoding='utf-8'))
 result=build(roads['features'] if isinstance(roads,dict) else roads)
 (ROOT/'cloud-sync/src/venue-compact-layout.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
