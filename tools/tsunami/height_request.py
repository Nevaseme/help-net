"""Prepare and run an exact coastal-height request using the existing solver.

This is an offline computation entry point, not an HTTP/cloud execution service.
No neighboring height, terrain threshold, or scaled inundation is substituted.
"""
import argparse
import copy
import hashlib
import json
import math
from decimal import Decimal, InvalidOperation
from pathlib import Path

CONTROL_KEYS = ("reference_point_m", "boundary", "baseline_m", "pulse_duration_s",
                "pulse_delay_s", "maximum_forcing_m", "tolerance_m", "max_iterations")
ADAPTER_METHOD = "anuga-3.3.10-native-scalar-flather-rk-observation-v4-mixed-outlet"


def height_value(value):
    """Accept finite, nonnegative decimal metres without silently rounding input."""
    if isinstance(value, bool) or not isinstance(value, (str, int, float, Decimal)):
        raise ValueError("height must be a number in metres")
    try:
        decimal = Decimal(str(value).strip())
    except InvalidOperation as error:
        raise ValueError("height must be a number in metres") from error
    if not decimal.is_finite() or decimal < 0:
        raise ValueError("height must be finite and nonnegative")
    result = float(decimal)
    if not math.isfinite(result) or Decimal(str(result)) != decimal:
        raise ValueError("height cannot be represented without changing the requested value")
    return 0.0 if result == 0 else result


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"),
                                    ensure_ascii=False, allow_nan=False).encode()).hexdigest()


def prepare_height_request(source, control, height_m, *, maximum_forcing_m=None):
    """Normalize a previous scenario into a new, independently computed request.

    A completed 3m scenario may be used as a terrain/condition template, but its
    calibrated forcing and achieved result metadata are not reused as a result.
    The explicit forcing bracket is a numerical search bound, not the wave height.
    """
    from tools.tsunami.engine import validate_scenario
    height = height_value(height_m)
    s = copy.deepcopy(source)
    c = {key: copy.deepcopy(control[key]) for key in CONTROL_KEYS}
    if maximum_forcing_m is not None:
        c["maximum_forcing_m"] = height_value(maximum_forcing_m)
    for key in ("baseline_m", "pulse_duration_s", "pulse_delay_s", "maximum_forcing_m", "tolerance_m"):
        if isinstance(c[key], bool) or not isinstance(c[key], (int, float)) or not math.isfinite(c[key]):
            raise ValueError("finite calibration controls required")
    if c["maximum_forcing_m"] <= 0 or not 0 < c["tolerance_m"] <= max(.01, height/4):
        raise ValueError("invalid forcing bracket or tolerance")
    if c["boundary"] not in ("left", "right", "top", "bottom"):
        raise ValueError("explicit inlet boundary required")
    if type(c["max_iterations"]) is not int or not 1 <= c["max_iterations"] <= 20:
        raise ValueError("bounded calibration iterations required")
    if c["pulse_delay_s"] < 0 or c["pulse_duration_s"] <= 0 or c["pulse_delay_s"]+c["pulse_duration_s"] > s["duration_s"]:
        raise ValueError("pulse must fit the simulated duration")
    # Strip only derived labels/results. Preserve terrain, datum, tide and all
    # physical/numerical settings so a different case never shares an identity.
    for key in ("coastal_control", "height_request", "experiment_runtime_s", "id", "title",
                "boundary_audit", "publication_status", "numerical_run_provenance"):
        s.pop(key, None)
    s["boundaries"][c["boundary"]] = {"type": "open_still_water", "baseline_m": c["baseline_m"]}
    s["forcing_source"] = "Height-controlled positive raised-cosine coastal experiment"
    family = digest({"adapter_method": ADAPTER_METHOD, "scenario": s, "control": {k: v for k, v in c.items()
                    if k not in ("maximum_forcing_m", "tolerance_m", "max_iterations")}})
    c["target_height_m"] = height
    request_id = digest({"family": family, "control": c})
    s["id"] = "coastal-height-" + request_id[:24]
    s["title"] = f"Conditional coastal height {height:g}m"
    validate_scenario(s)
    return {"schema": "pland-height-request-1", "adapter_method": ADAPTER_METHOD, "request_id": request_id,
            "family_id": family, "height_m": height, "scenario": s, "control": c}


def execute_height_request(request, destination, *, on_run=None, on_progress=None):
    from tools.tsunami.coastal import calibrate_coastal_height
    from tools.tsunami.engine import write_bundle
    from tools.tsunami.boundary_audit import audit_boundaries
    if request.get("schema") != "pland-height-request-1":
        raise ValueError("unsupported height request")
    normalized = prepare_height_request(request["scenario"], request["control"], request["height_m"])
    if normalized != request:
        raise ValueError("height request or conditions changed after preparation")
    destination = Path(destination)
    if destination.exists():
        raise FileExistsError("result destination already exists; no existing calculation is overwritten")
    result = calibrate_coastal_height(request["scenario"], request["control"],
                                     on_run=on_run, on_progress=on_progress)
    result["scenario"]["height_request"] = {key: request[key] for key in
                                           ("schema", "adapter_method", "request_id", "family_id", "height_m")}
    audit = audit_boundaries(result)
    rejected = (audit["inundation_reaches_closed_cut_boundary"] or
                audit.get("supercritical_characteristic_boundary_observed",
                          audit.get("supercritical_open_boundary_observed",
                                    audit.get("supercritical_open_boundary_observed_in_frames", False))))
    result["scenario"]["boundary_audit"] = audit
    result["scenario"]["publication_status"] = ("rejected_boundary_screen" if rejected
                                                else "experimental_requires_review")
    return write_bundle(result, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scenario", type=Path)
    parser.add_argument("control", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--height", required=True)
    parser.add_argument("--maximum-forcing", required=True,
                        help="Explicit maximum offshore forcing used to bracket calibration")
    parser.add_argument("--prepare-only", action="store_true",
                        help="Write a request JSON only; do not run or claim a computed result")
    args = parser.parse_args()
    request = prepare_height_request(json.loads(args.scenario.read_text(encoding="utf-8-sig")),
                                     json.loads(args.control.read_text(encoding="utf-8-sig")),
                                     args.height, maximum_forcing_m=args.maximum_forcing)
    if args.prepare_only:
        with args.output.open("x", encoding="utf-8") as out:
            json.dump(request, out, ensure_ascii=False, indent=2, allow_nan=False)
        print(json.dumps({"status": "prepared-not-computed", "request_id": request["request_id"]}))
    else:
        manifest = execute_height_request(request, args.output,
            on_run=lambda run: print(json.dumps({"calibration_run": run}), flush=True))
        status = json.loads((args.output/"scenario.json").read_text(encoding="utf-8"))["publication_status"]
        print(json.dumps({"status": status, "manifest": manifest}))


if __name__ == "__main__":
    main()
