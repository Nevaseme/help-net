"""Reviewed demo building addresses; requires shapely>=2. Existing records are untouched."""
import argparse
import gzip
import hashlib
import json
import pathlib
import re
from shapely.geometry import shape, Point

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'android/app/src/main/assets'
EVIDENCE = pathlib.Path(__file__).with_name('venue-address-evidence.json')
OUTPUT = ROOT / 'cloud-sync/src/venue-buildings.json'


def load_buildings():
    result = {}
    for path in sorted((ASSETS / 'kyoto-building-assessment').glob('523546*.gzip')):
        raw = path.read_bytes()
        digest = hashlib.sha256(raw).hexdigest()
        for building in json.loads(gzip.decompress(raw))['buildings']:
            west, south, east, north = building['bbox']
            if east > 135.758 and west < 135.767 and north > 35.011 and south < 35.018:
                result[building['id']] = dict(building, asset=path.name, assetSha256=digest)
    return result


def interior_points(geometry, count):
    """Distinct simulated occupants inside both footprints; never unit locations."""
    points = [geometry.representative_point()]
    west, south, east, north = geometry.bounds
    candidates = []
    for y in range(1, 16):
        for x in range(1, 16):
            p = Point(west + (east-west)*x/16, south + (north-south)*y/16)
            if geometry.contains(p):
                candidates.append(p)
    while len(points) < count:
        if not candidates:
            raise ValueError('Insufficient distinct interior placements')
        # Spread within the genuine building, never perturb coordinates outside it.
        p = max(candidates, key=lambda c: min(((c.x-q.x)*91000)**2 + ((c.y-q.y)*111000)**2 for q in points))
        if any(p.equals(q) for q in points):
            raise ValueError('Duplicate interior placement')
        points.append(p)
        candidates.remove(p)
    return points


def build_catalog(evidence, buildings):
    shapes = {key: shape(value['geometry']) for key, value in buildings.items()}
    homes = []
    used = set()
    west, south, east, north = evidence['bbox']
    rows = evidence['buildings']
    for index, row in enumerate(rows):
        name = row['name']
        if not row.get('addressSources') or any(not s.get('url', '').startswith('https://') for s in row['addressSources']):
            raise ValueError(f'{name}: missing public address source')
        if not re.search(r'\d', row['address']) or '\u4ed8\u8fd1' in row['address']:
            raise ValueError(f'{name}: missing parcel-number address')
        osm = row['osm']
        geometry = shape(osm['geometry'])
        if not geometry.is_valid or geometry.is_empty or geometry.area <= 0:
            raise ValueError(f'{name}: invalid OSM footprint')
        matches = []
        for key, candidate in shapes.items():
            if not candidate.is_valid or not geometry.intersects(candidate):
                continue
            common = geometry.intersection(candidate)
            osm_coverage = common.area / geometry.area
            plateau_coverage = common.area / candidate.area
            # Both independent footprints must identify substantially the same building.
            if osm_coverage >= 0.75 and plateau_coverage >= 0.75:
                matches.append((key, common, osm_coverage, plateau_coverage))
        if len(matches) != 1:
            raise ValueError(f'{name}: missing or ambiguous footprint match ({len(matches)})')
        key, common, osm_coverage, plateau_coverage = matches[0]
        if key != row['expectedPlateauId']:
            raise ValueError(f'{name}: expected PLATEAU ID changed: {key}')
        if key in used:
            raise ValueError(f'{name}: duplicate PLATEAU building')
        used.add(key)
        building = buildings[key]
        count = 64 // len(rows) + (index < 64 % len(rows))
        for slot, point in enumerate(interior_points(common, count)):
            if not (west < point.x < east and south < point.y < north):
                raise ValueError(f'{name}: outside approved demo extent')
            if not shapes[key].contains(point) or not geometry.contains(point):
                raise ValueError(f'{name}: pin must be strictly inside both footprints')
            homes.append({
                'buildingId': key.removeprefix('bldg_'), 'plateauId': key,
                'buildingName': name, 'buildingUse': row['buildingUse'],
                'address': row['address'], 'latitude': point.y, 'longitude': point.x,
                'floor': '', 'room': '', 'addressPrecision': 'verified-building-address',
                'placementId': f'{key}:{slot+1}',
                'placementPrecision': 'simulated-occupant-within-verified-building-not-real-unit',
                'attributes': building['attributes'],
                'attributeSource': {'dataset': 'plateau-26100-kyoto-shi-2025',
                                    'asset': building['asset'], 'sha256': building['assetSha256']},
                'addressEvidence': {
                    'method': 'published-building-name-address-and-unique-footprint-overlap',
                    'sources': row['addressSources'],
                    'osmUrl': osm['url'], 'osmVersion': osm['version'], 'osmName': osm['name'],
                    'osmCoverage': round(osm_coverage, 6), 'plateauCoverage': round(plateau_coverage, 6),
                    'checkedDate': '2026-11-04', 'cadastralBoundaryVerified': False,
                },
            })
    return {
        'version': 2, 'dataset': 'plateau-26100-kyoto-shi-2025',
        'addressSource': 'Reviewed published building addresses + OpenStreetMap named footprints',
        'addressPrecision': 'verified-building-address; not cadastral parcel boundary verification',
        'scope': evidence['scope'], 'bbox': evidence['bbox'],
        'verifiedBuildingCount': len(used), 'simulatedPlacementCount': len(homes),
        'limitations': evidence['limitations'], 'licenses': [dict(evidence['geometrySource'])],
        'homes': homes,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='compare without changing files')
    args = parser.parse_args()
    result = build_catalog(json.loads(EVIDENCE.read_text(encoding='utf8')), load_buildings())
    content = json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n'
    if args.check:
        if OUTPUT.read_text(encoding='utf8') != content:
            raise SystemExit('Catalogue differs from reviewed evidence; regenerate and review.')
    else:
        OUTPUT.write_text(content, encoding='utf8')
    unique = {h['plateauId']:h for h in result['homes']}
    complete = sum(all(h['attributes'].get(k) is not None for k in ['year','floors','structureCode']) for h in unique.values())
    print(f'{len(result["homes"])} placements / {len(unique)} verified buildings; {complete} with all 3 PLATEAU attributes')


if __name__ == '__main__':
    main()
