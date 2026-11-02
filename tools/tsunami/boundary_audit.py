"""Finite-domain screening, never a certification of coastal boundary validity."""
from tools.tsunami.engine import np
from scipy.interpolate import RegularGridInterpolator


def audit_boundaries(result, inundation_threshold_m=0.1, froude_minimum_depth_m=None):
    if "boundary_observations" in result:
        return _observed_audit(result, inundation_threshold_m, froude_minimum_depth_m)
    if froude_minimum_depth_m is None: froude_minimum_depth_m=0.1
    s=result["scenario"]; grid=s["grid"]; mesh=result["mesh"]
    nodes=np.asarray(mesh["nodes_m"]);triangles=np.asarray(mesh["triangles"])
    bed=np.asarray(mesh["bed_elevation_m"]);maximum=np.asarray(result["max_depth_m"])
    origin=np.asarray(grid["origin_m"]);end=origin+[grid["width_m"],grid["height_m"]]
    source=np.asarray(s["terrain"]["bed_m"],dtype=float)
    if source.ndim==0:source=np.full((grid["ny"]+1,grid["nx"]+1),source)
    interp=RegularGridInterpolator((np.linspace(origin[1],end[1],grid["ny"]+1),np.linspace(origin[0],end[0],grid["nx"]+1)),source)
    node_bed=interp(nodes[:,[1,0]])
    sides={}
    for side,axis,coordinate,normal in [("left",0,origin[0],[-1,0]),("right",0,end[0],[1,0]),("bottom",1,origin[1],[0,-1]),("top",1,end[1],[0,1])]:
        cells=[];lengths=[];edge_bed=[]
        for a,b in [(0,1),(1,2),(2,0)]:
            ends=triangles[:,[a,b]]
            hit=np.all(np.isclose(nodes[ends,axis],coordinate,rtol=0,atol=1e-7),axis=1)
            indices=np.flatnonzero(hit);cells.extend(indices.tolist())
            lengths.extend(np.linalg.norm(nodes[ends[hit,1]]-nodes[ends[hit,0]],axis=1).tolist())
            edge_bed.extend(node_bed[ends[hit]].mean(axis=1).tolist())
        cells=np.asarray(cells,dtype=int);lengths=np.asarray(lengths);edge_bed=np.asarray(edge_bed)
        specification=s["boundaries"][side];kind=specification["type"]
        if kind=="open_still_water":
            baseline=specification["baseline_m"]
            opened=(edge_bed<baseline-0.001)&(bed[cells]<baseline-0.001)
        else:opened=np.full(len(cells),kind!="reflective",dtype=bool)
        closed=~opened
        # A wet centroid alone does not establish that a higher boundary edge is wet.
        # This remains a screening estimate: reconstructed edge-stage extrema were
        # not recorded at every solver substep.
        estimated_edge_depth=np.where(maximum[cells]>0,np.maximum(0,maximum[cells]+bed[cells]-edge_bed),0)
        reached=closed&(estimated_edge_depth>=inundation_threshold_m)
        peak_froude=np.zeros(len(cells))
        for frame in result["frames"]:
            h=np.asarray(frame["depth_m"])[cells]
            q=abs(np.asarray(frame["xmomentum_m2_s"])[cells]*normal[0]+np.asarray(frame["ymomentum_m2_s"])[cells]*normal[1])
            valid=opened&(h>=froude_minimum_depth_m)
            values=np.zeros(len(cells));values[valid]=q[valid]/(h[valid]*np.sqrt(9.81*h[valid]))
            np.maximum(peak_froude,values,out=peak_froude)
        sides[side]={"policy":kind,"total_length_m":float(lengths.sum()),"open_length_m":float(lengths[opened].sum()),
            "closed_cut_length_m":float(lengths[closed].sum()),"closed_cut_inundated_length_m":float(lengths[reached].sum()),
            "maximum_closed_cut_depth_m":float(maximum[cells[closed]].max()) if closed.any() else None,
            "maximum_estimated_closed_edge_depth_m":float(estimated_edge_depth[closed].max()) if closed.any() else None,
            "sampled_maximum_normal_froude":float(peak_froude.max()) if len(cells) else 0,
            "sampled_supercritical_open_length_m":float(lengths[opened&(peak_froude>=1)].sum())}
    cut=sum(v["closed_cut_inundated_length_m"] for v in sides.values())
    return {"schema":"pland-boundary-audit-2","sides":sides,
        "cut_reach_sampling":"maximum adjacent-centroid water stage minus boundary edge bed; screening estimate, not recorded edge-stage extrema",
        "inundation_depth_threshold_m":inundation_threshold_m,"closed_cut_inundated_length_m":cut,
        "inundation_reaches_closed_cut_boundary":cut>0,
        "supercritical_open_boundary_observed_in_frames":any(v["sampled_maximum_normal_froude"]>=1 for v in sides.values()),
        "froude_sampling":"output-frame adjacent-centroid screening only; not every solver substep or ghost-boundary value",
        "froude_minimum_depth_m":froude_minimum_depth_m,
        "limitations":["Closed cut edges are computational boundaries, not proven real walls. Reaching them can truncate or reflect inundation.",
                       "Subcritical adjacent-cell frame samples do not prove the native Flather boundary assumptions hold at every instant.",
                       "No complete inundation extent or safe-area certification outside this finite domain."]}


