import math
import unittest
from summarize import summarize


class SummaryTest(unittest.TestCase):
    def capture(self):
        return {"label": "synthetic", "manufacturer": "test", "model": "test", "sdk": 37,
                "calibration": {"sensor": {"maximumRangeMps2": 40, "resolutionMps2": 0.01},
                "trace": {"methodId": "accelerometer-total-calibration-v1", "impactDecision": "unconfigured",
                    "includesGravity": True, "evictedSamples": 0, "rejectedSamples": 0, "maxGapNanos": 10000000,
                    "samples": [{"elapsedNanos": 1000000000+i*10000000, "x": 0, "y": 0, "z": v,
                        "totalAccelerationMps2": v} for i, v in enumerate([9.80665, 19.6133, 29.41995])]}}}

    def test_physical_units_and_actual_interval(self):
        result = summarize(self.capture())
        self.assertAlmostEqual(result["meanTotalG"], 2)
        self.assertAlmostEqual(result["peakTotalG"], 3)
        self.assertAlmostEqual(result["observedRateHz"], 100)
        self.assertEqual(result["samples"], 3)
        self.assertNotIn("impactDetected", result)

    def test_rejects_nonfinite_and_unordered_input(self):
        for bad in [math.nan, math.inf]:
            capture = self.capture()
            capture["calibration"]["trace"]["samples"][0]["z"] = bad
            with self.assertRaises(ValueError): summarize(capture)
        capture = self.capture()
        capture["calibration"]["trace"]["samples"][1]["elapsedNanos"] = 0
        with self.assertRaises(ValueError): summarize(capture)

    def test_rejects_wrong_method_or_mismatched_magnitude(self):
        capture = self.capture()
        capture["calibration"]["trace"]["includesGravity"] = False
        with self.assertRaises(ValueError): summarize(capture)
        capture = self.capture()
        capture["calibration"]["trace"]["samples"][0]["totalAccelerationMps2"] = 100
        with self.assertRaises(ValueError): summarize(capture)


if __name__ == "__main__": unittest.main()
