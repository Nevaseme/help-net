"""Audit generated sidecars against packaged buildings and fixed source hashes."""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path

from build_kyoto_parcel_matches import ASSETS, DEFAULT, EXPECTED_HASH


def verify(output):
    folder = output / 'tiles'
    index = json.loads((folder / 'index.json').read_text(encoding='utf8'))
    assert index['source']['sha256'] == EXPECTED_HASH
    counts, demo_counts, seen, target_buildings = Counter(), Counter(), set(), []
    for chunk in index['chunks']:
        content = (folder / chunk['file']).read_bytes()
        assert len(content) == chunk['bytes']
        assert hashlib.sha256(content).hexdigest() == chunk['sha256']
        original = (ASSETS / chunk['file']).read_bytes()
        assert hashlib.sha256(original).hexdigest() == chunk['buildingSourceSha256']
        original_buildings = {b['id']: b for b in json.loads(gzip.decompress(original))['buildings']}
        original_ids = set(original_buildings)
        tile = json.loads(gzip.decompress(content))
        assert original_ids == set(tile['buildings'])
        assert tile['mesh'] == chunk['mesh']
        for key, result in tile['buildings'].items():
            assert key not in seen
            seen.add(key)
            counts['buildings'] += 1
            counts[result['status']] += 1
            counts['candidates'] += len(result['candidates'])
            west, south, east, north = original_buildings[key]['bbox']
            if east > 135.758 and west < 135.767 and north > 35.011 and south < 35.018:
                demo_counts['buildings'] += 1
                demo_counts[result['status']] += 1
                demo_counts['candidates'] += len(result['candidates'])
            if any(c['address'] == '京都府京都市左京区讃州寺町203' for c in result['candidates']):
                target_buildings.append({'plateauId': key, 'mesh': chunk['mesh'], **result})
            assert -0.000001 <= result.get('unionBuildingCoverage', 0) <= 1.000001
            assert (result['status'] == 'unmatched') == (len(result['candidates']) == 0)
            for candidate in result['candidates']:
                assert candidate['address'].startswith('京都府京都市')
                assert candidate['wardCode'].startswith('261')
                assert 135 < candidate['longitude'] < 137 and 34 < candidate['latitude'] < 36
                assert 0 < candidate['buildingCoverage'] <= 1.000001
                # A tiny building on a very large parcel can round to zero at 6 decimals.
                assert 0 <= candidate['parcelCoverage'] <= 1.000001
                assert candidate['overlapM2'] >= 0.1
                assert candidate['parcelId'].endswith(':' + str(candidate['sourceFeatureIndex']))
            if result['status'] == 'single-parcel':
                assert result['candidates'][0]['buildingCoverage'] >= 0.95
                assert result['candidates'][0]['numbered']
                assert not result['flags']
    assert dict(counts) == index['counts'], (counts, index['counts'])
    validation = json.loads((output / 'validation.json').read_text(encoding='utf8'))
    assert validation['counts'] == index['counts']
    assert len(validation['knownBuildings']) == 12
    assert all(row['exactAddressAmongCandidates'] for row in validation['knownBuildings'])
    top_disagreements = [row['name'] for row in validation['knownBuildings']
                         if row['candidates'][0]['address'] != row['verifiedAddress']]
    assert set(top_disagreements) == {'藤和シティスクエア烏丸丸太町', 'アスヴェル京都御所前Ⅱ'}
    assert validation['sanshuji203Parcels']
    audit = {'verifiedChunks': len(index['chunks']), 'counts': dict(counts), 'demoBboxCounts': dict(demo_counts),
                      'knownExactAddressMatches': sum(r['exactAddressAmongCandidates'] for r in validation['knownBuildings']),
                      'knownBuildings': len(validation['knownBuildings']),
                      'knownLargestParcelDisagreements': top_disagreements,
                      'sanshuji203Parcels': len(validation['sanshuji203Parcels']), 'sanshuji203Buildings': target_buildings}
    (output / 'audit.json').write_text(json.dumps(audit, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in audit.items() if k != 'sanshuji203Buildings'}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=DEFAULT)
    verify(parser.parse_args().output)
