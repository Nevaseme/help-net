"""Prepare bounded, town-indexed parcel search; never load the city into the UI.

Input is build_kyoto_parcel_matches.py's parcel-address-search.json.gzip.
The same index and compressed chunks are consumed by Android and the PWA.
"""
import argparse
from collections import defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import re
import unicodedata


def normalize(value):
    value = unicodedata.normalize('NFKC', value).lower()
    value = re.sub(r'[\s　]', '', value)
    value = re.sub('[‐‑–—−]', '-', value)
    value = re.sub(r'(\d)ー(?=\d)', r'\1-', value)
    digits = dict(zip('〇零一二三四五六七八九', [0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9]))
    def chome(match):
        s = match[1]
        if s.isascii() and s.isdigit():
            return s + '-'
        value, digit = 0, 0
        for c in s:
            if c == '十':
                value += (digit or 1) * 10
                digit = 0
            else:
                digit = digits.get(c, 0)
        return str(value + digit) + '-'
    value = re.sub(r'([〇零一二三四五六七八九十0-9]+)丁目', chome, value)
    value = re.sub(r'(\d)(?:番地の|番地|番)', r'\1-', value)
    return re.sub(r'(\d)号', r'\1', value).rstrip('-')


def write(path, body, compressed=False):
    raw = json.dumps(body, ensure_ascii=False, separators=(',', ':')).encode()
    data = gzip.compress(raw, mtime=0) if compressed else raw
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def build(source_path, output):
    with gzip.open(source_path, 'rt', encoding='utf8') as stream:
        data = json.load(stream)
    source = data['source']
    chunks, towns = defaultdict(list), {}
    for row in data['addresses']:
        number = row['parcelNumber']
        assert number and row['address'].endswith(number)
        town = normalize(row['address'][:-len(number)])
        assert town.startswith('京都府京都市')
        # 256 bounded files avoid both 700k row loads and thousands of downloads.
        chunk_id = hashlib.sha256(town.encode()).hexdigest()[:2]
        towns[town] = chunk_id
        chunks[chunk_id].append([
            row['parcelId'], row['address'], row['longitude'], row['latitude'],
            'parcel-area', {'matchLevel': 'parcel', 'source': source['id'],
                            'sourceVersion': source['version'], 'municipalityCode': '26100',
                            'wardCode': row['wardCode'], 'sourceFeatureIndex': row['sourceFeatureIndex']},
            normalize(row['address']),
        ])
    descriptors = []
    for chunk_id, rows in sorted(chunks.items()):
        file = f'chunks/{chunk_id}.json.gzip'
        descriptors.append({'id': chunk_id, 'file': file, 'count': len(rows),
                            **write(output / file, {'schemaVersion': 1, 'rows': rows}, True)})
    manifest = {'schemaVersion': 1, 'municipalityCode': '26100', 'source': source,
                'rowCount': sum(c['count'] for c in descriptors),
                'towns': sorted(towns.items()), 'chunks': descriptors}
    descriptor = write(output / 'index.json', manifest)
    print(json.dumps({'output': str(output), **descriptor, 'rowCount': manifest['rowCount'],
                      'townCount': len(towns), 'chunkCount': len(descriptors),
                      'maxChunkRows': max(c['count'] for c in descriptors),
                      'totalBytes': descriptor['bytes'] + sum(c['bytes'] for c in descriptors)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=Path('.implementation/kyoto-parcel-match/parcel-address-search.json.gzip'))
    parser.add_argument('--output', type=Path, default=Path('.implementation/kyoto-parcel-search'))
    args = parser.parse_args()
    build(args.input, args.output)
