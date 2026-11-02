"""Plot a checked immutable bundle for review; not a warning or terrain-only model."""
import argparse
import hashlib
import json
import math
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
from matplotlib.colors import Normalize
import numpy as np


def plot_bundle(directory, destination):
    directory = Path(directory)
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
    if manifest['schema'] != 'pland-tsunami-result-1':
        raise ValueError('unsupported result')
    def read(key, filename):
        record = manifest[key]
        data = (directory / filename).read_bytes()
        if record['file'] != filename or len(data) != record['bytes'] or hashlib.sha256(data).hexdigest() != record['sha256']:
            raise ValueError('bundle does not match manifest: ' + filename)
        return json.loads(data)
    scenario = read('scenario', 'scenario.json')
    mesh = read('mesh', 'mesh.json')
    summary = read('summary_json', 'summary.json')
    control = scenario['coastal_control']
    if abs(control['residual_m']) > control['tolerance_m']:
        raise ValueError('unmet coastal height target')
    nodes = np.asarray(mesh['nodes_m'])
    triangles = np.asarray(mesh['triangles'])
    bed = np.asarray(mesh['bed_elevation_m'])
    depth = np.asarray(summary['max_depth_m'])
    ground = np.asarray(summary['initially_dry'], dtype=bool) & (bed >= control['baseline_m'])
    wet = ground & (depth >= manifest['arrival_depth_m'])
    if not (len(triangles) == len(bed) == len(depth) == len(ground)):
        raise ValueError('mesh/summary ordering mismatch')
    origin = nodes.min(axis=0)
    polygons = (nodes - origin)[triangles]
    fig, ax = plt.subplots(figsize=(11, 8), layout='constrained')
    backdrop = PolyCollection(polygons, facecolors=np.where(ground[:, None], np.array([.93,.93,.90,1]), np.array([.83,.91,.95,1])), edgecolors='none', antialiaseds=False)
    ax.add_collection(backdrop)
    water = PolyCollection(polygons[wet], array=depth[wet], cmap='YlOrRd', norm=Normalize(0, max(1, math.ceil(float(depth[wet].max()) if wet.any() else 0))), edgecolors='none', antialiaseds=False)
    ax.add_collection(water)
    gauge = np.asarray(control['realised_reference_point_m']) - origin
    ax.scatter(*gauge, color='#155eef', marker='x', s=65)
    ax.annotate(f"Reference crest {control['achieved_height_m']:.3f} m", gauge, xytext=(-170, 20), textcoords='offset points', fontsize=10, color='#155eef')
    ax.set(xlim=(0, nodes[:,0].max()-origin[0]), ylim=(0, nodes[:,1].max()-origin[1]), aspect='equal', xlabel='East from model origin (m)', ylabel='North from model origin (m)')
    fig.colorbar(water, ax=ax, label='Maximum model inundation depth on initially exposed ground (m)', shrink=.75)
    ax.set_title(f"Atami conditional experiment / coastal target {control['target_height_m']:g} m\n{scenario['grid']['nx']} x {scenario['grid']['ny']} grid / {scenario['duration_s']:g} s simulation")
    fig.text(.02,.01,f"Experimental, not validated against tsunami observations. Outside this rectangle: unknown. Display threshold {manifest['arrival_depth_m']:g} m.", fontsize=9)
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(destination, dpi=160)
    plt.close(fig)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    parser.add_argument('output', type=Path)
    args=parser.parse_args()
    plot_bundle(args.bundle,args.output)
