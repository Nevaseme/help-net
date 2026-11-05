"""Offline ANUGA scenario adapter; never a live tsunami forecast.

Run: python -m tools.tsunami.engine scenario.json new-output-directory
Optional isolated dependencies: .implementation/tsunami-deps (see requirements.txt).

Input pland-tsunami-scenario-1 uses metre Cartesian coordinates. Terrain samples
are node values, rows south-to-north, columns west-to-east, shape (ny+1,nx+1).
Initial fields may be explicit scalars or same-shape arrays. A boundary time
series has [seconds, absolute stage metres, x momentum m2/s, y momentum m2/s].
Every boundary and source must be supplied; no real-city wave or sea floor is
invented. The adapter interpolates samples onto ANUGA's triangular cross mesh.

This runs depth-averaged nonlinear shallow-water dynamics with wet/dry fronts,
ANUGA DE1 fluxes and Manning friction. It does not resolve 3D breaking, sediment,
debris, building collapse or frequency dispersion. DEM/roughness/source error
and mesh convergence must be assessed separately for each intended scenario.
Analytical software checks do not validate any real coastal hazard estimate.
Source: https://anuga.readthedocs.io/en/stable/background.html
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
DEPENDENCIES = ROOT / ".implementation" / "tsunami-deps"
if DEPENDENCIES.is_dir():
    sys.path.insert(0, str(DEPENDENCIES))
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".implementation" / "tsunami-mpl"))

import numpy as np

SCHEMA = "pland-tsunami-scenario-1"
SOLVER_VERSION = "3.3.10"
MAX_TRIANGLES = 250_000
MAX_VALUES = 12_000_000
LIMITATIONS = [
    "Experimental scenario output; not a warning, evacuation instruction or validated coastal prediction.",
    "Depth-averaged nondispersive shallow-water model; no resolved 3D breaking, debris or structural failure.",
    "Results depend on terrain/bathymetry, vertical datum, source, tide, roughness and mesh resolution.",
    "Depth and arrival maxima are monitored at solver steps; published animation frames are sampled separately.",
    "A precomputed bundle covers only its declared scenario; it cannot predict an arbitrary new earthquake.",
]


def _number(value, name, minimum=None, maximum=None):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(f"{name} must be finite")
    if minimum is not None and value < minimum or maximum is not None and value > maximum:
        raise ValueError(f"{name} outside supported bounds")
    return float(value)


def _text(value, name):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{name} is required")
    return value


def _field(value, shape, name):
    try:
        field = np.asarray(value, dtype=float)
    except (ValueError, TypeError) as exc:
        raise ValueError(f"{name} must be numeric") from exc
    if field.ndim == 0: field = np.full(shape, float(field))
    if field.shape != shape or not np.isfinite(field).all():
        raise ValueError(f"{name} must have shape {shape} and contain finite values")
    return field


def validate_scenario(source):
    """Validate before loading the solver or creating files; return a defensive copy."""
    try:
        s = copy.deepcopy(source)
        if s["schema"] != SCHEMA or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", s["id"]):
            raise ValueError("unsupported schema or scenario id")
        _text(s["title"], "title"); _text(s["forcing_source"], "forcing_source")
        if s["kind"] not in ("benchmark", "site-scenario"): raise ValueError("unsupported scenario kind")
        grid = s["grid"]
        for key in ("nx", "ny"):
            if type(grid[key]) is not int or grid[key] < 1: raise ValueError("positive integer grid dimensions required")
        if 4*grid["nx"]*grid["ny"] > MAX_TRIANGLES: raise ValueError("mesh exceeds offline experiment limit")
        for key in ("width_m", "height_m"): _number(grid[key], key, 0.001, 100_000)
        if len(grid["origin_m"]) != 2: raise ValueError("origin needs two metre coordinates")
        for coordinate in grid["origin_m"]: _number(coordinate, "origin")
        _text(grid["crs"], "crs")
        terrain = s["terrain"]
        if terrain["kind"] not in ("synthetic", "measured"): raise ValueError("terrain kind required")
        _text(terrain["source"], "terrain source"); _text(terrain["vertical_datum"], "vertical datum")
        if terrain["kind"] == "measured":
            if not re.fullmatch(r"[0-9a-f]{64}", terrain["source_sha256"]): raise ValueError("measured terrain requires source hash")
        if s["kind"] == "site-scenario":
            if terrain["kind"] != "measured" or grid["crs"] == "LOCAL_METRES":
                raise ValueError("site scenario requires measured terrain and projected CRS")
            _text(s["source_reference"], "documented wave source reference")
        if grid["crs"] != "LOCAL_METRES":
            from pyproj import CRS
            crs = CRS.from_user_input(grid["crs"])
            if not crs.is_projected or any(axis.unit_conversion_factor != 1 for axis in crs.axis_info[:2]):
                raise ValueError("grid CRS must be projected in metres")
        shape = (grid["ny"]+1, grid["nx"]+1)
        bed_nodes = _field(terrain["bed_m"], shape, "bed_m")
        for key in ("stage_m", "xmomentum_m2_s", "ymomentum_m2_s"):
            _field(s["initial"][key], shape, key)
        duration = _number(s["duration_s"], "duration_s", 0.001, 21_600)
        interval = _number(s["output_interval_s"], "output_interval_s", 0.001, duration)
        frame_count = math.ceil(duration/interval)+1
        if frame_count > 241 or frame_count*4*grid["nx"]*grid["ny"] > MAX_VALUES:
            raise ValueError("output exceeds offline experiment limit")
        _number(s["manning_n"], "manning_n", 0, 0.2)
        _number(s["arrival_depth_m"], "arrival_depth_m", 0.001, 10)
        if set(s["boundaries"]) != {"left", "right", "top", "bottom"}: raise ValueError("all four boundaries required")
        for side, boundary in s["boundaries"].items():
            if boundary["type"] not in ("reflective", "transmissive", "time_series", "flather_stage", "open_still_water"):
                raise ValueError("unsupported boundary")
            if boundary["type"] == "open_still_water": _number(boundary["baseline_m"], "boundary baseline")
            if boundary["type"] in ("time_series", "flather_stage"):
                samples = np.asarray(boundary["samples"], dtype=float)
                columns = 2 if boundary["type"] == "flather_stage" else 4
                if samples.ndim != 2 or samples.shape[1] != columns or samples.shape[0] < 2 or not np.isfinite(samples).all():
                    raise ValueError("boundary samples must be finite time and conserved-value rows")
                if samples[0,0] != 0 or samples[-1,0] < duration or np.any(np.diff(samples[:,0]) <= 0):
                    raise ValueError("boundary times must increase strictly and cover the run")
                if boundary["type"] == "flather_stage":
                    baseline = _number(boundary["baseline_m"], "boundary baseline")
                    if boundary.get("dry_edges") not in (None, "reflective", "transmissive"):
                        raise ValueError("mixed Flather dry edges must explicitly be reflective or transmissive")
                    edge = {"left": bed_nodes[:,0], "right": bed_nodes[:,-1], "bottom": bed_nodes[0,:], "top": bed_nodes[-1,:]}[side]
                    if boundary.get("dry_edges") is None and np.any(edge >= baseline-0.001):
                        raise ValueError("Flather inlet must be initially wet at every node")
        return s
    except (KeyError, TypeError, IndexError, OverflowError) as exc:
        raise ValueError(f"incomplete scenario: {exc}") from exc


def simulate(source, progress=None):
    """Run a bounded, real ANUGA simulation. No files or external services needed."""
    s = validate_scenario(source)
    try:
        import anuga
        import anuga.shallow_water.boundaries as anuga_boundaries
        import scipy
        from anuga.operators.base_operator import Operator
        from scipy.interpolate import RegularGridInterpolator
    except ImportError as exc:
        raise RuntimeError("Install tools/tsunami/requirements.txt into .implementation/tsunami-deps") from exc
    if anuga.__version__ != SOLVER_VERSION:
        raise RuntimeError(f"This adapter is pinned to ANUGA {SOLVER_VERSION}; revalidate before changing solver")
    grid = s["grid"]; nx, ny = grid["nx"], grid["ny"]
    domain = anuga.rectangular_cross_domain(nx, ny, len1=grid["width_m"], len2=grid["height_m"])
    domain.set_name(s["id"]); domain.set_store(False)
    domain.set_flow_algorithm("DE1"); domain.set_cfl(0.5)
    domain.g = 9.81
    # This pinned ANUGA release's Flather implementation reads module gravity.
    # Keep it identical to the domain's gravity; all adapter runs use this constant.
    anuga_boundaries.gravity = domain.g
    domain.set_omp_num_threads(1, verbose=False)
    # Internal coordinates are local metres; the published mesh carries the explicit origin/CRS.
    xs = np.linspace(0, grid["width_m"], nx+1); ys = np.linspace(0, grid["height_m"], ny+1)
    shape = (ny+1, nx+1)
    def interpolator(values):
        f = RegularGridInterpolator((ys, xs), _field(values, shape, "field"), bounds_error=True)
        return lambda x,y: f(np.column_stack((y,x)))
    domain.set_quantity("elevation", interpolator(s["terrain"]["bed_m"]))
    domain.set_quantity("friction", s["manning_n"])
    for field, key in (("stage", "stage_m"), ("xmomentum", "xmomentum_m2_s"), ("ymomentum", "ymomentum_m2_s")):
        domain.set_quantity(field, interpolator(s["initial"][key]))
    # Set negative initial depths to dry ground without inserting water into dry land.
    bed = domain.quantities["elevation"].centroid_values
    stage = domain.quantities["stage"].centroid_values
    domain.set_quantity("stage", np.maximum(stage, bed), location="centroids")
    dry = stage <= bed
    for key in ("xmomentum", "ymomentum"):
        domain.quantities[key].centroid_values[dry] = 0
    boundary_objects = {}
    class ScalarFlatherBoundary(anuga_boundaries.Flather_external_stage_zero_velocity_boundary):
        # 3.3.10's vector evaluate_segment wrongly selects its dry branch whenever
        # external stage > bed, even in wet water. The native scalar evaluate
        # implements the documented characteristic construction. Dispatch it via
        # the generic scalar adapter; do not reproduce or alter its flux formula.
        evaluate_segment = anuga_boundaries.Boundary.evaluate_segment

    class OpenStillWaterBoundary(anuga_boundaries.Boundary):
        """Dispatch native boundaries; no custom flux or stage calculation.

        Initially wet sea edges admit zero external-stage anomaly and outgoing
        characteristics. Initially dry cut edges use the explicitly selected
        native reflection or extrapolation. Both are finite-domain assumptions.
        """
        def __init__(self, baseline, function=None, dry_edges="reflective"):
            super().__init__()
            self.open = ScalarFlatherBoundary(domain, function=function or (lambda t: baseline))
            self.closed = (anuga.Transmissive_boundary(domain) if dry_edges == "transmissive"
                           else anuga.Reflective_boundary(domain))
            self.wet = (domain.quantities["elevation"].edge_values < baseline-0.001) & (bed[:,None] < baseline-0.001)
        def evaluate(self, vol_id, edge_id):
            return (self.open if self.wet[vol_id,edge_id] else self.closed).evaluate(vol_id,edge_id)
        def evaluate_segment(self, domain, segment_edges):
            # Native vector dry-edge dispatch retains its native semantics;
            # only Flather requires the pinned scalar workaround above.
            ids=np.asarray(segment_edges,dtype=int)
            opened=self.wet[domain.boundary_cells[ids],domain.boundary_edges[ids]]
            if opened.any(): self.open.evaluate_segment(domain,ids[opened])
            if (~opened).any(): self.closed.evaluate_segment(domain,ids[~opened])
    for side, specification in s["boundaries"].items():
        if specification["type"] == "reflective": boundary_objects[side] = anuga.Reflective_boundary(domain)
        elif specification["type"] == "transmissive": boundary_objects[side] = anuga.Transmissive_boundary(domain)
        elif specification["type"] == "open_still_water": boundary_objects[side] = OpenStillWaterBoundary(specification["baseline_m"])
        elif specification["type"] == "flather_stage":
            samples = np.asarray(specification["samples"], dtype=float)
            mixed = specification.get("dry_edges") in ("reflective", "transmissive")
            if not mixed and any(bed[cell] >= specification["baseline_m"]-0.001 for (cell,edge),tag in domain.boundary.items() if tag == side):
                raise ValueError("Flather inlet adjacent cells must be initially wet")
            function=lambda t, table=samples: float(np.interp(t, table[:,0], table[:,1]))
            boundary_objects[side] = (OpenStillWaterBoundary(specification["baseline_m"], function, specification["dry_edges"]) if mixed
                                      else ScalarFlatherBoundary(domain, function=function))
        else:
            samples = np.asarray(specification["samples"], dtype=float)
            def boundary_value(t, table=samples):
                return [np.interp(t, table[:,0], table[:,i]) for i in (1,2,3)]
            boundary_objects[side] = anuga.Time_boundary(domain, function=boundary_value)
    domain.set_boundary(boundary_objects)
    from tools.tsunami.boundary_observation import BoundaryObservation
    boundary_observation = BoundaryObservation(domain, s)
    update_boundary = domain.update_boundary
    def observed_update_boundary():
        update_boundary()
        boundary_observation.observe()
    domain.update_boundary = observed_update_boundary

    def state():
        depth = domain.quantities["stage"].centroid_values - bed
        if not np.isfinite(depth).all() or depth.min() < -1e-8:
            raise RuntimeError("invalid solver water depth")
        return np.maximum(depth, 0), domain.quantities["xmomentum"].centroid_values, domain.quantities["ymomentum"].centroid_values

    initial_depth, _, _ = state()
    initial_volume = float(np.dot(initial_depth, domain.areas))
    initially_dry = initial_depth < s["arrival_depth_m"]
    maximum = initial_depth.copy()
    arrival = np.where(initial_depth >= s["arrival_depth_m"], 0.0, np.nan)
    minimum = [float(initial_depth.min())]
    steps = [0]

    class Monitor(Operator):
        def __call__(self):
            depth, hu, hv = state()
            if not np.isfinite(hu).all() or not np.isfinite(hv).all(): raise RuntimeError("invalid solver momentum")
            np.maximum(maximum, depth, out=maximum)
            # DE1 uses RK2, which has already advanced the clock before fractional operators.
            at = min(s["duration_s"], domain.get_time())
            arrival[np.isnan(arrival) & (depth >= s["arrival_depth_m"])] = at
            minimum[0] = min(minimum[0], float(depth.min())); steps[0] += 1
            if progress is not None and steps[0] % 1000 == 0:
                progress({"time_s":float(at), "steps":steps[0], "timestep_s":float(domain.timestep)})
        def parallel_safe(self): return False

    Monitor(domain)
    frames = []
    for t in domain.evolve(yieldstep=s["output_interval_s"], finaltime=s["duration_s"]):
        depth, hu, hv = state()
        frames.append({"time_s": float(t), "depth_m": depth.copy(),
                       "xmomentum_m2_s": hu.copy(), "ymomentum_m2_s": hv.copy()})
        if progress is not None: progress({"time_s":float(t), "steps":steps[0], "timestep_s":float(domain.timestep)})
    final_volume = float(np.dot(frames[-1]["depth_m"], domain.areas))
    boundary_flux_integral = float(domain.get_boundary_flux_integral())
    mass_balance_error = final_volume-initial_volume-boundary_flux_integral
    origin = np.asarray(grid["origin_m"])
    return {"scenario": s, "solver": {"name": "ANUGA", "version": SOLVER_VERSION, "algorithm": "DE1", "cfl": 0.5,
            "gravity_m_s2": domain.g, "threads": 1, "minimum_allowed_depth_m": domain.minimum_allowed_height,
            "flather_evaluation": "native-scalar-characteristics; pinned-vector-dry-branch-bypassed",
            "runtime": {"python": sys.version.split()[0], "numpy": np.__version__, "scipy": scipy.__version__}},
            "mesh": {"nodes_m": domain.nodes.copy()+origin, "triangles": domain.triangles.copy(),
                     "centres_m": domain.get_centroid_coordinates()+origin, "areas_m2": domain.areas.copy(),
                     "bed_elevation_m": bed.copy()},
            "frames": frames, "max_depth_m": maximum, "arrival_time_s": arrival,
            "boundary_observations": boundary_observation.result(),
            "initially_dry": initially_dry, "diagnostics": {"initial_volume_m3": initial_volume,
            "final_volume_m3": final_volume, "volume_change_m3": final_volume-initial_volume,
            "boundary_flux_integral_m3": boundary_flux_integral,
            "mass_balance_error_m3": mass_balance_error,
            "relative_mass_balance_error":abs(mass_balance_error)/max(initial_volume,final_volume,abs(boundary_flux_integral),1),
            "minimum_depth_m": minimum[0], "steps": steps[0]}}


def _json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False), encoding="utf-8")
    return _file_record(path)


def _file_record(path):
    return {"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}


def write_bundle(result, destination):
    """Publish a new directory with manifest last. JSON files are client-readable.

    mesh.json holds projected nodes, triangle node indices and cell centres. Frame
    arrays and maxima have one value per triangle in this immutable mesh order.
    JSON arrival null means threshold never reached, not an arrival time of zero.
    summary.npz preserves unrounded archival data; JSON depths are rounded to 1 um.
    """
    target = Path(destination)
    if target.exists(): raise FileExistsError("output directory must not exist")
    target.mkdir(parents=True)
    scenario = result["scenario"]
    scenario_file = _json(target/"scenario.json", scenario)
    mesh_data = {key: values.tolist() for key, values in result["mesh"].items()}
    mesh_data["crs"] = scenario["grid"]["crs"]
    if mesh_data["crs"] != "LOCAL_METRES":
        from pyproj import Transformer
        transformer = Transformer.from_crs(mesh_data["crs"], "EPSG:4326", always_xy=True)
        lon, lat = transformer.transform(*result["mesh"]["nodes_m"].T)
        mesh_data["nodes_lonlat"] = np.column_stack((lon,lat)).tolist()
    mesh_file = _json(target/"mesh.json", mesh_data)
    summary = {"max_depth_m": result["max_depth_m"].round(6).tolist(),
               "arrival_time_s": [None if np.isnan(t) else float(t) for t in result["arrival_time_s"]],
               "initially_dry": result["initially_dry"].tolist()}
    summary_json = _json(target/"summary.json", summary)
    boundary_file = (_json(target/"boundary-observations.json", result["boundary_observations"])
                     if "boundary_observations" in result else None)
    np.savez_compressed(target/"summary.npz", max_depth_m=result["max_depth_m"],
                        arrival_time_s=result["arrival_time_s"], initially_dry=result["initially_dry"])
    frame_files = []
    for i, frame in enumerate(result["frames"]):
        data = {key: value.round(6).tolist() if isinstance(value,np.ndarray) else value for key,value in frame.items()}
        frame_files.append({**_json(target/f"frame-{i:04d}.json", data), "time_s": frame["time_s"]})
    manifest = {"schema": "pland-tsunami-result-1", "id": scenario["id"], "title": scenario["title"],
                "kind": scenario["kind"], "solver": result["solver"], "scenario": scenario_file,
                "mesh": mesh_file, "summary": _file_record(target/"summary.npz"), "summary_json": summary_json,
                "frames": frame_files, "diagnostics": result["diagnostics"],
                "units": {"depth": "m", "momentum": "m2/s", "time": "s", "coordinates": "m"},
                "arrival_depth_m": scenario["arrival_depth_m"],
                "validation": {"status": "experimental-unvalidated", "real_world_validated": False},
                "limitations": LIMITATIONS}
    if boundary_file is not None: manifest["boundary_observations"] = boundary_file
    _json(target/"manifest.json", manifest)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scenario", type=Path); parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = simulate(json.loads(args.scenario.read_text(encoding="utf-8")))
    manifest = write_bundle(result, args.output)
    print(json.dumps({"manifest": str(args.output/"manifest.json"), "solver": manifest["solver"],
                      "diagnostics": manifest["diagnostics"]}, ensure_ascii=False))


if __name__ == "__main__": main()
