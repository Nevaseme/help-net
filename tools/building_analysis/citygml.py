"""Read PLATEAU CityGML 2.0 building attributes without imputing missing data.

Only geographic EPSG:6697 (JGD2011 + height), 6668 and 4326 are supported.
The source axis order is latitude, longitude; GeoJSON uses longitude, latitude.
LOD0 footprints/roof edges are retained with holes. No bbox-derived footprints.
"""
import math
from lxml import etree as ET

B = '{http://www.opengis.net/citygml/building/2.0}'
G = '{http://www.opengis.net/gml}'
NS = {'b': B[1:-1], 'g': G[1:-1]}


def number(value, *, integer=False, minimum=0, maximum=1e10):
    try:
        n = float(value)
        if not math.isfinite(n) or not minimum <= n <= maximum or (integer and n != int(n)):
            return None
        return int(n) if integer else n
    except (ValueError, TypeError):
        return None


def read_buildings(source):
    crs = None
    dimension = None
    seen = set()
    for _, elem in ET.iterparse(source, events=('end',), resolve_entities=False, load_dtd=False, no_network=True):
        if elem.tag == G + 'Envelope' and crs is None:
            crs = elem.get('srsName', '')
            dimension = elem.get('srsDimension')
            if crs.rsplit('/', 1)[-1] not in ('6697', '6668', '4326'):
                raise ValueError('Unsupported CRS: ' + crs)
        if elem.tag != B + 'Building':
            continue
        if crs is None or dimension not in ('2', '3'):
            raise ValueError('Missing CRS or coordinate dimension')
        identifier = elem.get(G + 'id')
        if not identifier or identifier in seen:
            raise ValueError('Missing or duplicate building ID')
        seen.add(identifier)
        raw = {key: elem.findtext('b:' + tag, namespaces=NS) for key, tag in
               [('year','yearOfConstruction'),('floors','storeysAboveGround'),('heightM','measuredHeight')]}
        for key, tag in [('structureCode','buildingStructureType'),('floorAreaM2','totalFloorArea')]:
            found = elem.xpath('./*[local-name()="buildingDetailAttribute"]/*/*[local-name()=$name]', name=tag)
            raw[key] = found[0].text if len(found) == 1 else None
        values = {
            'year': number(raw['year'], integer=True, minimum=2, maximum=2025),
            'floors': number(raw['floors'], integer=True, minimum=1, maximum=300),
            'heightM': number(raw['heightM'], minimum=0.01, maximum=2000),
            'floorAreaM2': number(raw['floorAreaM2'], minimum=0.01),
            'structureCode': raw['structureCode'] if raw['structureCode'] in ('601','602','603','604','605','606','607','608','609','610') else None,
        }
        # Unit conversion must be explicit; incompatible units are not silently accepted.
        height = elem.find('b:measuredHeight', NS)
        if height is not None and height.get('uom') != 'm':
            values['heightM'] = None
        area = elem.xpath('./*[local-name()="buildingDetailAttribute"]/*/*[local-name()="totalFloorArea"]')
        if area and area[0].get('uom') not in ('m2','m^2'):
            values['floorAreaM2'] = None
        geometry = None
        geometry_source = None
        for tag in ('lod0FootPrint', 'lod0RoofEdge'):
            node = elem.find('b:' + tag, NS)
            if node is None:
                continue
            polygons = []
            valid = True
            for polygon in node.findall('.//g:Polygon', NS):
                rings = []
                for ring_node in [polygon.find('g:exterior', NS), *polygon.findall('g:interior', NS)]:
                    pos = ring_node.find('.//g:posList', NS) if ring_node is not None else None
                    if pos is None:
                        valid = False
                        break
                    parents = [pos, *pos.iterancestors()]
                    local_crs = next((p.get('srsName') for p in parents if p.get('srsName')), crs)
                    local_dim = next((p.get('srsDimension') for p in parents if p.get('srsDimension')), dimension)
                    if local_crs != crs or local_dim not in ('2','3'):
                        raise ValueError('Mixed CRS or unsupported coordinate dimension')
                    try:
                        coords = list(map(float, (pos.text or '').split()))
                        dim = int(local_dim)
                        ring = [[coords[i+1], coords[i]] for i in range(0,len(coords),dim)]
                        if len(coords) % dim or len(ring) < 4 or ring[0] != ring[-1] or len(set(map(tuple, ring))) < 3:
                            raise ValueError('Invalid ring')
                        if not all(math.isfinite(v) for v in coords) or not all(-180<=x<=180 and -90<=y<=90 for x,y in ring):
                            raise ValueError('Invalid geographic coordinates')
                        rings.append(ring)
                    except (ValueError, IndexError):
                        valid = False
                        break
                if rings:
                    polygons.append(rings)
            if valid and polygons:
                geometry = {'type':'MultiPolygon','coordinates':polygons}
                geometry_source = tag
                break
        yield {'id':identifier,'attributes':values,'raw':raw,'geometry':geometry,
               'geometrySource':geometry_source,'missing':[k for k,v in values.items() if v is None]+(['geometry'] if geometry is None else [])}
        # Release geometry/textures as each top-level member is completed.
        parent = elem.getparent()
        elem.clear()
        if parent is not None:
            while parent.getprevious() is not None:
                del parent.getparent()[0]
