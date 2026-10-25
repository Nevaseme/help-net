"""Operational preflight for coarse battery-level trials, not power calibration."""
import argparse
import json
from pathlib import Path


def validate_level_preflight(events):
    errors = []
    armed = [e for e in events if e.get("kind") == "armed"]
    if len(armed) != 1 or armed[0].get("protocolVersion") != 3 or armed[0].get("preflight") is not True:
        errors.append("Expected one protocol-v3 preflight")
    elif (armed[0].get("model") != "V2417A" or armed[0].get("sequence") != ["position", "acceleration"]
          or armed[0].get("phaseSeconds") != 900):
        errors.append("Wrong device or preflight configuration")
    if [e.get("reason") for e in events if e.get("kind") == "finished"] != ["completed"]:
        errors.append("Preflight incomplete or interrupted")
    rows = [e for e in events if e.get("kind") == "phase-end"]
    if [(e.get("phase"), e.get("mode")) for e in rows] != [(0, "position"), (1, "acceleration")]:
        errors.append("Both ordered conditions are required")
    regimes, levels = set(), []
    for row in rows:
        try:
            p, start, end = row["phase"], row["start"], row["end"]
            duration = end["elapsedMs"] - start["elapsedMs"]
            if not 900000 <= duration <= 905000 or not 0 <= row["alarmLatenessMs"] <= 5000:
                errors.append(f"Phase {p}: deadline or duration invalid")
            samples = [start, end] + [e["battery"] for e in events if e.get("kind") == "battery-sample"
                                     and e.get("phase") == p
                                     and start["elapsedMs"] <= e["battery"]["elapsedMs"] <= end["elapsedMs"]]
            samples.sort(key=lambda s: s["elapsedMs"])
            if any(s["levelScale"] != 100 or type(s["level"]) is not int or not 20 <= s["level"] <= 90 for s in samples):
                errors.append(f"Phase {p}: level outside validated range 20–90 or invalid scale")
            if row["screenOnEvents"] or any(s["plugged"] != 0 or s["screenInteractive"] for s in samples):
                errors.append(f"Phase {p}: charging or screen use")
            if max(s["temperatureTenthsC"] for s in samples) - min(s["temperatureTenthsC"] for s in samples) > 10:
                errors.append(f"Phase {p}: temperature is not stable")
            levels.extend((s["elapsedMs"], s["level"], s["temperatureTenthsC"]) for s in samples)
            if row["positionFixes"] <= 0 or not row["dropRegistered"] or len(row["disasterModes"]) != 1:
                errors.append(f"Phase {p}: missing or unstable location/drop workload")
            regimes.update(row["disasterModes"])
            if row["mode"] == "acceleration":
                if not 90 <= row["samples"] / (duration / 1000) <= 110 or not row["wakeLockHeldAtEnd"]:
                    errors.append(f"Phase {p}: acceleration not continuous")
                if row["maxGapNanos"] > 1000000000 or row["maxDeliveryDelayNanos"] > 1000000000:
                    errors.append(f"Phase {p}: acceleration gap")
                if row["firstSampleNanos"] / 1000000 - start["elapsedMs"] > 1000 or end["elapsedMs"] - row["lastSampleNanos"] / 1000000 > 1000:
                    errors.append(f"Phase {p}: incomplete acceleration coverage")
        except (KeyError, TypeError, ValueError, ZeroDivisionError) as exc:
            errors.append(f"Malformed phase: {exc}")
    levels.sort()
    if levels:
        if any(b[1] > a[1] for a, b in zip(levels, levels[1:])):
            errors.append("Upward level correction observed")
        if levels[0][1] - levels[-1][1] < 1:
            errors.append("No real level decrement observed; not proof of zero consumption")
        if max(s[2] for s in levels) - min(s[2] for s in levels) > 10:
            errors.append("Temperature differs across conditions")
    else:
        errors.append("No level observations")
    if len(regimes) != 1:
        errors.append("Location request regimes differ or missing")
    return dict(decision="not-ready" if errors else "operational-preflight-passed", reasons=errors,
                adoption_decided=False,
                limitations="Validates collection and an observed battery-level decrement only. Does not calibrate the fuel gauge or guarantee an overnight effect is resolvable. No mAh or added-drain estimate from this short test.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    args = parser.parse_args()
    events = [json.loads(line) for line in args.log.read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    print(json.dumps(validate_level_preflight(events), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
