"""Reproducible independent ANUGA/browser comparison on a synthetic planar beach.

Run: python -m tools.tsunami.compare_browser_solver
Writes .implementation/tsunami-browser-comparison.json. Not a site validation.
Both models receive the same continuous bed/wave/velocity functions, sampled on
their own meshes. ANUGA uses DE1 triangular elements; JS uses Cartesian cells.
"""
import json
from pathlib import Path
import subprocess
import time
from tools.tsunami.engine import simulate, np, ROOT


def run_case(nx):
    width, height, duration = 600., 20., 65.
    dx = width / nx
    ny = max(2, round(height / dx))
    def fields(x):
        bed = np.maximum(-5., .025 * (x - 400.))
        anomaly = .5 * np.exp(-((x - 150.) / 40.) ** 2)
        stage = np.where(bed < 0, anomaly, 0.)
        depth = np.maximum(0., stage - bed)
        qx = np.where(bed < 0., anomaly * np.sqrt(9.81 * depth), 0.)
        return bed, stage, qx
    x = np.linspace(0, width, nx + 1)
    nodes = [np.tile(f, (ny + 1, 1)).tolist() for f in fields(x)]
    s = {"schema": "pland-tsunami-scenario-1", "id": f"browser-beach-{nx}", "title": "Synthetic planar beach runup", "kind": "benchmark",
        "forcing_source": "Initial 0.5m Gaussian rightgoing shallow water pulse; synthetic",
        "grid": {"nx": nx, "ny": ny, "width_m": width, "height_m": height, "origin_m": [0, 0], "crs": "LOCAL_METRES"},
        "terrain": {"kind": "synthetic", "source": "Analytic planar beach z=max(-5,0.025*(x-400))", "vertical_datum": "synthetic zero", "bed_m": nodes[0]},
        "initial": {"stage_m": nodes[1], "xmomentum_m2_s": nodes[2], "ymomentum_m2_s": 0},
        "boundaries": {s: {"type": "reflective"} for s in ("left", "right", "top", "bottom")},
        "duration_s": duration, "output_interval_s": duration, "manning_n": 0, "arrival_depth_m": .01}
    begin = time.perf_counter(); cpu = time.process_time(); a = simulate(s)
    anuga_timing = {"wall_s": time.perf_counter() - begin, "cpu_s": time.process_time() - cpu}
    cell = [np.tile(f, (ny, 1)).ravel().tolist() for f in fields((np.arange(nx) + .5) * dx)]
    config = {"nx": nx, "ny": ny, "dx": dx, "dy": height / ny, "bed": cell[0], "initial_stage_m": cell[1], "initial_xmomentum": cell[2], "manning_n": 0, "duration_s": duration}
    p = subprocess.run(["node", "tools/tsunami/run_browser_solver.mjs"], cwd=ROOT, input=json.dumps(config), text=True, capture_output=True, check=True)
    if not p.stdout.strip(): raise RuntimeError(f"Node emitted no result: {p.stderr!r}")
    j = json.loads(p.stdout)
    centres = a["mesh"]["centres_m"][:, 0]
    bins = np.minimum(nx - 1, (centres / dx).astype(int))
    weights = a["mesh"]["areas_m2"]
    anuga_depth = np.bincount(bins, weights=weights * a["frames"][-1]["depth_m"], minlength=nx) / np.bincount(bins, weights=weights, minlength=nx)
    js_depth = np.asarray(j["depth"]).reshape(ny, nx).mean(axis=0)
    js_maximum = np.asarray(j["maxDepth"]).reshape(ny, nx).max(axis=0)
    z = np.asarray(cell[0]).reshape(ny, nx)[0]
    def wet_runup(bed, maximum, threshold=.01):
        wet = (bed >= 0) & (maximum >= threshold)
        return float(bed[wet].max()) if wet.any() else 0.
    return {"nx": nx, "ny": ny, "dx_m": dx,
        "depth_l1_difference_m": float(np.mean(np.abs(js_depth - anuga_depth))),
        "depth_linf_difference_m": float(np.max(np.abs(js_depth - anuga_depth))),
        "js_runup_elevation_m": wet_runup(z, js_maximum),
        "anuga_runup_elevation_m": wet_runup(a["mesh"]["bed_elevation_m"], a["max_depth_m"]),
        "runup_threshold_sensitivity": [{"depth_threshold_m": threshold, "js_elevation_m": wet_runup(z, js_maximum, threshold),
            "anuga_elevation_m": wet_runup(a["mesh"]["bed_elevation_m"], a["max_depth_m"], threshold)} for threshold in [.01, .05, .1]],
        "js_relative_mass_residual": j["diagnostics"]["relative_mass_residual"],
        "anuga_relative_mass_change": a["diagnostics"]["volume_change_m3"] / a["diagnostics"]["initial_volume_m3"],
        "js_steps": j["diagnostics"]["steps"], "anuga_steps": a["diagnostics"]["steps"],
        "js_timing": {"wall_s": j["diagnostics"]["elapsed_wall_ms"] / 1000, "cpu_s": j["cpu_s"]}, "anuga_timing": anuga_timing,
        "js_final_profile": js_depth.tolist(), "anuga_final_profile": anuga_depth.tolist()}


