"""Independent checks of the adapter, wet/dry evolution and exported results."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tools.tsunami.engine import simulate, write_bundle, validate_scenario
import numpy as np


def scenario(nx=40, ny=4, width=20.0, height=2.0):
    return {
        "schema": "pland-tsunami-scenario-1", "id": "synthetic-benchmark",
        "title": "Synthetic hydrodynamic benchmark", "kind": "benchmark",
        "grid": {"nx": nx, "ny": ny, "width_m": width, "height_m": height,
                 "origin_m": [0.0, 0.0], "crs": "LOCAL_METRES"},
        "terrain": {"kind": "synthetic", "source": "Explicit test geometry",
                    "vertical_datum": "local benchmark datum", "bed_m": np.zeros((ny+1, nx+1)).tolist()},
        "initial": {"stage_m": 1.0, "xmomentum_m2_s": 0.0, "ymomentum_m2_s": 0.0},
        "forcing_source": "Explicit synthetic initial condition; not a real earthquake",
        "boundaries": {side: {"type": "reflective"} for side in ("left", "right", "top", "bottom")},
        "manning_n": 0.0, "duration_s": 0.5, "output_interval_s": 0.25,
        "arrival_depth_m": 0.01,
    }


class InputTest(unittest.TestCase):
    def test_missing_bathymetry_is_rejected_instead_of_inventing_flat_sea(self):
        s = scenario(); del s["terrain"]["bed_m"]
        with self.assertRaises(ValueError): validate_scenario(s)

    def test_nonfinite_terrain_and_oversized_jobs_are_rejected(self):
        for mutate in (lambda s: s["terrain"]["bed_m"][0].__setitem__(0, float("nan")),
                       lambda s: s["grid"].__setitem__("nx", 1_000_000)):
            s = scenario(); mutate(s)
            with self.assertRaises(ValueError): validate_scenario(s)

    def test_waveform_must_cover_run_and_be_strictly_increasing(self):
        for samples in ([[0, 1, 0, 0], [0.1, 1, 0, 0]],
                        [[0, 1, 0, 0], [0, 1, 0, 0], [1, 1, 0, 0]]):
            s = scenario(); s["boundaries"]["left"] = {"type": "time_series", "samples": samples}
            with self.assertRaises(ValueError): validate_scenario(s)

    def test_real_site_requires_measured_provenance_and_projected_coordinates(self):
        s = scenario(); s["kind"] = "site-scenario"
        with self.assertRaises(ValueError): validate_scenario(s)

    def test_flather_inlet_rejects_any_initially_dry_boundary_nodes(self):
        s = scenario()
        s["boundaries"]["right"] = {"type": "flather_stage", "baseline_m": 0.0, "samples": [[0,0],[0.5,0.1]]}
        with self.assertRaisesRegex(ValueError, "inlet.*wet"): validate_scenario(s)


class PhysicsTest(unittest.TestCase):
    def test_mixed_transmissive_land_copies_outgoing_state_without_forcing_or_wall(self):
        s=scenario(nx=4,ny=4)
        s['terrain']['bed_m']=np.tile(np.linspace(-1,1,5)[:,None],(1,5)).tolist()
        s['initial'].update(stage_m=2,xmomentum_m2_s=1)
        s['duration_s']=.01;s['output_interval_s']=.01
        s['boundaries']['right']={'type':'flather_stage','baseline_m':0,'dry_edges':'transmissive','samples':[[0,3],[.01,3]]}
        r=simulate(s);o=r['boundary_observations'];right=np.asarray(o['side'])=='right'
        characteristic=np.asarray(o['characteristic_subcriticality_required'],dtype=bool)
        land=right&~characteristic
        self.assertTrue(land.any());self.assertTrue((right&characteristic).any())
        self.assertFalse(np.asarray(o['closed_cut'])[right].any())
        np.testing.assert_allclose(np.asarray(o['initial_ghost_stage_m'])[land],2,atol=1e-12)
        inside=np.asarray(o['initial_inside_normal_momentum_m2_s'])[land]
        self.assertTrue((inside>.9).all())
        np.testing.assert_allclose(np.asarray(o['initial_ghost_normal_momentum_m2_s'])[land],inside,atol=1e-12)
        self.assertLess(abs(r['diagnostics']['mass_balance_error_m3']),1e-10)

    def test_mixed_transmissive_land_retains_sloping_still_water(self):
        s=scenario(nx=8,ny=4)
        s['terrain']['bed_m']=np.tile(np.linspace(-2,2,5)[:,None],(1,9)).tolist()
        s['initial']['stage_m']=0
        s['boundaries']['right']={'type':'flather_stage','baseline_m':0,'dry_edges':'transmissive','samples':[[0,0],[.5,0]]}
        r=simulate(s)
        np.testing.assert_allclose(r['max_depth_m'],r['frames'][0]['depth_m'],atol=1e-12)
        self.assertLess(abs(r['diagnostics']['mass_balance_error_m3']),1e-10)

    def test_mixed_vector_reflection_matches_native_scalar_reflection_for_a_wave(self):
        import anuga
        from anuga.abstract_2d_finite_volumes.generic_boundary_conditions import Boundary
        from unittest.mock import patch
        s=scenario(nx=12,ny=3)
        s["terrain"]["bed_m"]=np.tile(np.linspace(1,-1,13),(4,1)).tolist()
        s["initial"]["stage_m"]=0;s["duration_s"]=4;s["output_interval_s"]=1
        s["boundaries"]={k:{"type":"open_still_water","baseline_m":0} for k in s["boundaries"]}
        s["boundaries"]["right"]={"type":"flather_stage","baseline_m":0,
            "samples":[[0,0],[1,.5],[2,.5],[4,0]]}
        fast=simulate(s)
        with patch.object(anuga.Reflective_boundary,"evaluate_segment",Boundary.evaluate_segment):
            scalar=simulate(s)
        for a,b in zip(fast["frames"],scalar["frames"]):
            for field in ("depth_m","xmomentum_m2_s","ymomentum_m2_s"):
                np.testing.assert_allclose(a[field],b[field],atol=1e-11,rtol=0)

    def test_flather_initial_ghost_uses_characteristics_not_full_external_stage(self):
        s = scenario(nx=8, ny=2)
        s["terrain"]["bed_m"] = -10.0; s["initial"]["stage_m"] = 0.0
        s["boundaries"]["right"] = {"type":"flather_stage", "baseline_m":0,
            "samples":[[0,0.2],[0.5,0.2]]}
        r = simulate(s)
        o = r["boundary_observations"]
        right = np.asarray(o["side"]) == "right"
        # Zero inside velocity and stage: characteristic construction yields
        # half the external stage and inward q=-sqrt(g*h)*external_stage/2.
        np.testing.assert_allclose(np.asarray(o["initial_ghost_stage_m"])[right], 0.1, atol=1e-12)
        np.testing.assert_allclose(np.asarray(o["initial_ghost_normal_momentum_m2_s"])[right],
                                   -np.sqrt(9.81*10)*0.1, atol=1e-12)
        self.assertGreaterEqual(o["boundary_update_count"], 2*r["diagnostics"]["steps"])

    def test_explicit_mixed_forced_boundary_preserves_still_water_on_dry_headland(self):
        s = scenario(nx=8, ny=4)
        s["terrain"]["bed_m"] = np.tile(np.linspace(-2,2,5)[:,None], (1,9)).tolist()
        s["initial"]["stage_m"] = 0.0
        s["boundaries"]["right"] = {"type":"flather_stage", "baseline_m":0,
            "dry_edges":"reflective", "samples":[[0,0],[0.5,0]]}
        r = simulate(s)
        np.testing.assert_allclose(r["max_depth_m"], r["frames"][0]["depth_m"], atol=1e-10)
        self.assertLess(abs(r["diagnostics"]["volume_change_m3"]),1e-10)
        o=r["boundary_observations"]; right=np.asarray(o["side"])=="right"
        self.assertGreater(np.asarray(o["closed_cut"])[right].sum(),0)
        self.assertGreater((~np.asarray(o["closed_cut"],dtype=bool)[right]).sum(),0)

    def test_open_still_water_mixed_land_sea_boundary_keeps_lake_at_rest(self):
        s = scenario(nx=20, ny=2)
        s["terrain"]["bed_m"] = np.tile(np.linspace(1,-1,21),(3,1)).tolist()
        s["initial"]["stage_m"] = 0.0
        s["duration_s"] = 8.0; s["output_interval_s"] = 2.0
        s["boundaries"] = {side:{"type":"open_still_water","baseline_m":0.0} for side in s["boundaries"]}
        r = simulate(s)
        np.testing.assert_allclose(r["max_depth_m"], r["frames"][0]["depth_m"], atol=1e-8)
        self.assertLess(abs(r["diagnostics"]["volume_change_m3"]),1e-8)

    def test_flather_still_water_boundary_preserves_lake_at_rest(self):
        s = scenario(nx=8, ny=2)
        s["terrain"]["bed_m"] = -2.0; s["initial"]["stage_m"] = 0.0
        s["boundaries"]["right"] = {"type": "flather_stage", "baseline_m": 0.0, "samples": [[0,0],[0.5,0]]}
        checkpoints = []
        r = simulate(s, progress=checkpoints.append)
        self.assertEqual(checkpoints[0]["time_s"], 0.0)
        self.assertEqual(checkpoints[-1]["time_s"], s["duration_s"])
        self.assertEqual(checkpoints[-1]["steps"], r["diagnostics"]["steps"])
        np.testing.assert_allclose(r["frames"][-1]["depth_m"], 2.0, atol=1e-10)
        self.assertLess(abs(r["diagnostics"]["volume_change_m3"]), 1e-9)
        np.testing.assert_allclose(r["mesh"]["bed_elevation_m"], -2.0)

    def test_lake_at_rest_over_slope_and_dry_land_remains_still(self):
        s = scenario()
        bed = np.linspace(-1, 2, 41)
        s["terrain"]["bed_m"] = np.tile(bed, (5, 1)).tolist()
        s["initial"]["stage_m"] = 0.0
        r = simulate(s)
        self.assertLess(np.max(np.abs(r["frames"][-1]["xmomentum_m2_s"])), 1e-9)
        self.assertLess(np.max(np.abs(r["frames"][-1]["ymomentum_m2_s"])), 1e-9)
        self.assertLess(abs(r["diagnostics"]["volume_change_m3"]), 1e-9)
        np.testing.assert_allclose(r["frames"][0]["depth_m"], r["frames"][-1]["depth_m"], atol=1e-9)

    def test_closed_domain_wave_moves_and_conserves_water(self):
        s = scenario()
        x = np.linspace(0, 20, 41)
        s["initial"]["stage_m"] = np.tile(1 + 0.1*np.exp(-((x-10)/1.5)**2), (5, 1)).tolist()
        r = simulate(s)
        self.assertGreater(np.max(np.abs(r["frames"][-1]["depth_m"] - r["frames"][0]["depth_m"])), 0.01)
        self.assertLess(abs(r["diagnostics"]["volume_change_m3"]), 1e-8)
        self.assertTrue(np.isfinite(r["max_depth_m"]).all())
        self.assertGreaterEqual(r["frames"][-1]["depth_m"].min(), 0)
        self.assertEqual(r["solver"]["gravity_m_s2"], 9.81)

    def test_dry_dam_break_matches_ritter_and_improves_with_refinement(self):
        errors = []
        for nx in (80, 160):
            s = scenario(nx=nx)
            xnodes = np.linspace(0, 20, nx+1)
            s["initial"]["stage_m"] = np.tile((xnodes < 10).astype(float), (5, 1)).tolist()
            r = simulate(s)
            x = r["mesh"]["centres_m"][:, 0]
            # Ritter's exact dry-bed solution, g=9.81, H=1, dam x=10, time=0.5.
            c = np.sqrt(9.81); xi = (x-10)/0.5
            exact = np.where(xi < -c, 1, np.where(xi > 2*c, 0, (2*c-xi)**2/(9*9.81)))
            error = np.sum(np.abs(r["frames"][-1]["depth_m"]-exact)*r["mesh"]["areas_m2"])/40
            errors.append(error)
            self.assertLess(abs(r["diagnostics"]["volume_change_m3"]), 1e-8)
            self.assertGreater(r["frames"][-1]["depth_m"][x > 11].max(), 0.01)
        self.assertLess(errors[1], 0.025)
        self.assertLess(errors[1], errors[0]*0.9)

    def test_prescribed_wave_changes_volume_and_wets_initially_dry_slope(self):
        s = scenario(nx=60, width=12)
        s["terrain"]["bed_m"] = np.tile(np.linspace(-1, 1, 61), (5, 1)).tolist()
        s["initial"]["stage_m"] = 0.0
        s["duration_s"] = 4.0; s["output_interval_s"] = 1.0
        s["boundaries"]["left"] = {"type": "time_series", "samples": [[0,0,0,0],[0.5,0.6,1,0],[2,0.6,1,0],[4,0,0,0]]}
        r = simulate(s)
        dry = r["initially_dry"]
        self.assertGreater(r["max_depth_m"][dry].max(), 0.01)
        self.assertTrue(np.isfinite(r["arrival_time_s"][dry]).any())
        self.assertGreater(abs(r["diagnostics"]["volume_change_m3"]), 0.01)
        self.assertLess(abs(r["diagnostics"]["mass_balance_error_m3"]),1e-8)
        self.assertGreaterEqual(r["diagnostics"]["minimum_depth_m"], 0)
        observed = np.stack([f["depth_m"] for f in r["frames"]])
        self.assertTrue((r["max_depth_m"] >= observed.max(axis=0)-1e-12).all())
        self.assertTrue((r["arrival_time_s"][dry & np.isfinite(r["arrival_time_s"])] > 0).all())
        for frame in r["frames"]:
            wet = frame["depth_m"] >= s["arrival_depth_m"]
            self.assertTrue((r["arrival_time_s"][wet] <= frame["time_s"]+1e-10).all())


class ExportTest(unittest.TestCase):
    def test_bundle_is_versioned_reproducible_and_never_claims_coastal_validation(self):
        r = simulate(scenario(nx=8, ny=2))
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)/"bundle"
            manifest = write_bundle(r, out)
            saved = json.loads((out/"manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(saved, manifest)
            self.assertEqual(saved["schema"], "pland-tsunami-result-1")
            self.assertFalse(saved["validation"]["real_world_validated"])
            self.assertEqual(saved["solver"]["name"], "ANUGA")
            self.assertEqual(saved["frames"][-1]["time_s"], 0.5)
            self.assertTrue((out/saved["summary"]["file"]).is_file())
            for record in [saved["scenario"], saved["mesh"], saved["summary"], saved["summary_json"], *saved["frames"]]:
                self.assertEqual(hashlib.sha256((out/record["file"]).read_bytes()).hexdigest(), record["sha256"])
            with np.load(out/saved["summary"]["file"]) as arrays:
                np.testing.assert_array_equal(arrays["max_depth_m"], r["max_depth_m"])
            with self.assertRaises(FileExistsError): write_bundle(r, out)

    def test_final_frame_and_projected_mesh_are_preserved(self):
        s = scenario(nx=2, ny=2); s["duration_s"] = 0.6
        s["grid"].update(crs="EPSG:6676", origin_m=[70000.0, -100000.0])
        r = simulate(s)
        self.assertEqual([round(f["time_s"], 9) for f in r["frames"]], [0, 0.25, 0.5, 0.6])
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)/"bundle"; write_bundle(r, out)
            mesh = json.loads((out/"mesh.json").read_text())
            self.assertTrue(np.isfinite(mesh["nodes_lonlat"]).all())
            np.testing.assert_allclose(np.min(mesh["nodes_m"], axis=0), [70000, -100000])


if __name__ == "__main__": unittest.main()
