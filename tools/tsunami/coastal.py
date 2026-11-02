"""Offline coastal-height-controlled experiments, independent of shaking intensity.

User height means the model crest rise above initial still water at a declared
nearshore reference point, NOT offshore forcing amplitude or land inundation depth.
ANUGA's native Flather boundary supplies an external stage pulse; bounded calibration
adjusts that forcing until the simulated reference crest matches the target.

The fixed source is a single positive raised-cosine pulse, with duration, direction,
tide, roughness and terrain declared by the experiment configuration. These are
conditional scenario assumptions, not an earthquake forecast. The same coastal
height with other durations/directions/tides can produce different inundation.
Calibration to a chosen height is not validation against observed tsunami data.

Definitions: https://www.jma.go.jp/jma/kishou/know/faq/faq26.html
Boundary: https://anuga.readthedocs.io/en/latest/_modules/anuga/shallow_water/boundaries.html
"""
import copy
import math
import time

from tools.tsunami.engine import simulate, validate_scenario
from tools.tsunami.terrain import load_xyz_tiles
import numpy as np


def with_xyz_crop(template, sources, bounds_m, spacing_m, *, crs, vertical_datum, datum_verified=False):
    """Attach an exact-source-node crop; no extrapolation or inferred datum conversion."""
    crop = load_xyz_tiles(sources, bounds_m, spacing_m)
    scenario = copy.deepcopy(template)
    scenario["grid"] = {key: crop[key] for key in ("nx", "ny", "width_m", "height_m", "origin_m")}
    scenario["grid"]["crs"] = crs
    scenario["kind"] = "site-scenario"
    scenario["terrain"] = {"kind": "measured", "source": "Declared XYZ source tiles", "sources": crop["sources"],
        "source_sha256": crop["source_sha256"], "vertical_datum": vertical_datum,
        "vertical_datum_status": "verified" if datum_verified else "unverified", "bed_m": crop["bed_m"].tolist(),
        "sampling": crop["sampling"], "sampling_spacing_m": spacing_m}
    return scenario


def pulse_samples(baseline, amplitude, delay, duration, finaltime):
    """65 explicitly sampled raised-cosine values plus still-water padding."""
    if not all(math.isfinite(x) for x in (baseline, amplitude, delay, duration, finaltime)) or amplitude < 0 or delay < 0 or duration <= 0 or finaltime < delay+duration:
        raise ValueError("pulse must be finite, nonnegative and fit the simulation duration")
    times = sorted(set([0.0, finaltime, *np.linspace(delay, delay+duration, 65).tolist()]))
    return [[float(t), float(baseline+amplitude*0.5*(1-math.cos(2*math.pi*(t-delay)/duration)))
             if delay < t < delay+duration else float(baseline)] for t in times]


