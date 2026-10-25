"""Compare on-device charge-counter trials; never infer savings from missing samples."""
import argparse
import json
from pathlib import Path
from statistics import mean

SEQUENCE = ["maps", "position", "acceleration", "acceleration", "position", "maps"]


def validate_preflight(events):
    """Gate a longer trial; internal consistency is not an external calibration.

    Never integrate CURRENT_AVERAGE: its averaging window is device-dependent.
    Missing CURRENT_NOW data during sleep is a failed measurement, not zero drain.
    """
    errors, results = [], []
    if not any(e.get("kind") == "armed" and e.get("protocolVersion") == 2
               and e.get("preflight") is True for e in events):
        errors.append("Expected a protocol-v2 preflight")
    if not any(e.get("kind") == "finished" and e.get("reason") == "completed" for e in events):
        errors.append("Preflight did not complete")
    phases = [e for e in events if e.get("kind") == "phase-end"]
    if [e.get("mode") for e in phases] != ["maps", "acceleration"]:
        errors.append("Expected both complete preflight conditions")
    for row in phases:
        try:
            phase, start, end = row["phase"], row["start"], row["end"]
            duration = end["elapsedMs"] - start["elapsedMs"]
            if not 600000 <= duration <= 605000 or not 0 <= row["alarmLatenessMs"] <= 5000:
                errors.append(f"Phase {phase}: deadline not met within five seconds")
            samples = [start] + [e["battery"] for e in events if e.get("kind") == "battery-sample"
                                and e.get("phase") == phase
                                and start["elapsedMs"] <= e["battery"]["elapsedMs"] <= end["elapsedMs"]] + [end]
            samples = sorted({s["elapsedMs"]: s for s in samples}.values(), key=lambda s: s["elapsedMs"])
            gaps = [b["elapsedMs"] - a["elapsedMs"] for a, b in zip(samples, samples[1:])]
            max_gap = max(gaps, default=duration)
            if max_gap > 15000:
                errors.append(f"Phase {phase}: current sampling gap {max_gap} ms; cannot integrate sleep")
            if row["screenOnEvents"] or any(s["plugged"] != 0 or s["screenInteractive"] for s in samples):
                errors.append(f"Phase {phase}: screen use or charging")
            if any(not -20000000 < s["currentUa"] <= 0 for s in samples):
                errors.append(f"Phase {phase}: unsupported, charging, or implausible current")
            if any(s["chargeUah"] <= 0 for s in samples):
                errors.append(f"Phase {phase}: unsupported charge counter")
            if max(s["temperatureTenthsC"] for s in samples) - min(s["temperatureTenthsC"] for s in samples) > 10:
                errors.append(f"Phase {phase}: temperature range exceeds one degree C")
            if any(b["chargeUah"] - a["chargeUah"] > 1000 for a, b in zip(samples, samples[1:])):
                errors.append(f"Phase {phase}: upward charge correction")
            charge_mah = (start["chargeUah"] - end["chargeUah"]) / 1000
            integrated_mah = sum(-(a["currentUa"] + b["currentUa"]) / 2 / 1000
                                 * (b["elapsedMs"] - a["elapsedMs"]) / 3600000
                                 for a, b in zip(samples, samples[1:]))
            # A coarse counter needs enough signal, plus agreement with sampled current.
            if charge_mah < 4 or abs(charge_mah - integrated_mah) > max(2, integrated_mah * .2):
                errors.append(f"Phase {phase}: charge/current consistency or resolution insufficient")
            if row["mode"] == "acceleration":
                if not 90 <= row["samples"] / (duration / 1000) <= 110 or not row["wakeLockHeldAtEnd"]:
                    errors.append(f"Phase {phase}: continuous acceleration not demonstrated")
                if row["maxGapNanos"] > 1000000000 or row["maxDeliveryDelayNanos"] > 1000000000:
                    errors.append(f"Phase {phase}: acceleration delivery gap")
                if row["firstSampleNanos"] / 1000000 - start["elapsedMs"] > 1000 or end["elapsedMs"] - row["lastSampleNanos"] / 1000000 > 1000:
                    errors.append(f"Phase {phase}: incomplete acceleration coverage")
                if not row["dropRegistered"] or row["positionFixes"] <= 0 or len(row["disasterModes"]) != 1:
                    errors.append(f"Phase {phase}: position/drop workload not stable")
            results.append(dict(phase=phase, mode=row["mode"], duration_ms=duration,
                                max_sample_gap_ms=max_gap, counter_mah=charge_mah,
                                integrated_mah=integrated_mah,
                                integrated_mean_ma=integrated_mah * 3600000 / duration,
                                values_usable=not any(f"Phase {phase}:" in e for e in errors)))
        except (KeyError, TypeError, ValueError, ZeroDivisionError) as exc:
            errors.append(f"Malformed preflight phase: {exc}")
    return dict(decision="not-ready" if errors else "ready-for-comparison", reasons=errors, phases=results,
                limitations="Internal consistency only, not an externally calibrated power measurement. No adoption decision. Invalid-phase integrals are diagnostics, not consumption estimates.")


