"""Download official Kyoto mesh files and retain compact, lossless LOD0 records.

Raw CityGML is a local cache, not an Android asset. Each input checksum is retained.
Usage: python tools/building_analysis/fetch_kyoto.py --cache .implementation/kyoto
"""
import argparse
import concurrent.futures
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import urllib.request
from citygml import read_buildings


def fetch(item, cache):
    code = item['code']
    output = cache / (code + '.json.gz')
    if output.exists():
        with gzip.open(output, 'rt', encoding='utf-8') as stream:
            return json.load(stream)['source']
    raw = cache / (code + '.gml')
    # Preserve the previously downloaded official sample.
    sample = Path('.implementation/kyoto-' + code + '.gml')
    if not raw.exists() and sample.exists():
        shutil.copyfile(sample, raw)
    if not raw.exists():
        temporary = raw.with_suffix('.part')
        with urllib.request.urlopen(item['url'], timeout=180) as response, temporary.open('wb') as target:
            shutil.copyfileobj(response, target)
        temporary.replace(raw)
    digest = hashlib.file_digest(raw.open('rb'), 'sha256').hexdigest()
    rows = list(read_buildings(str(raw)))
    source = {**item, 'sha256': digest, 'bytes': raw.stat().st_size, 'buildings': len(rows)}
    payload = json.dumps({'source': source, 'buildings': rows}, ensure_ascii=False, separators=(',', ':')).encode()
    temporary = output.with_suffix('.part')
    temporary.write_bytes(gzip.compress(payload, mtime=0))
    temporary.replace(output)
    return source


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=3)
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    items = json.loads(Path(__file__).with_name('kyoto-sources.json').read_text(encoding='utf-8'))['files']['bldg']
    results, failures = [], []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(fetch, item, args.cache): item for item in items}
        for future in concurrent.futures.as_completed(futures):
            item = futures[future]
            try:
                result = future.result()
                results.append(result)
                print(f"{len(results)}/{len(items)} {item['code']} {result['buildings']} buildings", flush=True)
            except Exception as error:
                failures.append({'code': item['code'], 'error': str(error)})
                print(f"FAILED {item['code']}: {error}", flush=True)
    (args.cache / 'download-report.json').write_text(json.dumps({'sources': sorted(results,key=lambda x:x['code']), 'failures': failures}, indent=2), encoding='utf-8')
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
