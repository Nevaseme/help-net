"""Describe explicit calibration captures without selecting impact thresholds."""
import argparse
import json
import math
import statistics
from pathlib import Path

STANDARD_GRAVITY = 9.80665


def summarize(capture):
    calibration = capture["calibration"]
    trace = calibration["trace"]
    if (trace.get("methodId") != "accelerometer-total-calibration-v1"
            or trace.get("impactDecision") != "unconfigured"
            or trace.get("includesGravity") is not True):
        raise ValueError("Unsupported measurement method")
    points = trace["samples"]
    if not points:
        raise ValueError("Empty capture")
    previous = None
    gaps, magnitudes = [], []
    clipped = 0
    sensor = calibration["sensor"]
    maximum, resolution = sensor["maximumRangeMps2"], sensor["resolutionMps2"]
    if not math.isfinite(maximum) or maximum <= 0 or not math.isfinite(resolution) or not 0 <= resolution < maximum:
        raise ValueError("Invalid sensor range")
    for point in points:
        timestamp = point["elapsedNanos"]
        if type(timestamp) is not int or timestamp < 0 or (previous is not None and timestamp <= previous):
            raise ValueError("Invalid or unordered timestamp")
        axes = [point[key] for key in ("x", "y", "z")]
        magnitude = point["totalAccelerationMps2"]
        if not all(math.isfinite(value) for value in axes + [magnitude]):
            raise ValueError("Nonfinite measurement")
        if not math.isclose(math.hypot(*axes), magnitude, rel_tol=1e-9, abs_tol=1e-9):
            raise ValueError("Magnitude does not match axes")
        if previous is not None:
            gaps.append((timestamp - previous) / 1e9)
        previous = timestamp
        magnitudes.append(magnitude / STANDARD_GRAVITY)
        clipped += int(any(abs(value) >= maximum - resolution for value in axes))
    duration = (points[-1]["elapsedNanos"] - points[0]["elapsedNanos"]) / 1e9
    ordered = sorted(magnitudes)

    def percentile(fraction):
        index = fraction * (len(ordered) - 1)
        low, high = math.floor(index), math.ceil(index)
        return ordered[low] + (ordered[high] - ordered[low]) * (index - low)

    return {"label": capture["label"], "device": f'{capture["manufacturer"]} {capture["model"]}',
            "samples": len(points), "durationSeconds": duration,
            "observedRateHz": (len(points) - 1) / duration if duration else None,
            "largestRetainedGapSeconds": max(gaps, default=0),
            "largestSessionGapSeconds": trace["maxGapNanos"] / 1e9,
            "meanTotalG": statistics.mean(magnitudes), "medianTotalG": percentile(.5),
            "p95TotalG": percentile(.95), "p99TotalG": percentile(.99), "peakTotalG": max(magnitudes),
            "nearRangeLimitSamples": clipped, "evictedSamples": trace["evictedSamples"],
            "rejectedSamples": trace["rejectedSamples"], "includesGravity": True,
            "interpretation": "Calibration measurements only; no impact classification or threshold selected."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("captures", nargs="+", type=Path)
    args = parser.parse_args()
    print(json.dumps([summarize(json.loads(path.read_text(encoding="utf-8"))) for path in args.captures],
                     ensure_ascii=False, indent=2, allow_nan=False))
