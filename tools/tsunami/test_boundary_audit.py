import unittest
from tools.tsunami.engine import np
from tools.tsunami.boundary_audit import audit_boundaries


def fixture(bed):
    return {"scenario":{"grid":{"nx":1,"ny":1,"origin_m":[0,0],"width_m":1,"height_m":1},
        "terrain":{"bed_m":bed},"boundaries":{k:{"type":"open_still_water","baseline_m":0} for k in ["left","right","top","bottom"]}},
        "mesh":{"nodes_m":np.array([[0,0],[1,0],[1,1],[0,1]]),"triangles":np.array([[0,1,2],[0,2,3]]),"bed_elevation_m":np.array([bed,bed])},
        "max_depth_m":np.array([max(-bed,0),max(-bed,0)],dtype=float),
        "frames":[{"time_s":0,"depth_m":np.array([max(-bed,0),max(-bed,0)],dtype=float),"xmomentum_m2_s":np.zeros(2),"ymomentum_m2_s":np.zeros(2)}]}


class BoundaryAuditTest(unittest.TestCase):
    def test_shallow_but_active_supercritical_open_flow_is_not_filtered_out(self):
        from tools.tsunami.engine import simulate
        from tools.tsunami.test_engine import scenario
        s=scenario(nx=2,ny=2);s["duration_s"]=.01;s["output_interval_s"]=.01
        s["initial"].update(stage_m=.05,xmomentum_m2_s=.1)
        s["boundaries"]={k:{"type":"open_still_water","baseline_m":.05} for k in s["boundaries"]}
        a=audit_boundaries(simulate(s))
        self.assertTrue(a["supercritical_open_boundary_observed"])
        self.assertTrue(a["supercritical_characteristic_boundary_observed"])
        self.assertGreater(a["sides"]["right"]["maximum_inside_edge_normal_froude"],2.8)

    def test_discontinuous_solver_bed_does_not_invent_water_on_dry_steep_land(self):
        from tools.tsunami.engine import simulate
        from tools.tsunami.test_engine import scenario
        s=scenario(nx=8,ny=2)
        s["terrain"]["bed_m"]=(10+np.arange(3)[:,None]*7+np.arange(9)[None,:]*5).tolist()
        s["initial"]["stage_m"]=0
        r=simulate(s);a=audit_boundaries(r)
        self.assertEqual(r["max_depth_m"].max(),0)
        self.assertEqual(a["closed_cut_inundated_length_m"],0)
        self.assertFalse(a["inundation_reaches_closed_cut_boundary"])
        s["boundaries"]={k:{"type":"open_still_water","baseline_m":0} for k in s["boundaries"]}
        self.assertFalse(audit_boundaries(simulate(s))["inundation_reaches_closed_cut_boundary"])

    def test_solver_reconstructed_wall_depth_is_observed_at_all_rk_updates(self):
        from tools.tsunami.engine import simulate
        from tools.tsunami.test_engine import scenario
        r=simulate(scenario(nx=8,ny=2));a=audit_boundaries(r)
        self.assertEqual(a["schema"],"pland-boundary-audit-3")
        self.assertEqual(a["closed_cut_inundated_length_m"],44)
        self.assertAlmostEqual(a["sides"]["right"]["maximum_observed_closed_edge_depth_m"],1)
        self.assertGreaterEqual(a["boundary_update_count"],2*r["diagnostics"]["steps"])

    def test_uniform_supercritical_flow_is_detected_at_actual_open_edges(self):
        from tools.tsunami.engine import simulate
        from tools.tsunami.test_engine import scenario
        s=scenario(nx=8,ny=2);s["initial"]["xmomentum_m2_s"]=4
        s["boundaries"]={k:{"type":"transmissive"} for k in s["boundaries"]}
        r=simulate(s);a=audit_boundaries(r)
        self.assertTrue(a["supercritical_open_boundary_observed"])
        self.assertFalse(a["supercritical_characteristic_boundary_observed"])
        self.assertFalse(a["inundation_reaches_closed_cut_boundary"])
        self.assertAlmostEqual(a["sides"]["right"]["maximum_inside_edge_normal_froude"],4/np.sqrt(9.81))
        self.assertAlmostEqual(a["sides"]["right"]["maximum_ghost_normal_froude"],4/np.sqrt(9.81))
        self.assertAlmostEqual(a["sides"]["right"]["maximum_outward_inside_normal_velocity_m_s"],4)
        self.assertAlmostEqual(a["sides"]["left"]["maximum_inward_inside_normal_velocity_m_s"],4)

    def test_flooding_dry_cut_edges_marks_extent_truncated(self):
        r=fixture(1);r["max_depth_m"][0]=0.2
        a=audit_boundaries(r)
        self.assertTrue(a["inundation_reaches_closed_cut_boundary"])
        self.assertEqual(a["closed_cut_inundated_length_m"],2)
        self.assertEqual(a["sides"]["right"]["maximum_closed_cut_depth_m"],0.2)

    def test_open_boundary_screen_is_explicitly_frame_sampled(self):
        r=fixture(-1);r["frames"][0]["xmomentum_m2_s"][:]=4
        a=audit_boundaries(r)
        self.assertTrue(a["supercritical_open_boundary_observed_in_frames"])
        self.assertFalse(a["inundation_reaches_closed_cut_boundary"])
        self.assertIn("frame",a["froude_sampling"])
        self.assertGreater(a["sides"]["right"]["sampled_maximum_normal_froude"],1)

    def test_wet_cell_below_higher_edge_does_not_mark_edge_reached(self):
        r=fixture(0)
        r["scenario"]["terrain"]["bed_m"]=[[0,4],[0,4]]
        r["mesh"]["bed_elevation_m"]=np.array([8/3,4/3])
        r["max_depth_m"][0]=1
        a=audit_boundaries(r)
        self.assertEqual(a["sides"]["right"]["closed_cut_inundated_length_m"],0)
        self.assertEqual(a["sides"]["right"]["maximum_estimated_closed_edge_depth_m"],0)
        r["max_depth_m"][:]=0
        self.assertFalse(audit_boundaries(r)["inundation_reaches_closed_cut_boundary"])


if __name__=="__main__":unittest.main()