def _observed_audit(result, threshold, minimum_depth):
    o=result["boundary_observations"]
    if minimum_depth is None: minimum_depth=o["froude_minimum_depth_m"]
    if minimum_depth != o["froude_minimum_depth_m"]:
        raise ValueError("recorded boundary Froude threshold cannot be changed after simulation")
    side_names=np.asarray(o["side"]); lengths=np.asarray(o["length_m"])
    closed=np.asarray(o["closed_cut"],dtype=bool)
    characteristic=np.asarray(o.get("characteristic_subcriticality_required",~closed),dtype=bool)
    transmissive=np.asarray(o.get('transmissive_edge',~closed&~characteristic),dtype=bool)
    inside=np.asarray(o["maximum_inside_edge_depth_m"]);ghost=np.asarray(o["maximum_ghost_depth_m"])
    fi=np.asarray(o["maximum_inside_edge_normal_froude"])
    fg=np.asarray(o["maximum_ghost_normal_froude"])
    fn=np.asarray(o["maximum_native_centroid_depth_normal_froude"])
    f=np.maximum(np.maximum(fi,fg),fn);sides={}
    initially_dry=np.asarray(o.get("initial_inside_edge_depth_m",np.zeros(len(closed))))<minimum_depth
    inward=np.asarray(o.get("maximum_inward_inside_normal_velocity_m_s",np.zeros(len(closed))))
    outward=np.asarray(o.get("maximum_outward_inside_normal_velocity_m_s",np.zeros(len(closed))))
    for side in ("left","right","bottom","top"):
        mask=side_names==side; c=mask&closed;opened=mask&~closed
        reached=c&(np.maximum(inside,ghost)>=threshold)
        sides[side]={"policy":result["scenario"]["boundaries"][side]["type"],
            "flather_length_m":float(lengths[mask&characteristic].sum()),
            "transmissive_length_m":float(lengths[mask&transmissive].sum()),
            "total_length_m":float(lengths[mask].sum()),"open_length_m":float(lengths[opened].sum()),
            "closed_cut_length_m":float(lengths[c].sum()),"closed_cut_inundated_length_m":float(lengths[reached].sum()),
            "maximum_observed_closed_edge_depth_m":float(np.maximum(inside,ghost)[c].max()) if c.any() else None,
            "maximum_inside_edge_normal_froude":float(fi[opened].max()) if opened.any() else 0,
            "maximum_ghost_normal_froude":float(fg[opened].max()) if opened.any() else 0,
            "maximum_native_centroid_depth_normal_froude":float(fn[opened].max()) if opened.any() else 0,
            "observed_supercritical_open_length_m":float(lengths[opened&(f>=1)].sum())}
        sides[side].update({
            "characteristic_subcriticality_required":bool(characteristic[mask].any()),
            "supercritical_characteristic_length_m":float(lengths[mask&characteristic&(f>=1)].sum()),
            "open_initially_dry_inundated_length_m":float(lengths[opened&initially_dry&(np.maximum(inside,ghost)>=threshold)].sum()),
            "maximum_inward_inside_normal_velocity_m_s":float(inward[opened].max()) if opened.any() else 0,
            "maximum_outward_inside_normal_velocity_m_s":float(outward[opened].max()) if opened.any() else 0})
    cut=sum(v["closed_cut_inundated_length_m"] for v in sides.values())
    return {"schema":"pland-boundary-audit-3","sides":sides,
        "cut_reach_sampling":o["sampling"],"boundary_update_count":o["boundary_update_count"],
        "inundation_depth_threshold_m":threshold,"closed_cut_inundated_length_m":cut,
        "inundation_reaches_closed_cut_boundary":cut>0,
        "supercritical_open_boundary_observed":bool(np.any((~closed)&(f>=1))),
        "supercritical_characteristic_boundary_observed":bool(np.any(characteristic&(f>=1))),
        "open_initially_dry_inundated_length_m":sum(v["open_initially_dry_inundated_length_m"] for v in sides.values()),
        "froude_sampling":"reconstructed inside edge, native ghost, and native Flather centroid-bed depth at every boundary update",
        "froude_minimum_depth_m":minimum_depth,
        "limitations":["Dry cut boundaries remain computational walls, not verified physical walls; any wetted cut rejects complete extent use.",
            "All RK boundary states observed, but subcriticality alone does not validate incoming source or domain-size independence.",
            "Extrapolating outflow boundaries require independent baseline and ROI boundary/distance sensitivity; they do not require subcritical outflow. Inward state velocity is recorded, not proof of a physically known exterior state.",
            "Complete inundation extent and safe-area certification outside this finite domain are unsupported."]}
