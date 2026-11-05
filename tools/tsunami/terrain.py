"""Read bounded XYZ ZIP tiles without filling missing terrain or extrapolating.

This is a sampling/provenance utility, not a terrain suitability certificate.
It selects exact source samples at the declared spacing, without smoothing or
interpolation. Coarsening can lose seawalls, channels and other important features.
Numeric values in a published grid may themselves have been interpolated by its
producer. Verify acquisition coverage, nodata conventions, vertical datum, tide
and roughness independently before constructing a real coastal scenario.
"""
import hashlib
import io
import json
from pathlib import Path
import zipfile

from tools.tsunami.engine import MAX_TRIANGLES
import numpy as np


def load_xyz_tiles(sources, bounds_m, spacing_m):
    """Return node samples for explicit [west,south,east,north] in metre XY.

    Each source is {path: local ZIP, url: original source URL}. No network access.
    Archives must contain exactly one XYZ text file with finite columns X Y Z.
    Inputs with numeric nodata sentinels must be cleaned/rejected by their
    source-specific acquisition adapter first; a finite number is not proof of
    measurement. This reader rejects missing required nodes and conflicting data.
    """
    bounds = np.asarray(bounds_m, dtype=float)
    if (bounds.shape != (4,) or not np.isfinite(bounds).all() or
            not np.isfinite(spacing_m) or spacing_m <= 0 or not 1 <= len(sources) <= 100):
        raise ValueError("invalid bounded terrain request")
    intervals = (bounds[2:]-bounds[:2])/spacing_m
    if (intervals <= 0).any() or not np.allclose(intervals, np.rint(intervals), atol=1e-9, rtol=0):
        raise ValueError("bounds must contain whole grid intervals")
    nx, ny = np.rint(intervals).astype(int)
    if 4*int(nx)*int(ny) > MAX_TRIANGLES: raise ValueError("terrain exceeds mesh limit")
    records = []; indices = []; elevations = []
    for source in sources:
        path = Path(source["path"])
        if not isinstance(source["url"], str) or not source["url"].strip():
            raise ValueError("source URL required")
        if path.stat().st_size > 50_000_000: raise ValueError("archive exceeds bounded reader limit")
        raw = path.read_bytes()
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            members = archive.infolist()
            if len(members) != 1 or not members[0].filename.lower().endswith(".txt") or members[0].file_size > 100_000_000:
                raise ValueError("expected one bounded XYZ text file")
            xyz = np.loadtxt(archive.open(members[0]), ndmin=2)
        if xyz.shape[1] != 3 or not np.isfinite(xyz).all(): raise ValueError("invalid XYZ samples")
        cell = (xyz[:,:2]-bounds[:2])/spacing_m
        rounded = np.rint(cell)
        selected = ((np.abs(cell-rounded) < 1e-7).all(axis=1) &
                    (rounded[:,0] >= 0) & (rounded[:,0] <= nx) &
                    (rounded[:,1] >= 0) & (rounded[:,1] <= ny))
        ij = rounded[selected].astype(int)
        indices.append(ij[:,1]*(nx+1)+ij[:,0]); elevations.append(xyz[selected,2])
        records.append({"file": path.name, "url": source["url"], "sha256": hashlib.sha256(raw).hexdigest(),
                        "bytes": len(raw), "source_samples": len(xyz), "selected_samples": int(selected.sum())})
    flat_index = np.concatenate(indices); z = np.concatenate(elevations)
    order = np.argsort(flat_index); flat_index = flat_index[order]; z = z[order]
    if np.any((np.diff(flat_index) == 0) & (np.abs(np.diff(z)) > 1e-9)):
        raise ValueError("conflicting overlapping terrain samples")
    unique, first = np.unique(flat_index, return_index=True)
    expected = (int(nx)+1)*(int(ny)+1)
    if len(unique) != expected: raise ValueError(f"missing {expected-len(unique)} required terrain samples; no filling allowed")
    values = z[first].reshape(ny+1, nx+1)
    digest = hashlib.sha256(json.dumps(records, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return {"bed_m": values, "origin_m": bounds[:2].tolist(), "width_m": float(bounds[2]-bounds[0]),
            "height_m": float(bounds[3]-bounds[1]), "nx": int(nx), "ny": int(ny), "sampling_spacing_m": spacing_m,
            "source_sha256": digest, "sources": records,
            "sampling": "exact-source-node-selection; no interpolation or extrapolation"}
