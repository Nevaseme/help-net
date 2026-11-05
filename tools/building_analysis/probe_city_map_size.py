"""Measure a bounded sample; never call this an exact full-city download size."""
import json, pathlib, urllib.request, concurrent.futures, math
from probe_address_city import CACHE, tile

def main():
 audit=json.loads((CACHE/'audit.json').read_text(encoding='utf-8'));bounds=audit['boundary']['bbox']
 source=audit['mapSources']['openmaptiles']['inspection'];z=source['maxzoom']
 left,top=tile(bounds[0],bounds[3],z);right,bottom=tile(bounds[2],bounds[1],z)
 cells=sorted(set((round(left+(right-left)*i/3),round(top+(bottom-top)*j/3)) for i in range(4) for j in range(4)))
 def one(cell):
  x,y=cell;url=source['tiles'][0].replace('{z}',str(z)).replace('{x}',str(x)).replace('{y}',str(y));p=CACHE/f'tile-{z}-{x}-{y}.pbf'
  if not p.exists():
   req=urllib.request.Request(url,headers={'User-Agent':'Tasukeai-DataAudit/1.0 (non-commercial prototype)'})
   with urllib.request.urlopen(req,timeout=30) as response:
    data=response.read(2_000_001)
    if len(data)>2_000_000:raise ValueError('Sample tile unexpectedly large')
    p.write_bytes(data)
  return {'z':z,'x':x,'y':y,'bytes':p.stat().st_size}
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:sample=list(pool.map(one,cells))
 counts={k:v for k,v in audit['boundary']['boundingRectangleTileCounts'].items() if int(k)<=z}
 result={'municipality':'京都市','nativeMaxZoom':z,'downloadTileCounts':counts,'totalTilesZoom8to14':sum(counts.values()),'sample':sample,'sampleBytes':sum(x['bytes'] for x in sample),'sampleMeanBytes':sum(x['bytes'] for x in sample)/len(sample),'note':'Even spatial sample only. Not a full download size, excludes fonts/sprites/styles and lower-zoom tile sizes.','tilejsonUrl':audit['mapSources']['openmaptiles']['url']}
 # Optional installed research dependency; counting intersections downloads nothing.
 try:
  from shapely.geometry import shape, box
  from shapely.ops import unary_union
  geometry=unary_union([shape(f['geometry']) for f in json.loads((CACHE/'kyoto-city.geojson').read_text(encoding='utf-8'))['features']])
  def ll(x,y,zoom):return (x/2**zoom*360-180,math.degrees(math.atan(math.sinh(math.pi*(1-2*y/2**zoom)))))
  intersecting={}
  for zoom in range(8,z+1):
   left,top=tile(bounds[0],bounds[3],zoom);right,bottom=tile(bounds[2],bounds[1],zoom);n=0
   for x in range(left,right+1):
    for y in range(top,bottom+1):
     w,north=ll(x,y,zoom);east,south=ll(x+1,y+1,zoom)
     if geometry.intersects(box(w,south,east,north)):n+=1
   intersecting[str(zoom)]=n
  result['cityIntersectingTileCounts']=intersecting
  result['cityIntersectingTileTotal']=sum(intersecting.values())
 except ImportError:result['geometryCount']='not measured: shapely unavailable'
 (CACHE/'map-size-sample.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
