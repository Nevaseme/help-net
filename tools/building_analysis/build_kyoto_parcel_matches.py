"""Build offline parcel candidates by polygon intersection, never nearest lookup.

Dependencies: shapely>=2, pyproj, pyshp. Downloaded originals stay in .implementation.
The source is MLIT's standardized Kyoto municipal cadastral-reference derivative;
its filename year is NOT proof of the original municipal snapshot date.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import json
import logging
from pathlib import Path
import re
import urllib.request
import warnings
import zipfile

import pyproj
import shapefile
import shapely
from shapely.geometry import shape
from shapely.ops import transform, unary_union
from shapely.strtree import STRtree

ROOT = Path(__file__).resolve().parents[2]
DEFAULT = ROOT / '.implementation/kyoto-parcel-match'
ASSETS = ROOT / 'android/app/src/main/assets/kyoto-building-assessment'
PAGE = 'https://www.mlit.go.jp/tochi_fudousan_kensetsugyo/tochi_fudousan_kensetsugyo_tk17_000001_00063.html'
ZIP_URL = 'https://www.mlit.go.jp/tochi_fudousan_kensetsugyo/content/26100_Shapefile.zip'
EXPECTED_HASH = 'fe5784b439f63cf8f1ee4f632a34cf169cd0ee831d3c6a016b0cf508aa88ba50'
SOURCE = {
    'id': 'mlit-kyoto-parcel-prototype-2025',
    'version': 'MLIT prototype filename 2025; original municipal snapshot unconfirmed',
    'url': PAGE, 'downloadUrl': ZIP_URL, 'sha256': EXPECTED_HASH,
    'retrievedDate': '2026-11-04', 'crs': 'EPSG:6674', 'encoding': 'Shift_JIS',
    'decoding': 'Shift_JIS; CP932 fallback for vendor-extension kanji',
    'originalDatasetUrl': 'https://data.city.kyoto.lg.jp/dataset/00677/',
    'attribution': '京都市の地番図を基にした国土交通省「土地境界データ（地番現況図）試作データ」を加工して作成',
    'license': 'PDL-1.0; original Kyoto municipal data CC-BY-4.0',
    'licenseUrls': ['https://www.mlit.go.jp/link.html',
                    'https://www.digital.go.jp/resources/open_data/public_data_license_v1.0',
                    'https://creativecommons.org/licenses/by/4.0/'],
}
LIMITATIONS = [
    '地番候補は建物ポリゴンと地番図の重なりから算出したもので、確認済みの住居住所ではありません。',
    '土地の境界・所有権・面積を証明する資料ではなく、申請や権利確認には使用できません。',
    '地番の欠落、筆界未定、形状・位置の誤差、時点差があり得ます。',
    '同一地番に複数建物、同一建物に複数地番が対応する場合があります。',
    '階・部屋・居住者を特定する情報は含みません。',
]


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write_json(path, value, compressed=False):
    raw = (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')
    Path(path).write_bytes(gzip.compress(raw, compresslevel=9, mtime=0) if compressed else raw)


def acquire(output):
    source = output / 'source'
    source.mkdir(parents=True, exist_ok=True)
    archive = source / '26100_Shapefile.zip'
    if not archive.exists():
        with urllib.request.urlopen(ZIP_URL, timeout=120) as response, archive.open('wb') as target:
            while block := response.read(1024 * 1024):
                target.write(block)
    if sha(archive) != EXPECTED_HASH:
        raise ValueError('Source archive changed; review metadata and update pinned hash explicitly')
    folder = source / 'shapefile'
    folder.mkdir(exist_ok=True)
    entries = []
    with zipfile.ZipFile(archive, metadata_encoding='cp932') as zipped:
        for info in zipped.infolist():
            ext = Path(info.filename).suffix
            if ext not in {'.shp', '.shx', '.prj', '.dbf', '.cpg'}:
                continue
            target = folder / ('kyoto' + ext)
            if not target.exists():
                target.write_bytes(zipped.read(info))
            # Check previously extracted bytes against the pinned archive too.
            if hashlib.sha256(zipped.read(info)).hexdigest() != sha(target):
                raise ValueError(f'Extracted source changed: {target}')
            entries.append({'name': info.filename, 'bytes': target.stat().st_size, 'sha256': sha(target)})
    return folder / 'kyoto.shp', entries


def polygonal(geometry):
    if geometry.geom_type in ('Polygon', 'MultiPolygon'):
        return geometry
    if geometry.geom_type == 'GeometryCollection':
        return unary_union([g for g in geometry.geoms if g.geom_type in ('Polygon', 'MultiPolygon')])
    return shapely.Polygon()


def clean(geometry):
    repaired = not geometry.is_valid
    return polygonal(shapely.make_valid(geometry) if repaired else geometry), repaired


def parcel_info(properties, index):
    number = str(properties['地番']).strip()
    prefix = ''.join(str(properties[k] or '').strip() for k in ('都道府県名', '市区町村名', '大字名', '小字名'))
    return {'parcelId': f'kyoto-mlit2025:{index}', 'address': prefix + number,
            'wardCode': properties['行政区域C'], 'parcelNumber': number,
            'sourceFeatureIndex': index,
            'hasNumber': bool(re.search(r'\d', number))}


def match_building(building, tree, geometries, metadata, repaired_parcels=None):
    """Input geometries must all be in the same metric CRS (EPSG:6674)."""
    building, repaired = clean(building)
    if building.is_empty or building.area <= 0:
        return {'status': 'unmatched', 'confidence': 'none', 'candidates': [], 'flags': ['invalid-building']}
    candidates, pieces, ignored = [], [], 0
    for i in tree.query(building, predicate='intersects'):
        i = int(i)
        parcel = geometries[i]
        intersection = building.intersection(parcel)
        area = intersection.area
        coverage = area / building.area
        # Boundary touches and sub-decimetre/sliver overlaps do not support candidates.
        if area < 0.1 or coverage < 0.001:
            ignored += area > 0
            continue
        info = metadata[i]
        candidates.append({**{k: v for k, v in info.items() if k != 'hasNumber'},
                           'buildingCoverage': round(coverage, 6),
                           'parcelCoverage': round(area / parcel.area, 6),
                           'overlapM2': round(area, 3),
                           'numbered': info['hasNumber'],
                           'geometryRepaired': bool(repaired_parcels and i in repaired_parcels)})
        pieces.append(intersection)
    candidates.sort(key=lambda c: (-c['buildingCoverage'], c['parcelId']))
    union_coverage = unary_union(pieces).area / building.area if pieces else 0
    total = sum(c['buildingCoverage'] for c in candidates)
    flags = []
    if repaired:
        flags.append('building-geometry-repaired')
    if ignored:
        flags.append('subthreshold-intersections-ignored')
    if total - union_coverage > 0.01:
        flags.append('overlapping-source-parcels')
    if any(not c['numbered'] for c in candidates):
        flags.append('unnumbered-parcel')
    if any(c['geometryRepaired'] for c in candidates):
        flags.append('parcel-geometry-repaired')
    significant = [c for c in candidates if c['buildingCoverage'] >= 0.01]
    if not candidates:
        status, confidence = 'unmatched', 'none'
    elif len(significant) >= 2:
        status, confidence = 'multiple-parcels', 'ambiguous'
    elif (candidates[0]['buildingCoverage'] >= 0.95 and len(significant) == 1
          and candidates[0]['numbered'] and not flags):
        status, confidence = 'single-parcel', 'high-geometric-overlap'
    else:
        status, confidence = 'partial', 'needs-review'
    return {'status': status, 'confidence': confidence, 'candidates': candidates,
            'unionBuildingCoverage': round(union_coverage, 6), 'flags': flags}


def build(output, mesh_filter=None):
    shp, entries = acquire(output)
    crs = pyproj.CRS.from_wkt(shp.with_suffix('.prj').read_text())
    if crs.to_epsg() != 6674:
        raise ValueError(f'Unexpected source projection: {crs}')
    projector = pyproj.Transformer.from_crs(4326, crs, always_xy=True)
    back = pyproj.Transformer.from_crs(crs, 4326, always_xy=True)
    geometries, metadata, repaired_parcels = [], [], set()
    search_rows, target_parcels = [], []
    parcel_counts = Counter()
    # pyshp warns loudly on malformed ring orientation; shape errors are counted below.
    logging.getLogger('shapefile').setLevel(logging.ERROR)
    reader = shapefile.Reader(str(shp), encoding='shift_jis')
    with warnings.catch_warnings():
        warnings.simplefilter('ignore', UserWarning)
        fallback_reader = shapefile.Reader(str(shp), encoding='cp932')
    excluded_source_rows = []
    decoding_fallback_rows = []
    for index in range(len(reader)):
        try:
            try:
                record = reader.shapeRecord(index)
            except shapefile.ShapefileException as error:
                if 'decode' not in str(error):
                    raise
                record = fallback_reader.shapeRecord(index)
                parcel_counts['cp932Recovered'] += 1
                decoding_fallback_rows.append(index)
            geom, repaired = clean(shape(record.shape.__geo_interface__))
        except (shapefile.ShapefileException, ValueError) as error:
            parcel_counts['malformedExcluded'] += 1
            excluded_source_rows.append({'index': index, 'reason': str(error)})
            continue
        if geom.is_empty or geom.area <= 0:
            parcel_counts['emptyExcluded'] += 1
            continue
        if repaired:
            repaired_parcels.add(len(geometries))
            parcel_counts['repaired'] += 1
        info = parcel_info(record.record.as_dict(), index)
        point = geom.representative_point()
        lng, lat = back.transform(point.x, point.y)
        info.update(longitude=round(lng, 8), latitude=round(lat, 8))
        if info['hasNumber']:
            search_rows.append({**{k: v for k, v in info.items() if k != 'hasNumber'},
                                'precision': 'parcel-area', 'matchLevel': 'parcel',
                                'source': SOURCE['id']})
        if '讃州寺町203' in info['address']:
            target_parcels.append(info)
        parcel_counts[info['wardCode']] += 1
        geometries.append(geom)
        metadata.append(info)
        if index % 100000 == 0:
            print(f'Loaded parcels {index:,}/{len(reader):,}', flush=True)
    tree = STRtree(geometries)
    write_json(output / 'parcel-address-search.json.gzip',
               {'schemaVersion': 1, 'source': SOURCE, 'addresses': search_rows}, True)
    write_json(output / 'search.json.gzip', {'schemaVersion': 1, 'source': SOURCE,
        'rows': [[r['parcelId'], r['address'], r['longitude'], r['latitude'], 'parcel-area',
                  {'matchLevel': 'parcel', 'source': SOURCE['id'], 'sourceVersion': SOURCE['version'],
                   'municipalityCode': '26100', 'wardCode': r['wardCode'],
                   'sourceFeatureIndex': r['sourceFeatureIndex']}]
                 for r in search_rows]}, True)
    del search_rows
    print(f'Indexed {len(geometries):,} parcels', flush=True)
    tiles = output / 'tiles'
    tiles.mkdir(exist_ok=True)
    counts, chunks, evidence, known_results = Counter(), [], {}, []
    known = {r['expectedPlateauId']: r for r in json.loads((Path(__file__).with_name('venue-address-evidence.json')).read_text(encoding='utf8'))['buildings']}
    seen = set()
    for path in sorted(ASSETS.glob('*.json.gzip')):
        mesh = path.name.split('.')[0]
        if mesh_filter and not mesh.startswith(mesh_filter):
            continue
        source = json.loads(gzip.decompress(path.read_bytes()))
        records = {}
        bounds = [180., 90., -180., -90.]
        for building in source['buildings']:
            key = building['id']
            if key in seen:
                raise ValueError(f'Duplicate PLATEAU ID: {key}')
            seen.add(key)
            g = transform(projector.transform, shape(building['geometry']))
            result = match_building(g, tree, geometries, metadata, repaired_parcels)
            records[key] = result
            counts[result['status']] += 1
            counts['buildings'] += 1
            counts['candidates'] += len(result['candidates'])
            w, s, e, n = building['bbox']
            bounds = [min(bounds[0], w), min(bounds[1], s), max(bounds[2], e), max(bounds[3], n)]
            if key in known:
                row = known[key]
                # Compare only complete municipality/ward/town+number strings.
                candidates = [c['address'] for c in result['candidates']]
                known_results.append({'plateauId': key, 'name': row['name'], 'verifiedAddress': row['address'],
                                      'exactAddressAmongCandidates': row['address'] in candidates, **result})
            # One reproducible example of each status, plus a large multi-parcel case.
            if result['status'] not in evidence or (result['status'] == 'multiple-parcels' and len(result['candidates']) > len(evidence[result['status']]['candidates'])):
                evidence[result['status']] = {'plateauId': key, 'mesh': mesh, **result}
        target = tiles / f'{mesh}.json.gzip'
        write_json(target, {'schemaVersion': 1, 'mesh': mesh, 'sourceId': SOURCE['id'], 'buildings': records}, True)
        chunks.append({'mesh': mesh, 'file': target.name, 'sha256': sha(target), 'bytes': target.stat().st_size,
                       'bbox': bounds, 'buildingCount': len(records), 'buildingSourceSha256': sha(path)})
        print(f'Matched {mesh}: {len(records):,}; total {counts["buildings"]:,}', flush=True)
    index = {'schemaVersion': 1, 'source': SOURCE, 'precision': 'cadastral-parcel-candidate-not-verified-postal-address',
             'limitations': LIMITATIONS, 'counts': dict(counts), 'chunks': chunks,
             'algorithm': {'crs': 'EPSG:6674', 'minimumOverlapM2': 0.1, 'minimumBuildingCoverage': 0.001,
                           'significantBuildingCoverage': 0.01, 'singleParcelMinimumCoverage': 0.95,
                           'nearestAssignment': False, 'geometryRepair': 'shapely.make_valid; polygonal components only'},
             'parcelCounts': {'sourceRecords': len(reader), 'indexed': len(geometries), **dict(parcel_counts)}}
    write_json(tiles / 'index.json', index)
    write_json(output / 'validation.json', {'knownBuildings': known_results, 'examples': evidence,
               'sanshuji203Parcels': target_parcels,
               'excludedSourceRows': excluded_source_rows,
               'decodingFallbackRows': decoding_fallback_rows,
               'sourceMembers': entries, 'sourceCrsWkt': crs.to_wkt(),
               'parcelBoundsWgs84': back.transform_bounds(*reader.bbox),
               'libraries': {'shapely': shapely.__version__, 'pyproj': pyproj.__version__, 'pyshp': shapefile.__version__},
               'counts': dict(counts), 'totalCompressedBytes': sum(c['bytes'] for c in chunks)})
    print(json.dumps({'counts': dict(counts), 'knownMatched': sum(r['exactAddressAmongCandidates'] for r in known_results),
                      'knownTotal': len(known_results), 'compressedBytes': sum(c['bytes'] for c in chunks)}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=DEFAULT)
    parser.add_argument('--mesh-prefix', help='Optional smoke test scope; omit for all packaged Kyoto buildings')
    args = parser.parse_args()
    build(args.output, args.mesh_prefix)
