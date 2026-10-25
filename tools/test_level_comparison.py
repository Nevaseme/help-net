import unittest
from analyze_level_comparison import validate_level_preflight


def fixture():
    events = [dict(kind="armed", protocolVersion=3, preflight=True,
                   sequence=["position", "acceleration"], phaseSeconds=900,
                   model="V2417A")]
    for i, mode in enumerate(["position", "acceleration"]):
        start = dict(elapsedMs=i * 900000, level=80-i, levelScale=100,
                     plugged=0, screenInteractive=False, temperatureTenthsC=250)
        end = dict(start, elapsedMs=(i+1)*900000, level=79-i)
        events.append(dict(kind="phase-end", phase=i, mode=mode, start=start, end=end,
                           alarmLatenessMs=1, screenOnEvents=0, positionFixes=2,
                           dropRegistered=True, disasterModes=[False], samples=90000,
                           firstSampleNanos=i*900000000000,
                           lastSampleNanos=(i+1)*900000000000,
                           maxGapNanos=10000000, maxDeliveryDelayNanos=10000000,
                           wakeLockHeldAtEnd=(mode == "acceleration")))
    return events + [dict(kind="finished", reason="completed")]


class LevelPreflightTest(unittest.TestCase):
    def test_observed_level_decrease_validates_operation_not_power_accuracy(self):
        result = validate_level_preflight(fixture())
        self.assertEqual("operational-preflight-passed", result["decision"])
        self.assertFalse(result["adoption_decided"])

    def test_full_charge_cannot_pass(self):
        events = fixture()
        events[1]["start"]["level"] = 100
        self.assertEqual("not-ready", validate_level_preflight(events)["decision"])

    def test_flat_levels_are_unverified_not_zero_consumption(self):
        events = fixture()
        for e in events[1:3]:
            e["start"]["level"] = e["end"]["level"] = 80
        self.assertEqual("not-ready", validate_level_preflight(events)["decision"])

    def test_interruption_cannot_pass(self):
        events = fixture()
        events[-1]["reason"] = "charging-interrupted"
        self.assertEqual("not-ready", validate_level_preflight(events)["decision"])

    def test_battery_correction_during_sleep_cannot_pass(self):
        events = fixture()
        events.append(dict(kind="battery-sample", phase=0,
                           battery=dict(events[1]["start"], elapsedMs=450000, level=81)))
        self.assertEqual("not-ready", validate_level_preflight(events)["decision"])

    def test_missing_acceleration_or_location_cannot_pass(self):
        for key, value in [("samples", 0), ("positionFixes", 0), ("dropRegistered", False)]:
            events = fixture()
            events[2][key] = value
            self.assertEqual("not-ready", validate_level_preflight(events)["decision"])

    def test_missing_phase_or_duplicate_phase_cannot_pass(self):
        events = fixture()
        self.assertEqual("not-ready", validate_level_preflight(events[:2]+events[-1:])["decision"])
        events[2]["phase"] = 0
        self.assertEqual("not-ready", validate_level_preflight(events)["decision"])

    def test_late_alarm_and_screen_use_cannot_pass(self):
        for key, value in [("alarmLatenessMs", 20000), ("screenOnEvents", 1)]:
            events = fixture()
            events[1][key] = value
            self.assertEqual("not-ready", validate_level_preflight(events)["decision"])

    def test_disaster_regime_and_temperature_change_cannot_pass(self):
        events = fixture()
        events[2]["disasterModes"] = [True]
        self.assertEqual("not-ready", validate_level_preflight(events)["decision"])
        events = fixture()
        events[2]["end"]["temperatureTenthsC"] = 280
        self.assertEqual("not-ready", validate_level_preflight(events)["decision"])


if __name__ == "__main__":
    unittest.main()