def calibrate_coastal_height(source, control, on_run=None, on_progress=None):
    """Return one actual simulated result only if its reference crest meets tolerance.

    All numerical controls are explicit. The reference point is sampled at its
    nearest mesh centroid and the realised coordinate is recorded. Maxima come
    from every solver step, not the sparsely sampled animation. No extrapolation
    in target height or flood-mask interpolation is performed.
    """
    s = copy.deepcopy(source); c = copy.deepcopy(control)
    required = ("target_height_m", "baseline_m", "pulse_duration_s", "pulse_delay_s", "maximum_forcing_m", "tolerance_m")
    if any(key not in c or isinstance(c[key], bool) or not isinstance(c[key], (int,float)) or not math.isfinite(c[key]) for key in required):
        raise ValueError("finite explicit coastal controls required")
    target, baseline, maximum, tolerance = (c[k] for k in ("target_height_m", "baseline_m", "maximum_forcing_m", "tolerance_m"))
    if target < 0 or maximum <= 0 or tolerance <= 0 or tolerance > max(0.01,target/4): raise ValueError("invalid calibration bounds or tolerance")
    if type(c.get("max_iterations")) is not int or not 1 <= c["max_iterations"] <= 20: raise ValueError("bounded calibration iterations required")
    side = c.get("boundary")
    if side not in ("left", "right", "top", "bottom"): raise ValueError("explicit inlet boundary required")
    point = np.asarray(c.get("reference_point_m"), dtype=float)
    grid=s["grid"]; origin=np.asarray(grid["origin_m"]); extent=np.array([grid["width_m"],grid["height_m"]])
    if point.shape != (2,) or not np.isfinite(point).all() or np.any(point < origin) or np.any(point > origin+extent): raise ValueError("coastal reference point outside domain")
    if s["kind"] == "site-scenario" and s["terrain"].get("vertical_datum_status") != "verified":
        raise ValueError("verify the exact terrain vertical datum before real-site calibration")
    if not np.all(np.asarray(s["initial"]["stage_m"]) == baseline) or any(np.any(np.asarray(s["initial"][k]) != 0) for k in ("xmomentum_m2_s", "ymomentum_m2_s")):
        raise ValueError("coastal calibration requires initial still water at the declared baseline")
    for tag, boundary in s["boundaries"].items():
        if tag != side and boundary["type"] not in ("reflective", "transmissive", "open_still_water"):
            raise ValueError("calibration supports one forced inlet only")
        if tag != side and boundary["type"] == "open_still_water" and boundary["baseline_m"] != baseline:
            raise ValueError("open side baseline must match initial still water")
    runs=[]; gauge=None

    def run(amplitude):
        nonlocal gauge
        experiment=copy.deepcopy(s)
        experiment["boundaries"][side]={"type":"flather_stage", "baseline_m":baseline,
            "samples":pulse_samples(baseline, amplitude, c["pulse_delay_s"], c["pulse_duration_s"], s["duration_s"])}
        if s["boundaries"][side].get("dry_edges") in ("reflective", "transmissive"):
            experiment["boundaries"][side]["dry_edges"] = s["boundaries"][side]["dry_edges"]
        experiment["forcing_source"]="Synthetic positive raised-cosine external stage; calibrated to model coastal reference crest"
        validate_scenario(experiment)
        started=time.perf_counter(); result=simulate(experiment, progress=on_progress)
        if gauge is None:
            gauge=int(np.argmin(np.sum((result["mesh"]["centres_m"]-point)**2,axis=1)))
            if result["frames"][0]["depth_m"][gauge] < 0.001: raise ValueError("coastal reference point must be initially wet")
        achieved=float(result["max_depth_m"][gauge]+result["mesh"]["bed_elevation_m"][gauge]-baseline)
        runs.append({"forcing_amplitude_m":float(amplitude), "achieved_height_m":achieved, "elapsed_s":time.perf_counter()-started,
                     "solver_steps":result["diagnostics"]["steps"]})
        if on_run is not None: on_run(copy.deepcopy(runs[-1]))
        ordered=sorted(runs,key=lambda r:r["forcing_amplitude_m"])
        if any(b["achieved_height_m"]+tolerance < a["achieved_height_m"] for a,b in zip(ordered,ordered[1:])):
            raise ValueError("nonmonotone coastal response; bounded bisection is not appropriate")
        return result,achieved

    low,low_height=0.0,0.0
    result,low_height=run(low)
    initial_depth=result["frames"][0]["depth_m"]
    baseline_drift=max(float(np.max(np.abs(frame["depth_m"]-initial_depth))) for frame in result["frames"])
    baseline_drift=max(baseline_drift,float(np.max(np.abs(result["max_depth_m"]-initial_depth))))
    if max(abs(low_height),baseline_drift) > min(tolerance,0.001):
        raise ValueError("still-water baseline is not stationary across the domain")
    achieved=low_height
    if abs(achieved-target) > tolerance:
        high=maximum; result,achieved=run(high); high_height=achieved
        if achieved < target-tolerance: raise ValueError("target coastal height is outside the forcing bracket")
        for _ in range(c["max_iterations"]):
            if abs(achieved-target) <= tolerance: break
            # Safeguarded inverse interpolation: actual endpoint responses only,
            # with a 10% minimum bracket reduction and no extrapolation.
            fraction=(target-low_height)/(high_height-low_height) if high_height > low_height else 0.5
            amplitude=low+(high-low)*min(0.9,max(0.1,fraction))
            result,achieved=run(amplitude)
            if achieved < target: low,low_height=amplitude,achieved
            else: high,high_height=amplitude,achieved
        if abs(achieved-target) > tolerance: raise ValueError("coastal calibration failed to converge within bounded iterations")
    result["scenario"]["coastal_control"]={**c, "schema":"pland-coastal-control-1", "definition":"model coastal crest rise above initial still-water baseline",
        "achieved_height_m":achieved, "residual_m":achieved-target, "forcing_amplitude_m":runs[-1]["forcing_amplitude_m"],
        "reference_triangle":gauge, "realised_reference_point_m":result["mesh"]["centres_m"][gauge].tolist(),
        "pulse_shape":"single positive raised cosine", "runs":runs, "real_world_validated":False,
        "baseline_maximum_depth_drift_m":baseline_drift,
        "limitations":["A fixed conditional pulse, tide and direction; height alone does not uniquely determine real inundation.",
                       "Calibration is not validation against observations; no building damage or safe-area certification."]}
    return result
