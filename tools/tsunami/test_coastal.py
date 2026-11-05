"""Coastal control is achieved model stage at a declared gauge, not forcing amplitude."""
import copy
import unittest
from unittest.mock import patch
import tempfile

from tools.tsunami.coastal import calibrate_coastal_height, pulse_samples, with_xyz_crop
from tools.tsunami.test_engine import scenario
from tools.tsunami.engine import simulate
from tools.tsunami import test_terrain
import numpy as np


def experiment():
    s = scenario(nx=20, ny=2)
    s["terrain"]["bed_m"] = np.tile(np.linspace(1, -1, 21), (3,1)).tolist()
    s["initial"]["stage_m"] = 0.0
    s["duration_s"] = 8.0; s["output_interval_s"] = 2.0
    return s, {"target_height_m": 0.1, "reference_point_m": [17.0,1.0], "boundary": "right",
               "baseline_m": 0.0, "pulse_duration_s": 3.0, "pulse_delay_s": 0.5,
               "maximum_forcing_m": 0.8, "tolerance_m": 0.005, "max_iterations": 12}


class CoastalTest(unittest.TestCase):
    def test_baseline_checks_the_whole_domain_not_just_reference_cell(self):
        s,c = experiment()
        r = simulate(s)
        r["max_depth_m"][0] += 0.1
        with patch("tools.tsunami.coastal.simulate", return_value=r):
            with self.assertRaisesRegex(ValueError,"baseline.*stationary"):
                calibrate_coastal_height(s,c)

    def test_crop_preserves_exact_samples_and_unverified_site_never_runs(self):
        s,c = experiment()
        original = copy.deepcopy(s)
        with tempfile.TemporaryDirectory() as directory:
            tile = test_terrain.TerrainTest().tile(directory, "crop", [[x,y,x-2] for x in range(3) for y in range(3)])
            cropped = with_xyz_crop(s, [tile], [0,0,2,2], 1, crs="EPSG:6676", vertical_datum="unresolved")
        self.assertEqual(s,original)
        self.assertEqual(cropped["grid"]["nx"],2)
        self.assertEqual(cropped["terrain"]["bed_m"], [[-2,-1,0]]*3)
        self.assertEqual(len(cropped["terrain"]["source_sha256"]),64)
        c["reference_point_m"]=[0.5,0.5]
        with patch("tools.tsunami.coastal.simulate") as solver:
            with self.assertRaisesRegex(ValueError,"vertical datum"):
                calibrate_coastal_height(cropped,c)
            solver.assert_not_called()

    def test_raised_cosine_pulse_starts_and_ends_at_baseline(self):
        samples = pulse_samples(1.0, 2.0, 1.0, 4.0, 6.0)
        a = np.asarray(samples)
        self.assertEqual(a[0,0], 0); self.assertEqual(a[-1,0], 6)
        self.assertEqual(a[:,1].max(), 3)
        self.assertTrue((a[a[:,0] <= 1,1] == 1).all())
        self.assertTrue((a[a[:,0] >= 5,1] == 1).all())

    def test_control_matches_computed_coastal_crest_and_preserves_input(self):
        s,c = experiment(); original = copy.deepcopy(s)
        r = calibrate_coastal_height(s,c)
        calibration = r["scenario"]["coastal_control"]
        self.assertEqual(s,original)
        self.assertLessEqual(abs(calibration["residual_m"]), c["tolerance_m"])
        gauge = calibration["reference_triangle"]
        achieved = r["max_depth_m"][gauge]+r["mesh"]["bed_elevation_m"][gauge]-c["baseline_m"]
        self.assertAlmostEqual(achieved,calibration["achieved_height_m"])
        self.assertNotAlmostEqual(calibration["forcing_amplitude_m"], c["target_height_m"], places=2)
        self.assertFalse(calibration["real_world_validated"])

    def test_dry_or_outside_reference_and_unachievable_height_fail(self):
        for point in ([0,1], [100,1]):
            s,c=experiment(); c["reference_point_m"]=point
            with self.assertRaises(ValueError): calibrate_coastal_height(s,c)
        s,c=experiment(); c["target_height_m"]=20
        with self.assertRaisesRegex(ValueError,"bracket"): calibrate_coastal_height(s,c)


if __name__ == "__main__": unittest.main()
