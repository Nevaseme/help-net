import unittest
from analyze_power_comparison import analyze
import analyze_power_comparison as power


def run_fixture(accel_uah=2500):
    events = []
    for i, mode in enumerate(["maps", "position", "acceleration", "acceleration", "position", "maps"]):
        start = dict(elapsedMs=i * 900000, chargeUah=4000000, plugged=0,
                     screenInteractive=False, temperatureTenthsC=300)
        end = dict(start, elapsedMs=(i + 1) * 900000,
                   chargeUah=4000000 - (accel_uah if mode == "acceleration" else 2500))
        events.append(dict(kind="phase-end", phase=i, mode=mode, start=start, end=end,
                           screenOnEvents=0, positionRequests=3, positionFixes=3,
                           dropRegistered=True, disasterAtEnd=False, disasterModes=[False],
                           wakeLockHeldAtEnd=True, samples=90000,
                           firstSampleNanos=i * 900000000000 + 10000000,
                           lastSampleNanos=(i + 1) * 900000000000,
                           maxGapNanos=10000000, maxDeliveryDelayNanos=1000000))
    return events + [dict(kind="finished", reason="completed")]


def preflight_fixture():
    events = [dict(kind="armed", protocolVersion=2, preflight=True)]
    for phase, mode in enumerate(["maps", "acceleration"]):
        samples = [dict(elapsedMs=phase * 600000 + i * 5000,
                        chargeUah=4000000 - round((phase * 600 + i * 5) * 50000 / 3600),
                        currentUa=-50000, voltageMv=4000, temperatureTenthsC=250,
                        plugged=0, screenInteractive=False) for i in range(121)]
        events.extend(dict(kind="battery-sample", phase=phase, battery=s) for s in samples)
        events.append(dict(kind="phase-end", phase=phase, mode=mode, start=samples[0], end=samples[-1],
                           alarmLatenessMs=10, screenOnEvents=0, samples=60000,
                           maxGapNanos=10000000, maxDeliveryDelayNanos=10000000,
                           firstSampleNanos=samples[0]["elapsedMs"] * 1000000,
                           lastSampleNanos=samples[-1]["elapsedMs"] * 1000000,
                           wakeLockHeldAtEnd=True, dropRegistered=True, positionFixes=10,
                           disasterModes=[True]))
    return events + [dict(kind="finished", reason="completed")]


class PowerAnalysisTest(unittest.TestCase):
    def test_consistent_preflight_is_ready_but_does_not_decide_adoption(self):
        result = power.validate_preflight(preflight_fixture())
        self.assertEqual("ready-for-comparison", result["decision"])
        self.assertAlmostEqual(50, result["phases"][0]["integrated_mean_ma"])

    def test_sparse_samples_cannot_validate_battery_current(self):
        events = [e for e in preflight_fixture() if e["kind"] != "battery-sample"
                  or e["phase"] != 0 or e["battery"]["elapsedMs"] in [0, 600000]]
        self.assertEqual("not-ready", power.validate_preflight(events)["decision"])

    def test_counter_disagrees_with_integrated_current(self):
        events = preflight_fixture()
        for event in events:
            if event["kind"] == "battery-sample" and event["phase"] == 0:
                event["battery"]["chargeUah"] = 4000000
        self.assertEqual("not-ready", power.validate_preflight(events)["decision"])

    def test_late_alarm_prevents_long_run(self):
        events = preflight_fixture()
        next(e for e in events if e["kind"] == "phase-end")["alarmLatenessMs"] = 300000
        self.assertEqual("not-ready", power.validate_preflight(events)["decision"])

    def test_preflight_needs_completed_run(self):
        self.assertEqual("not-ready", power.validate_preflight(preflight_fixture()[:-1])["decision"])

    def test_unsupported_current_is_not_zero_power(self):
        events = preflight_fixture()
        next(e for e in events if e["kind"] == "battery-sample")["battery"]["currentUa"] = -2147483648
        self.assertEqual("not-ready", power.validate_preflight(events)["decision"])

    def test_legacy_analyzer_cannot_accept_v2_without_sample_checks(self):
        events = [dict(kind="armed", protocolVersion=2, preflight=False)] + run_fixture()
        events.append(dict(kind="battery-sample", phase=0,
                           battery=dict(elapsedMs=450000, chargeUah=5000000, currentUa=-2147483648)))
        self.assertEqual("invalid", analyze(events, 6000, 3, 10)["decision"])

    def test_large_extra_drain_is_rejected(self):
        result = analyze(run_fixture(20000), 6000, 3, 1000)
        self.assertEqual("exceeds-limit", result["decision"])
        self.assertAlmostEqual(80, result["conditions"]["acceleration"]["mean_ma"])
        self.assertAlmostEqual(28, result["comparisons"]["vs_maps"]["mean_extra_pp_per_day"])

    def test_equal_small_readings_are_not_proof_when_resolution_is_coarse(self):
        result = analyze(run_fixture(), 6000, 1, 1000)
        self.assertEqual("inconclusive", result["decision"])
        self.assertGreater(result["comparisons"]["vs_maps"]["upper_extra_pp_per_day"], 1)

    def test_suspended_sensor_cannot_look_power_efficient(self):
        events = run_fixture()
        events[2]["samples"] = 0
        self.assertEqual("invalid", analyze(events, 6000, 3, 1000)["decision"])

    def test_charging_or_screen_use_invalidates_comparison(self):
        for key, value in [("plugged", 2), ("screenInteractive", True)]:
            events = run_fixture()
            events[0]["end"][key] = value
            self.assertEqual("invalid", analyze(events, 6000, 3, 1000)["decision"])

    def test_complete_equal_repeated_fine_resolution_measurement_can_meet_limit(self):
        result = analyze(run_fixture(), 6000, 3, 10)
        self.assertEqual("within-measured-bound", result["decision"])

    def test_incomplete_run_cannot_be_used(self):
        self.assertEqual("invalid", analyze(run_fixture()[:-1], 6000, 3, 1000)["decision"])

    def test_failed_location_attempts_do_not_count_as_workload(self):
        events = run_fixture()
        events[1]["positionFixes"] = 0
        self.assertEqual("invalid", analyze(events, 6000, 3, 10)["decision"])

    def test_transient_disaster_invalidates_comparison(self):
        events = run_fixture()
        events[2]["disasterModes"] = [False, True]
        self.assertEqual("invalid", analyze(events, 6000, 3, 10)["decision"])


if __name__ == "__main__":
    unittest.main()
