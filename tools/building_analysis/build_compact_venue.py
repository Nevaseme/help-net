"""Generate initial venue data across three adjacent four-block neighborhoods."""
import gzip, hashlib, json, math, pathlib, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
REGIONS=[
 ('demo-west','御所南西町内会','Gosho South West',135.7617,135.7624,135.7632,24,8,11),
 ('demo-central','御所南中央町内会','Gosho South Central',135.7632,135.7639,135.7650,16,0,6),
 ('demo-east','御所南東町内会','Gosho South East',135.7650,135.7658,135.7665,24,8,11)]
def inside(point,geometry):
 def hit(r):
  x,y=point;v=False
  for a,b in zip(r,r[1:]+r[:1]):
   if (a[1]>y)!=(b[1]>y) and x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:v=not v
  return v
 return any(hit(p[0]) and not any(hit(h) for h in p[1:]) for p in (geometry['coordinates'] if geometry['type']=='MultiPolygon' else [geometry['coordinates']]))
def area(g):
 def a(r):
  ox,oy=r[0];q=[(x-ox,y-oy) for x,y in r]
  return abs(sum(x[0]*y[1]-y[0]*x[1] for x,y in zip(q,q[1:]+q[:1])))*.5*91000*111195
 return sum(a(p[0])-sum(a(h) for h in p[1:]) for p in (g['coordinates'] if g['type']=='MultiPolygon' else [g['coordinates']]))