def analyze(events, capacity_mah, limit_pp, counter_quantum_uah):
    if any(e.get("kind") == "armed" and e.get("protocolVersion", 1) >= 2 for e in events):
        return {"decision": "invalid", "reasons": ["Protocol v2 must not use endpoint-only legacy analysis. Use --preflight for preflight logs; a long-run sample validator is required before comparison."]}
    if capacity_mah <= 0 or limit_pp < 0 or counter_quantum_uah <= 0:
        raise ValueError("Positive capacity/resolution and a nonnegative limit are required")
    phases = [e for e in events if e.get("kind") == "phase-end"]
    errors = []
    if not any(e.get("kind") == "finished" and e.get("reason") == "completed" for e in events):
        errors.append("No successful completion record")
    if len(phases) != 6 or [e.get("mode") for e in phases] != SEQUENCE or [e.get("phase") for e in phases] != list(range(6)):
        errors.append("Expected six complete ordered counterbalanced phases")
    values = {name: [] for name in set(SEQUENCE)}
    disasters = set()
    for row in phases:
        try:
            begin, end = row["start"], row["end"]
            duration_ms = end["elapsedMs"] - begin["elapsedMs"]
            if duration_ms < 900000:
                errors.append(f"Phase {row['phase']}: less than 15 minutes")
            if begin["chargeUah"] <= 0 or end["chargeUah"] <= 0 or end["chargeUah"] > begin["chargeUah"]:
                errors.append(f"Phase {row['phase']}: invalid or rising charge counter")
            if any(s["plugged"] != 0 or s["screenInteractive"] for s in (begin, end)) or row["screenOnEvents"]:
                errors.append(f"Phase {row['phase']}: charging or screen use")
            if abs(end["temperatureTenthsC"] - begin["temperatureTenthsC"]) > 30:
                errors.append(f"Phase {row['phase']}: temperature changed by more than 3 C")
            hours = duration_ms / 3600000
            if hours <= 0:
                raise ValueError("nonpositive duration")
            rate = (begin["chargeUah"] - end["chargeUah"]) / 1000 / hours
            # Conservative endpoint quantization envelope, not a statistical confidence interval.
            uncertainty = 2 * counter_quantum_uah / 1000 / hours
            values[row["mode"]].append((rate, uncertainty))
            if row["mode"] != "maps":
                modes = set(row["disasterModes"])
                disasters.update(modes)
                if len(modes) != 1 or row["disasterAtEnd"] not in modes:
                    errors.append(f"Phase {row['phase']}: location regime changed or was not observed")
                if row["positionRequests"] <= 0 or row["positionFixes"] <= 0 or not row["dropRegistered"]:
                    errors.append(f"Phase {row['phase']}: location/drop workload missing")
            if row["mode"] == "acceleration":
                hz = row["samples"] / (duration_ms / 1000)
                if not 90 <= hz <= 110 or not row["wakeLockHeldAtEnd"]:
                    errors.append(f"Phase {row['phase']}: 100 Hz continuous monitoring not demonstrated")
                if row["maxGapNanos"] > 1000000000 or row["maxDeliveryDelayNanos"] > 1000000000:
                    errors.append(f"Phase {row['phase']}: sample/delivery gap exceeds one second")
                if row["firstSampleNanos"] / 1000000 - begin["elapsedMs"] > 1000 or end["elapsedMs"] - row["lastSampleNanos"] / 1000000 > 1000:
                    errors.append(f"Phase {row['phase']}: samples do not cover phase boundaries")
        except (KeyError, TypeError, ValueError, ZeroDivisionError) as exc:
            errors.append(f"Malformed phase: {exc}")
    if len(disasters) > 1:
        errors.append("Location request regimes differ across conditions")
    if errors:
        return {"decision": "invalid", "reasons": errors}
    conditions = {
        mode: {"rates_ma": [r for r, _ in rows], "mean_ma": mean(r for r, _ in rows),
               "lower_ma": min(r - q for r, q in rows), "upper_ma": max(r + q for r, q in rows)}
        for mode, rows in values.items()
    }
    scale = 24 * 100 / capacity_mah
    candidate = conditions["acceleration"]
    comparisons = {}
    for reference in ("maps", "position"):
        base = conditions[reference]
        comparisons["vs_" + reference] = {
            "mean_extra_pp_per_day": (candidate["mean_ma"] - base["mean_ma"]) * scale,
            "lower_extra_pp_per_day": (candidate["lower_ma"] - base["upper_ma"]) * scale,
            "upper_extra_pp_per_day": (candidate["upper_ma"] - base["lower_ma"]) * scale,
        }
    if any(c["lower_extra_pp_per_day"] > limit_pp for c in comparisons.values()):
        decision = "exceeds-limit"
    elif all(c["upper_extra_pp_per_day"] <= limit_pp for c in comparisons.values()):
        decision = "within-measured-bound"
    else:
        decision = "inconclusive"
    return {"decision": decision, "conditions": conditions, "comparisons": comparisons,
            "capacity_mah": capacity_mah, "limit_extra_pp_per_day": limit_pp,
            "counter_quantum_uah": counter_quantum_uah,
            "limitations": "Two repetitions; stationary screen-off shared measurement overhead. Bounds include observed spread and supplied counter resolution, not all fuel-gauge/systematic error. 24-hour figures are extrapolations."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--preflight", action="store_true")
    parser.add_argument("--capacity-mah", type=float)
    parser.add_argument("--max-extra-pp-per-day", type=float)
    parser.add_argument("--counter-quantum-uah", type=float)
    args = parser.parse_args()
    events = [json.loads(line) for line in args.log.read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    if args.preflight:
        result = validate_preflight(events)
    else:
        if any(v is None for v in (args.capacity_mah, args.max_extra_pp_per_day, args.counter_quantum_uah)):
            parser.error("Legacy analysis requires capacity, limit, and counter resolution")
        result = analyze(events, args.capacity_mah, args.max_extra_pp_per_day, args.counter_quantum_uah)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