def run_2d_case(nx):
    width, duration = 200., 10.
    dx = width / nx
    def fields(x, y):
        z = -5 + .01 * x + .005 * y
        eta = .1 * np.exp(-((x - 100.) ** 2 + (y - 100.) ** 2) / 20. ** 2)
        return z, eta
    xx, yy = np.meshgrid(np.linspace(0, width, nx + 1), np.linspace(0, width, nx + 1))
    z, eta = fields(xx, yy)
    scenario = {"schema": "pland-tsunami-scenario-1", "id": f"browser-2d-{nx}", "title": "Synthetic 2D wave on oblique slope", "kind": "benchmark",
        "forcing_source": "Initial circular Gaussian stage anomaly on a cross-slope plane",
        "grid": {"nx": nx, "ny": nx, "width_m": width, "height_m": width, "origin_m": [0, 0], "crs": "LOCAL_METRES"},
        "terrain": {"kind": "synthetic", "source": "Analytic plane z=-5+.01*x+.005*y", "vertical_datum": "synthetic zero", "bed_m": z.tolist()},
        "initial": {"stage_m": eta.tolist(), "xmomentum_m2_s": 0, "ymomentum_m2_s": 0},
        "boundaries": {s: {"type": "reflective"} for s in ("left", "right", "top", "bottom")},
        "duration_s": duration, "output_interval_s": duration, "manning_n": 0, "arrival_depth_m": .01}
    a = simulate(scenario)
    xx, yy = np.meshgrid((np.arange(nx) + .5) * dx, (np.arange(nx) + .5) * dx)
    z, eta = fields(xx, yy)
    config = {"nx": nx, "ny": nx, "dx": dx, "dy": dx, "bed": z.ravel().tolist(), "initial_stage_m": eta.ravel().tolist(), "manning_n": 0, "duration_s": duration}
    p = subprocess.run(["node", "tools/tsunami/run_browser_solver.mjs"], cwd=ROOT, input=json.dumps(config), text=True, capture_output=True, check=True)
    j = json.loads(p.stdout)
    centres = a["mesh"]["centres_m"]
    bins = np.minimum(nx - 1, (centres[:, 1] / dx).astype(int)) * nx + np.minimum(nx - 1, (centres[:, 0] / dx).astype(int))
    weights = a["mesh"]["areas_m2"]
    ad = np.bincount(bins, weights=weights * a["frames"][-1]["depth_m"], minlength=nx * nx) / np.bincount(bins, weights=weights, minlength=nx * nx)
    diff = np.asarray(j["depth"]) - ad
    return {"nx": nx, "ny": nx, "dx_m": dx, "depth_l1_difference_m": float(np.abs(diff).mean()), "depth_linf_difference_m": float(np.abs(diff).max()),
        "js_relative_mass_residual": j["diagnostics"]["relative_mass_residual"], "anuga_relative_mass_change": a["diagnostics"]["volume_change_m3"] / a["diagnostics"]["initial_volume_m3"]}


def main():
    results = []
    for nx in [60, 120, 240]:
        r = run_case(nx); results.append(r)
        print(json.dumps({k: v for k, v in r.items() if not k.endswith("profile")}), flush=True)
    report = {"schema": "pland-browser-solver-comparison-1", "benchmark": "Gaussian initial rightgoing wave, planar beach, reflective boundaries, zero friction, 65 seconds",
        "real_world_validated": False, "limitations": ["Different Cartesian and triangular discretizations; shared continuous inputs, not identical degrees of freedom.", "Synthetic cross-solver agreement is not observed tsunami validation.", "Hydrostatic reconstruction can be inaccurate at steep slopes relative to cell depth; assess real-site grid convergence separately."], "results": results}
    report["two_dimensional_results"] = [run_2d_case(nx) for nx in [20, 40]]
    print(json.dumps(report["two_dimensional_results"]), flush=True)
    destination = ROOT / ".implementation" / "tsunami-browser-comparison.json"
    destination.write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__": main()
