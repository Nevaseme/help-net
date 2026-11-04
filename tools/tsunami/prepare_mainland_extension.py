"""Prepare a fixed measured mainland extension; keep review pending.

The citywide coverage audit establishes that this does not cover all Atami.
The reporting bounds below are fixed before wave results are examined.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path

from tools.tsunami.terrain import load_xyz_tiles
from tools.tsunami.build_browser_domain import build_domain, pair_domains

DOMAIN_BOUNDS = {
    'large': [51140.5, -101499.5, 53180.5, -98139.5],
    'small': [51140.5, -101339.5, 53180.5, -98299.5],
}
REPORTING_BOUNDS = [51220.5, -101119.5, 53000.5, -98999.5]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('acquisition', type=Path)
    parser.add_argument('base_scenario', type=Path)
    parser.add_argument('control', type=Path)
    parser.add_argument('new_output', type=Path)
    parser.add_argument('--candidate', choices=['largest-area', 'original-east-buffer'], default='largest-area')
    args = parser.parse_args()
    acquisition = json.loads(args.acquisition.read_text(encoding='utf8'))
    base = json.loads(args.base_scenario.read_text(encoding='utf8'))
    control = json.loads(args.control.read_text(encoding='utf8'))
    if control['reference_point_m'] != [52660.5, -100209.5] or control['tolerance_m'] != .005:
        raise ValueError('Preserve the original coastal reference and tight calibration tolerance')
    args.new_output.mkdir(parents=True, exist_ok=False)
    packs = []
    bounds_by_name = DOMAIN_BOUNDS if args.candidate == 'largest-area' else {
        'large': [51140.5, -101539.5, 53120.5, -98139.5],
        'small': [51140.5, -101379.5, 53120.5, -98299.5],
    }
    for name, bounds in bounds_by_name.items():
        sources = [r for r in acquisition['sources'] if r['xyz_min'][0] <= bounds[2]
                   and r['xyz_max'][0] >= bounds[0] and r['xyz_min'][1] <= bounds[3]
                   and r['xyz_max'][1] >= bounds[1]]
        # Re-read the raw ZIPs. Cached coverage arrays are never model input.
        terrain = load_xyz_tiles(sources, bounds, 20)
        for source in terrain['sources']:
            recorded = next(r for r in sources if r['url'] == source['url'])
            if source['sha256'] != recorded['sha256']:
                raise ValueError('Source changed after acquisition audit')
        scenario = copy.deepcopy(base)
        scenario['id'] = 'mainland-extension-' + name
        scenario['grid'].update(nx=terrain['nx'], ny=terrain['ny'], origin_m=bounds[:2],
                                width_m=terrain['width_m'], height_m=terrain['height_m'])
        for key in ['source_sha256', 'sources', 'sampling', 'sampling_spacing_m']:
            scenario['terrain'][key] = terrain[key]
        scenario['terrain']['bed_m'] = terrain['bed_m'].tolist()
        scenario['assumptions'] = ['Diagnostic mainland extension; original coastal gauge, TP0, 600s raised-cosine pulse, 900s run, n0.03, 20m grid preserved. Full-city coverage not achieved.']
        pack = build_domain(scenario, control)
        packs.append(pack)
        for filename, data in [(name+'-scenario.json', scenario), (name+'.json', pack)]:
            (args.new_output/filename).write_text(json.dumps(data, separators=(',', ':'), allow_nan=False), encoding='utf8')
    pair = pair_domains(*packs, REPORTING_BOUNDS)
    (args.new_output/'pair-pending.json').write_text(json.dumps(pair, separators=(',', ':'), allow_nan=False), encoding='utf8')
    evidence = {'source_acquisition_sha256': hashlib.sha256(args.acquisition.read_bytes()).hexdigest(),
                'pair_id': pair['id'], 'status': 'pending', 'reporting_bounds_m': REPORTING_BOUNDS,
                'allcity': False, 'candidate': args.candidate}
    (args.new_output/'preparation.json').write_text(json.dumps(evidence, indent=2), encoding='utf8')
    print(json.dumps(evidence))


if __name__ == '__main__':
    main()
