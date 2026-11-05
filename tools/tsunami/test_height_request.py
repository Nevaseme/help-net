import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.tsunami.height_request import height_value, prepare_height_request, execute_height_request
from tools.tsunami.test_coastal import experiment


class HeightRequestTest(unittest.TestCase):
    def test_decimal_heights_are_exact_and_invalid_values_are_not_clamped(self):
        self.assertEqual(height_value("12.50"), 12.5)
        self.assertEqual(height_value("0"), 0)
        for value in (True, "NaN", "Infinity", -1, "10m", "1e10000", "0.123456789012345678901", []):
            with self.subTest(value=value), self.assertRaises(ValueError):
                height_value(value)

    def test_identity_covers_height_and_physical_conditions_not_old_output_labels(self):
        s, c = experiment()
        original = copy.deepcopy((s, c))
        first = prepare_height_request(s, c, ".1")
        self.assertEqual(first, prepare_height_request(s, c, "0.100"))
        other = prepare_height_request(s, c, ".15")
        self.assertNotEqual(first["request_id"], other["request_id"])
        self.assertEqual(first["family_id"], other["family_id"])
        s["coastal_control"] = {"achieved_height_m": 123, "target_height_m": 3}
        s["title"] = "Old completed result"
        s["publication_status"] = "rejected_boundary_screen"
        s["boundary_audit"] = {"inundation_reaches_closed_cut_boundary": True}
        self.assertEqual(first, prepare_height_request(s, c, .1))
        s["manning_n"] += .01
        self.assertNotEqual(first["family_id"], prepare_height_request(s, c, .1)["family_id"])
        self.assertEqual(c, original[1])

    def test_invalid_control_and_altered_prepared_request_never_reach_solver(self):
        s, c = experiment()
        with self.assertRaises(ValueError):
            prepare_height_request(s, c, 1, maximum_forcing_m=0)
        request = prepare_height_request(s, c, .1)
        request["scenario"]["manning_n"] += .01
        with tempfile.TemporaryDirectory() as directory, patch("tools.tsunami.coastal.calibrate_coastal_height") as run:
            with self.assertRaisesRegex(ValueError, "changed"):
                execute_height_request(request, Path(directory)/"new")
            run.assert_not_called()

    def test_real_calibrated_result_and_existing_destination_protection(self):
        s, c = experiment()
        request = prepare_height_request(s, c, .13)
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)/"result"
            execute_height_request(request, target)
            result = json.loads((target/"scenario.json").read_text())
            self.assertEqual(result["height_request"]["height_m"], .13)
            self.assertEqual(result["coastal_control"]["target_height_m"], .13)
            self.assertLessEqual(abs(result["coastal_control"]["residual_m"]), c["tolerance_m"])
            self.assertIn(result["publication_status"], ["rejected_boundary_screen", "experimental_requires_review"])
            self.assertEqual(result["boundary_audit"]["schema"], "pland-boundary-audit-3")
            with patch("tools.tsunami.coastal.calibrate_coastal_height") as run:
                with self.assertRaises(FileExistsError):
                    execute_height_request(request, target)
                run.assert_not_called()

    def test_boundary_rejection_is_saved_for_diagnosis_not_overridden_by_height_match(self):
        s, c = experiment()
        request = prepare_height_request(s, c, .13)
        audit = {"inundation_reaches_closed_cut_boundary": True,
                 "supercritical_open_boundary_observed_in_frames": False}
        with tempfile.TemporaryDirectory() as directory, patch("tools.tsunami.boundary_audit.audit_boundaries", return_value=audit):
            target = Path(directory)/"diagnostic"
            execute_height_request(request, target)
            result = json.loads((target/"scenario.json").read_text())
            self.assertEqual(result["publication_status"], "rejected_boundary_screen")
            self.assertLessEqual(abs(result["coastal_control"]["residual_m"]), c["tolerance_m"])


if __name__ == "__main__":
    unittest.main()