def distance(a,b):return math.hypot((a[0]-b[0])*91000,(a[1]-b[1])*111195)
def position(p):return {'latitude':p[1],'longitude':p[0]}
def center(b):return ((b['bbox'][0]+b['bbox'][2])/2,(b['bbox'][1]+b['bbox'][3])/2)
def spread(pool,count,avoid=()):
 pool=sorted(set(pool));assert len(pool)>=count,(len(pool),count)
 chosen=[];anchors=list(avoid)
 if not anchors and count:chosen.append(pool.pop(len(pool)//2));anchors=chosen[:]
 nearest={p:min(distance(p,q) for q in anchors) for p in pool}
 while len(chosen)<count:
  p=max(pool,key=lambda p:nearest[p]);chosen.append(p);pool.remove(p)
  for q in pool:nearest[q]=min(nearest[q],distance(q,p))
 return chosen
def lines(roads):
 for f in roads:
  g=f['geometry']
  if f['properties'].get('class') not in ['minor','tertiary','secondary','primary','service']:continue
  if g['type']=='LineString':yield g['coordinates']
  elif g['type']=='MultiLineString':yield from g['coordinates']
def junction(x,y,segments):
 hs=[];vs=[]
 for a,b in segments:
  dx=abs(b[0]-a[0])*91000;dy=abs(b[1]-a[1])*111195
  if dx>dy*5 and min(a[0],b[0])-.0002<x<max(a[0],b[0])+.0002 and abs((a[1]+b[1])/2-y)<.0002:hs.append((a,b))
  if dy>dx*5 and min(a[1],b[1])-.0002<y<max(a[1],b[1])+.0002 and abs((a[0]+b[0])/2-x)<.0002:vs.append((a,b))
 points=[]
 for (x1,y1),(x2,y2) in hs:
  for (x3,y3),(x4,y4) in vs:
   d=(x1-x2)*(y3-y4)-(y1-y2)*(x3-x4)
   if abs(d)<1e-16:continue
   t=((x1-x3)*(y3-y4)-(y1-y3)*(x3-x4))/d;u=-((x1-x2)*(y1-y3)-(y1-y2)*(x1-x3))/d
   if -.02<=t<=1.02 and -.02<=u<=1.02:points.append((x1+t*(x2-x1),y1+t*(y2-y1)))
 assert points,(x,y)
 return min(points,key=lambda p:distance(p,(x,y)))
def build(roads):
 buildings=[];sources=[]
 for path in sorted((ROOT/'demo/data/cities/26100/buildings').glob('523546*.gzip')):
  raw=path.read_bytes();sources.append({'asset':path.name,'sha256':hashlib.sha256(raw).hexdigest()});buildings+=json.loads(gzip.decompress(raw))['buildings']
 known={h['plateauId']:h for h in json.loads((ROOT/'cloud-sync/src/venue-buildings.json').read_text(encoding='utf8'))['homes']}
 segments=[(a,b) for line in lines(roads) for a,b in zip(line,line[1:])]
 result={'version':3,'sources':sources,'communities':[],'households':[],'people':[],'slots':[],'roads':{},'reportPoints':{},'blocks':{}}
 indoor_offset=0
 for region,ja,en,w,m,e,household_count,families,road_count in REGIONS:
  grid=[[junction(x,y,segments) for x in [w,m,e]] for y in [35.01365,35.0149,35.0162]]
  boundary=[grid[0][0],grid[0][2],grid[2][2],grid[2][0]];polygon={'type':'Polygon','coordinates':[boundary]}
  blocks=[{'type':'Polygon','coordinates':[[grid[y][x],grid[y][x+1],grid[y+1][x+1],grid[y+1][x]]]} for y in range(2) for x in range(2)]
  result['blocks'][region]=[b['coordinates'][0] for b in blocks]
  included=[b for b in buildings if inside(center(b),polygon)]
  eligible=[b for b in included if area(b['geometry'])>=25]
  per_block=[];attendees_per_block=[];family_ids=set()
  for block in blocks:
   candidates=[b for b in eligible if inside(center(b),block)]
   large=[b for b in candidates if area(b['geometry'])>=100]
   fp=spread([center(b) for b in large],families//4) if families else []
   family=[next(b for b in large if center(b)==p) for p in fp];family_ids.update(b['id'] for b in family)
   rest=[b for b in candidates if b['id'] not in family_ids]
   sp=spread([center(b) for b in rest],household_count//4-len(family),fp)
   chosen=family+[next(b for b in rest if center(b)==p) for p in sp];per_block.append(chosen)
   if region=='demo-central':
    remaining=[b for b in candidates if b not in chosen]
    pts=spread([center(b) for b in remaining],16,[center(b) for b in chosen])
    attendees_per_block.append([next(b for b in remaining if center(b)==p) for p in pts])
  household_buildings=[row[i] for i in range(household_count//4) for row in per_block]
  attendee_buildings=[row[i] for i in range(16) for row in attendees_per_block] if attendees_per_block else []
  occupied={b['id'] for b in household_buildings+attendee_buildings}
  visitors=[b for b in eligible if b['id'] not in occupied and area(b['geometry'])>=100]
  assert len(visitors)>=8,region
  interiors={};used={b['id']:0 for b in eligible}
  def points(b):
   if b['id'] not in interiors:
    a,s,c,n=b['bbox'];pool=[(a+(c-a)*x/21,s+(n-s)*y/21) for x in range(1,21) for y in range(1,21)]
    pool=[p for p in pool if inside(p,b['geometry']) and inside(p,polygon)]
    assert pool,b['id']
    interiors[b['id']]=spread(pool,min(12,len(pool)))
   return interiors[b['id']]
  def home(b,hid):
   base=known.get(b['id'],{})
   return {'householdId':hid,'buildingId':b['id'].removeprefix('bldg_'),'plateauId':b['id'],'address':base.get('address',f"{en}, building {included.index(b)+1:03}"),**{k:base[k] for k in ['addressEvidence','buildingUse'] if k in base},'addressPrecision':base.get('addressPrecision','building-reference-not-postal-address'),**position(points(b)[0]),'floor':'1','room':'','attributes':b['attributes']}
  def indoor_position(b,away):
   if away:b=min(visitors,key=lambda v:(used[v['id']],-distance(center(v),center(b))))
   p=points(b)[used[b['id']]];used[b['id']]+=1;return position(p)
  residents=[];resident_buildings=[]
  for i,b in enumerate(household_buildings):
   h=home(b,f'{region}-home-{i+1:02}');result['households'].append(h)
   for _ in range(3 if b['id'] in family_ids else 1):
    residents.append({'communityId':region,'home':h,'position':position(points(b)[0]),'safety':'SAFE','injury':'UNINJURED','note':'','observations':{'impact':False,'enclosed':False}});resident_buildings.append(b)
  pool=[];nearby=[b for b in buildings if b['bbox'][2]>=w-.0003 and b['bbox'][0]<=e+.0003 and b['bbox'][3]>=35.0134 and b['bbox'][1]<=35.0165]
  for a,b in segments:
   if max(a[0],b[0])<w-.0003 or min(a[0],b[0])>e+.0003 or max(a[1],b[1])<35.0134 or min(a[1],b[1])>35.0165:continue
   steps=max(1,math.ceil(distance(a,b)/2))
   for i in range(steps+1):
    p=(a[0]+(b[0]-a[0])*i/steps,a[1]+(b[1]-a[1])*i/steps)
    if inside(p,polygon) and not any(inside(p,x['geometry']) for x in nearby if x['bbox'][0]<=p[0]<=x['bbox'][2] and x['bbox'][1]<=p[1]<=x['bbox'][3]):pool.append(p)
  road_points=spread(pool,road_count+(16 if region=='demo-central' else 8));result['roads'][region]=list(map(position,road_points))
  road_ids={int((i+.5)*len(residents)/road_count):i for i in range(road_count)}
  for i,j in road_ids.items():residents[i]['position']=position(road_points[j])
  unknown={2:7,3:3} if region=='demo-central' else {}
  assert not set(unknown)&set(road_ids)
  for i,days in unknown.items():residents[i].update(positionUnknown=True,lastKnownAgeDays=days,lastKnownPosition=residents[i]['position'],position=None,safety='SOS' if i==2 else 'UNREPORTED',injury='UNKNOWN')
  indoor=[]
  for i,p in enumerate(residents):
   if i in road_ids or i in unknown:continue
   p['position']=indoor_position(resident_buildings[i],(indoor_offset+6)//10>(indoor_offset+5)//10);indoor_offset+=1;indoor.append(p)
  first=indoor[0];second=max(indoor,key=lambda p:distance((p['position']['longitude'],p['position']['latitude']),(first['position']['longitude'],first['position']['latitude'])))
  for p,status in zip([first,second],['SOS','UNREPORTED']):p.update(safety=status,injury='UNKNOWN',note='I need help.' if status=='SOS' else '',observations={'impact':status=='SOS','enclosed':status=='UNREPORTED'})
  if region=='demo-central':
   # Six separate, known-location residents. Keep the two unknown examples separate.
   candidates=[p for p in residents if not p.get('positionUnknown')]
   chosen=spread([(p['position']['longitude'],p['position']['latitude']) for p in candidates],6)
   for p in candidates:p.update(safety='SAFE',injury='UNINJURED',note='',observations={'impact':False,'enclosed':False})
   patterns=[('SOS',False,False),('SOS',True,True),('SOS',True,False),('UNREPORTED',False,False),('UNREPORTED',True,True),('UNREPORTED',False,True)]
   for xy,(status,impact,enclosed) in zip(chosen,patterns):
    p=next(p for p in candidates if (p['position']['longitude'],p['position']['latitude'])==xy)
    p.update(safety=status,injury='UNKNOWN',note='I need help.' if status=='SOS' else '',observations={'impact':impact,'enclosed':enclosed})
  k=0
  for i,b in enumerate(attendee_buildings):
   h=home(b,f'attendee-home-{i+1:02}')
   # Rotate outdoor attendees across the four home blocks, not always block four.
   if i%16 not in [3,6,9,12]:p=indoor_position(b,(k+6)//10>(k+5)//10);k+=1
   else:p=position(road_points[road_count+i//4])
   result['slots'].append({**p,'home':h,'communityId':region})
  critical=[(p['position']['longitude'],p['position']['latitude']) for p in [first,second]]+[(p['longitude'],p['latitude']) for p in result['reportPoints'].values()]
  if region=='demo-west':
   rp=max(road_points[road_count:],key=lambda p:min(distance(p,q) for q in critical));category='ROAD_BLOCKED';result['blockedRoadPoint']=position(rp)
  else:
   b=max(visitors,key=lambda b:min(distance(center(b),q) for q in critical));rp=points(b)[used[b['id']]];category='WATER_OUTAGE' if region=='demo-central' else 'BUILDING_DAMAGE'
  result['reportPoints'][category]={**position(rp),'communityId':region}
  result['communities'].append({'id':region,'nameJa':ja,'nameEn':en,'latitude':sum(p[1] for p in boundary)/4,'longitude':sum(p[0] for p in boundary)/4,'boundary':boundary})
  result['people']+=residents
  print(region,'physical buildings',len(included),'sample homes',len(household_buildings),'people',len(residents),'participant homes',len(attendee_buildings))
 result['communityLongitudeCuts']=[sum(p[0] for p in c['boundary'][1:3])/2 for c in result['communities'][:2]]
 assert len(result['people'])==96 and len(result['households'])==64 and len(result['slots'])==64
 return result
if __name__=='__main__':
 source=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else pathlib.Path(__file__).with_name('venue-compact-roads.json')
 roads=json.loads(source.read_text(encoding='utf8'));result=build(roads['features'] if isinstance(roads,dict) else roads)
 (ROOT/'cloud-sync/src/venue-compact-layout.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')
