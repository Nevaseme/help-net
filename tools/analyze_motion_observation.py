"""Summarize sampled OS-state intervals; does not certify impact sensitivity or energy use."""
import json
import sys


def summarize(rows):
    conditions = {key: dict(seconds=0.0, samples=0, hz=None) for key in
                  ("screenOff", "screenOffIdle", "screenOffPowerSave", "screenOffIdlePowerSave")}
    problems = set()
    if not any(r.get("phase") == "completed" for r in rows):
        problems.add("capture-incomplete")
    for a, b in zip(rows, rows[1:]):
        dt = (b["elapsedNanos"] - a["elapsedNanos"]) / 1e9
        if dt < 1:  # Adjacent final summary rows are not measurement intervals.
            continue
        if dt > 15:
            problems.add("capture-gap")
            continue
        sa, sb = a["observation"], b["observation"]
        count = sb.get("sampleCount", 0) - sa.get("sampleCount", 0)
        if sa["startedElapsedNanos"] != sb["startedElapsedNanos"] or count < 0:
            problems.add("observation-restarted")
            continue
        fresh = all(r.get("status") == "OBSERVING" and r.get("positionActive") and
                    r["observation"].get("lastSampleElapsedNanos") is not None and
                    0 <= r["elapsedNanos"] - r["observation"]["lastSampleElapsedNanos"] < 1e9 for r in (a, b))
        if count <= 0 or not fresh:
            problems.add("stalled-or-stale")
            continue
        off = not a["interactive"] and not b["interactive"]
        idle = a["deviceIdle"] and b["deviceIdle"]
        saver = a["powerSave"] and b["powerSave"]
        for key, eligible in (("screenOff", off), ("screenOffIdle", off and idle),
                              ("screenOffPowerSave", off and saver),
                              ("screenOffIdlePowerSave", off and idle and saver)):
            if eligible:
                conditions[key]["seconds"] += dt
                conditions[key]["samples"] += count
    for item in conditions.values():
        if item["seconds"]:
            item["hz"] = item["samples"] / item["seconds"]
    return dict(conditions=conditions, problems=sorted(problems),
                maxSampleGapMs=max((r["observation"].get("maxSampleGapNanos", 0) for r in rows), default=0)/1e6,
                maxDeliveryDelayMs=max((r["observation"].get("maxDeliveryDelayNanos", 0) for r in rows), default=0)/1e6,
                note="State sampled every 5s; no claim of physical impact sensitivity, natural Doze entry, or battery usage.")


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8-sig") as f:
        print(json.dumps(summarize([json.loads(line) for line in f if line.strip()]), ensure_ascii=False, indent=2))
