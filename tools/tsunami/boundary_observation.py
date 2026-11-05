"""Observe the actual boundary states at every ANUGA update, including RK stages.

This hook reads reconstructed inside-edge and native ghost states after boundary
evaluation, before the flux call. It does not modify any state or numerical flux.
"""
import numpy as np


class BoundaryObservation:
    def __init__(self, domain, scenario):
        self.domain = domain
        self.cells = np.asarray(domain.boundary_cells)
        self.edges = np.asarray(domain.boundary_edges)
        self.sides = [domain.boundary[(int(c), int(e))] for c,e in zip(self.cells,self.edges)]
        self.bed = domain.quantities["elevation"].edge_values[self.cells,self.edges].copy()
        self.centroid_bed = domain.quantities["elevation"].centroid_values[self.cells].copy()
        self.normals = domain.normals.reshape(-1,3,2)[self.cells,self.edges]
        self.closed = np.zeros(len(self.cells),dtype=bool)
        self.characteristic_required = np.zeros(len(self.cells),dtype=bool)
        self.transmissive = np.zeros(len(self.cells),dtype=bool)
        for i,side in enumerate(self.sides):
            spec=scenario["boundaries"][side]
            if spec["type"]=="reflective": self.closed[i]=True
            elif spec["type"]=="open_still_water" or spec.get("dry_edges")=="reflective":
                self.closed[i]=not (self.bed[i]<spec["baseline_m"]-.001 and self.centroid_bed[i]<spec["baseline_m"]-.001)
            self.characteristic_required[i]=(not self.closed[i]) and spec["type"] in ("flather_stage","open_still_water")
            self.transmissive[i]=spec['type']=='transmissive'
            if spec['type']=='flather_stage' and spec.get('dry_edges')=='transmissive':
                wet=self.bed[i]<spec['baseline_m']-.001 and self.centroid_bed[i]<spec['baseline_m']-.001
                self.characteristic_required[i]=wet
                self.transmissive[i]=not wet
        self.maximum_inside_depth = np.zeros(len(self.cells))
        self.maximum_ghost_depth = np.zeros(len(self.cells))
        self.maximum_inside_froude = np.zeros(len(self.cells))
        self.maximum_ghost_froude = np.zeros(len(self.cells))
        self.maximum_native_depth_froude = np.zeros(len(self.cells))
        self.count=0
        self.froude_minimum_depth = float(domain.minimum_allowed_height)
        self.initial_ghost_stage = None
        self.initial_ghost_q = None
        self.initial_inside_q = None
        self.solver_bed = None
        self.initial_inside_depth = None
        self.maximum_inward_velocity = np.zeros(len(self.cells))
        self.maximum_outward_velocity = np.zeros(len(self.cells))

    def observe(self):
        d=self.domain; c=self.cells; e=self.edges
        stage=d.quantities["stage"]; hu=d.quantities["xmomentum"]; hv=d.quantities["ymomentum"]
        inside=stage.edge_values[c,e]; ghost=stage.boundary_values
        qi=hu.edge_values[c,e]*self.normals[:,0]+hv.edge_values[c,e]*self.normals[:,1]
        qg=hu.boundary_values*self.normals[:,0]+hv.boundary_values*self.normals[:,1]
        if not all(np.isfinite(v).all() for v in (inside,ghost,qi,qg)):
            raise RuntimeError("nonfinite actual boundary state")
        # DE1 reconstructs a discontinuous elevation at evolution startup.
        # The source-node edge average cached for boundary classification is
        # not the bed subsequently seen by the flux. Read that actual bed now.
        solver_bed=d.quantities["elevation"].edge_values[c,e]
        # ANUGA DE1's boundary flux sets exterior bed equal to inside bed
        # (get_edge_data_central_flux: zr=zl). elevation.boundary_values is
        # not evolved by scalar conserved-value boundary dispatch and can be
        # stale/zero; it is not the bed used by this flux.
        hi=np.maximum(0,inside-solver_bed); hg=np.maximum(0,ghost-solver_bed)
        active=(hi>=self.froude_minimum_depth)&(~self.closed)
        velocity=np.zeros(len(hi));velocity[active]=qi[active]/hi[active]
        np.maximum(self.maximum_inward_velocity,-velocity,out=self.maximum_inward_velocity)
        np.maximum(self.maximum_outward_velocity,velocity,out=self.maximum_outward_velocity)
        np.maximum(self.maximum_inside_depth,hi,out=self.maximum_inside_depth)
        np.maximum(self.maximum_ghost_depth,hg,out=self.maximum_ghost_depth)
        for h,q,out in ((hi,qi,self.maximum_inside_froude),(hg,qg,self.maximum_ghost_froude),
                        (np.maximum(0,inside-self.centroid_bed),qi,self.maximum_native_depth_froude)):
            valid=(h>=self.froude_minimum_depth)&(~self.closed)
            f=np.zeros(len(h)); f[valid]=np.abs(q[valid])/(h[valid]*np.sqrt(d.g*h[valid]))
            np.maximum(out,f,out=out)
        if self.count==0:
            self.initial_ghost_stage=ghost.copy(); self.initial_ghost_q=qg.copy()
            self.initial_inside_q=qi.copy()
            self.solver_bed=solver_bed.copy()
            self.initial_inside_depth=hi.copy()
        self.count+=1

    def result(self):
        return {"schema":"pland-boundary-observations-2",
            "sampling":"every native boundary update including all RK intermediate states and output reconstructions",
            "boundary_update_count":self.count,"froude_minimum_depth_m":self.froude_minimum_depth,
            "side":self.sides,"cell":self.cells.tolist(),"edge":self.edges.tolist(),
            "length_m":self.domain.edgelengths[self.cells,self.edges].tolist(),
            "edge_bed_m":self.bed.tolist(),"closed_cut":self.closed.tolist(),
            "transmissive_edge":self.transmissive.tolist(),
            "initial_inside_normal_momentum_m2_s":self.initial_inside_q.tolist(),
            "characteristic_subcriticality_required":self.characteristic_required.tolist(),
            "initial_inside_edge_depth_m":self.initial_inside_depth.tolist(),
            "maximum_inward_inside_normal_velocity_m_s":self.maximum_inward_velocity.tolist(),
            "maximum_outward_inside_normal_velocity_m_s":self.maximum_outward_velocity.tolist(),
            "solver_reconstructed_edge_bed_m":self.solver_bed.tolist(),
            "ghost_depth_definition":"ghost stage minus current inside solver edge bed, matching DE1 boundary flux zr=zl",
            "maximum_inside_edge_depth_m":self.maximum_inside_depth.tolist(),
            "maximum_ghost_depth_m":self.maximum_ghost_depth.tolist(),
            "maximum_inside_edge_normal_froude":self.maximum_inside_froude.tolist(),
            "maximum_ghost_normal_froude":self.maximum_ghost_froude.tolist(),
            "maximum_native_centroid_depth_normal_froude":self.maximum_native_depth_froude.tolist(),
            "initial_ghost_stage_m":self.initial_ghost_stage.tolist(),
            "initial_ghost_normal_momentum_m2_s":self.initial_ghost_q.tolist()}
