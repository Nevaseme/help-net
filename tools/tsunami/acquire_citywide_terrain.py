"""Inventory and sample the public 2019 ALB grid in a bounded Atami envelope.

This is an acquisition/coverage audit, not an inundation calculation. Missing
samples remain missing. Existing downloaded tiles are reused with fresh hashes.
The envelope deliberately includes Hatsushima and a margin beyond municipal
extrema; it is not an administrative-boundary polygon.
"""
import argparse
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

DEPENDENCIES = Path(__file__).resolve().parents[2] / '.implementation' / 'tsunami-deps'
if DEPENDENCIES.is_dir():
    sys.path.insert(0, str(DEPENDENCIES))
import numpy as np

BASE = 'https://virtual-shizuoka.s3.ap-northeast-1.amazonaws.com/'
PREFIX = '2019/ALB/Grid/08/NF/'
BOUNDS = [45200.5, -110399.5, 62400.5, -92999.5]
SPACING = 20
BOUNDARY_URL = 'https://nlftp.mlit.go.jp/ksj/gml/data/N03/N03-2025/N03-20250101_22_GML.zip'


def acquire_boundary(directory):
    path = directory / 'N03-20250101_22_GML.zip'
    if not path.exists():
        with urllib.request.urlopen(BOUNDARY_URL, timeout=60) as response:
            raw = response.read(100_000_001)
        if len(raw) > 100_000_000:
            raise ValueError('Administrative boundary exceeds 100 MB budget')
        path.write_bytes(raw)
    raw = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = [n for n in archive.namelist() if n.endswith('.geojson')]
        if len(names) != 1 or archive.getinfo(names[0]).file_size > 100_000_000:
            raise ValueError('Expected one bounded administrative GeoJSON')
        data = json.loads(archive.read(names[0]))
    features = [f for f in data['features'] if f['properties'].get('N03_007') == '22205']
    if not features:
        raise ValueError('Atami municipality is absent from boundary data')
    (directory / 'atami-boundary.geojson').write_text(json.dumps({'type': 'FeatureCollection', 'features': features}, ensure_ascii=False), encoding='utf8')
    record = {'url': BOUNDARY_URL, 'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw),
              'date': '2025-01-01', 'source': 'MLIT National Land Numerical Information N03',
              'crs': 'JGD2011 geographic EPSG:6668'}
    (directory / 'boundary-source.json').write_text(json.dumps(record, indent=2), encoding='utf8')


def tile_bounds(name):
    match = re.fullmatch(r'08NF(\d)(\d)(\d)(\d)\.zip', name)
    if not match:
        raise ValueError('Unexpected tile name: ' + name)
    row, col, subrow, subcol = map(int, match.groups())
    west = 40000.5 + col * 4000 + subcol * 400
    north = -90000.5 - row * 3000 - subrow * 300
    return [west, north - 299, west + 399, north]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--reuse-root', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    listing_url = BASE + '?list-type=2&prefix=' + PREFIX + '&max-keys=1000'
    with urllib.request.urlopen(listing_url, timeout=40) as response:
        listing = response.read(2_000_000)
    root = ET.fromstring(listing)
    ns = {'s': 'http://s3.amazonaws.com/doc/2006-03-01/'}
    if root.findtext('s:IsTruncated', namespaces=ns) != 'false':
        raise ValueError('Incomplete catalog; pagination must be implemented')
    (args.output / 'source-catalog.xml').write_bytes(listing)
    entries = []
    for element in root.findall('s:Contents', ns):
        key = element.findtext('s:Key', namespaces=ns)
        b = tile_bounds(Path(key).name)
        if b[2] >= BOUNDS[0] and b[0] <= BOUNDS[2] and b[3] >= BOUNDS[1] and b[1] <= BOUNDS[3]:
            entries.append({'key': key, 'bytes': int(element.findtext('s:Size', namespaces=ns)), 'bounds_m': b})
    total = sum(e['bytes'] for e in entries)
    if total > 500_000_000 or shutil.disk_usage(args.output).free < total * 3:
        raise ValueError('Acquisition exceeds 500 MB budget or available disk margin')
    existing = {p.name: p for p in args.reuse_root.rglob('08NF*.zip')}
    print(json.dumps({'selected_tiles': len(entries), 'catalog_bytes': total, 'bounds_m': BOUNDS}), flush=True)
    started = time.monotonic()

    def acquire(entry):
        name = Path(entry['key']).name
        path = existing.get(name, args.output / name)
        if not path.exists():
            with urllib.request.urlopen(BASE + entry['key'], timeout=60) as response:
                raw = response.read(50_000_001)
            if len(raw) != entry['bytes'] or len(raw) > 50_000_000:
                raise ValueError('Unexpected object length: ' + name)
            path.write_bytes(raw)
        raw = path.read_bytes()
        if len(raw) != entry['bytes']:
            raise ValueError('Cached source does not match current catalog size: ' + name)
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            members = archive.infolist()
            if len(members) != 1 or not members[0].filename.lower().endswith('.txt') or members[0].file_size > 100_000_000:
                raise ValueError('Unexpected ZIP content')
            xyz = np.loadtxt(archive.open(members[0]), ndmin=2)
        if xyz.shape[1] != 3 or not np.isfinite(xyz).all():
            raise ValueError('Invalid XYZ data')
        b = entry['bounds_m']
        if np.any(xyz[:, :2].min(axis=0) < np.array(b[:2]) - 1e-7) or np.any(xyz[:, :2].max(axis=0) > np.array(b[2:]) + 1e-7):
            raise ValueError('Tile index coordinate mapping mismatch: ' + name)
        ij = (xyz[:, :2] - BOUNDS[:2]) / SPACING
        keep = (np.abs(ij - np.rint(ij)) < 1e-7).all(axis=1)
        samples = xyz[keep]
        np.savez_compressed(args.output / (name[:-4] + '-20m.npz'), xyz=samples)
        record = {**entry, 'path': str(path.resolve()), 'url': BASE + entry['key'],
                  'sha256': hashlib.sha256(raw).hexdigest(), 'sample_count': len(xyz),
                  'sampled_20m_count': len(samples), 'xyz_min': xyz.min(axis=0).tolist(),
                  'xyz_max': xyz.max(axis=0).tolist(), 'reused': name in existing}
        return record

    records = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for record in pool.map(acquire, entries):
            records.append(record)
            if len(records) % 25 == 0:
                print(json.dumps({'completed': len(records), 'total': len(entries), 'elapsed_s': time.monotonic() - started}), flush=True)
    evidence = {'schema': 'pland-citywide-terrain-acquisition-1', 'catalog_url': listing_url,
                'catalog_sha256': hashlib.sha256(listing).hexdigest(), 'bounds_m': BOUNDS,
                'crs': 'EPSG:6676', 'spacing_m': SPACING, 'elapsed_s': time.monotonic() - started,
                'downloaded_bytes': sum(r['bytes'] for r in records if not r['reused']),
                'all_source_bytes': total, 'missing_sample_fill': False,
                'purpose': 'Coverage audit only; no tsunami result or municipal-boundary proof', 'sources': records}
    (args.output / 'acquisition.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    acquire_boundary(args.output)
    print(json.dumps({k: v for k, v in evidence.items() if k != 'sources'}), flush=True)


if __name__ == '__main__':
    main()
