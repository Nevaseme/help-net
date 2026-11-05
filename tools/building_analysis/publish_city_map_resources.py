"""Publish the already measured public map snapshot on the existing Pages host.

No additional upstream downloading and no user/device records. Paths mirror the
original public assets; a generated allow-list leaves other online regions alone.
"""
import hashlib,json,pathlib,urllib.parse
root=pathlib.Path('demo/data/cities');cache=pathlib.Path('.implementation/address-city/map-resources');paths=set()
for code in ['26100','22205','13103']:
 manifest=json.loads((root/code/'map-manifest.json').read_text(encoding='utf8'))
 for row in manifest['resources']:
  url=urllib.parse.urlparse(row['url']);assert url.scheme=='https' and url.netloc=='tiles.openfreemap.org'
  relative=urllib.parse.unquote(url.path).lstrip('/');assert '..' not in pathlib.PurePosixPath(relative).parts
  source=cache/hashlib.sha256(row['url'].encode()).hexdigest();data=source.read_bytes();assert hashlib.sha256(data).hexdigest()==row['sha256']
  target=root/'map-assets'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);paths.add(url.path)
pathlib.Path('lab/web/city-map-resources.mjs').write_text('// Public OpenFreeMap snapshot; original source paths only.\nexport const CITY_MAP_PATHS=new Set('+json.dumps(sorted(paths),separators=(',',':'))+');\n',encoding='utf8')
(root/'map-assets'/'NOTICE.txt').write_text('Public offline city-map snapshot. Source: https://openfreemap.org/\nMap data: OpenStreetMap contributors, ODbL https://www.openstreetmap.org/copyright\nSchema: OpenMapTiles https://openmaptiles.org/\nFonts: Noto Sans, SIL Open Font License https://github.com/notofonts/noto-fonts\nOriginal URLs, file hashes and sizes are recorded in each map-manifest.json.\n',encoding='utf8')
print('Published unique reference resources:',len(paths),'bytes:',sum(p.stat().st_size for p in (root/'map-assets').rglob('*') if p.is_file()))
