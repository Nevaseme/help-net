# Experimental browser shallow-water solver

`lab/web/tsunami-solver.mjs` integrates the nonlinear, depth-averaged shallow-water equations on rectangular cells. It runs locally inside `tsunami-solver-worker.mjs`; the existing static host supplies the terrain and program. It needs no paid compute service and no always-on personal computer. A phone still spends its own processor time on the calculation.

This is a numerical scenario tool, not an observed-tsunami-validated hazard product. A returned `accepted` status means the requested coastal crest was reached within tolerance and the listed numerical/domain checks passed. It does not certify physical accuracy. The separate terrain review and publication gate remains necessary.

## Discretization

The transport operator uses limited MUSCL reconstruction of depth, water-surface elevation, and both velocity components. The generalized minmod limiter has parameter 1.3. Reconstructed bed is reconstructed stage minus reconstructed depth. Each interface then applies the nonnegative hydrostatic reconstruction and an HLL flux; the two cells share exactly the same water flux. Side-specific hydrostatic pressure corrections and the centered within-cell bed source balance a lake at rest. Time integration is SSPRK2. Reconstructed half cells use half the configured CFL, whose maximum is 0.45. If the second RK stage needs a smaller time step, both stages are repeated and discarded-stage diagnostics are restored.

Manning momentum dissipation is semi-implicit, applied after transport with denominator `1 + dt*g*n²*|q|/h^(7/3)`. Water is not removed by friction. The transport method is second order in smooth wet regions; limiting, wetting/drying, and the first-order friction splitting reduce the effective accuracy. No second-order convergence claim is made for moving shoreline locations.

References:

- [Audusse et al., hydrostatic reconstruction](https://www.math.univ-paris13.fr/~audusse/articles/hydro.pdf): nonnegative depths, well-balanced source treatment, and higher-order extension.
- [Delestre et al., limitation of hydrostatic reconstruction](https://arxiv.org/abs/1206.4986): slope, cell size, and water depth can combine to produce inaccurate flow. A finer grid and real-site comparison are required; the method name does not establish accuracy.
- Flather boundary characteristic combination was checked against the scalar implementation in the installed ANUGA 3.3.10 source. Browser and ANUGA spatial and time discretizations remain different.

## Boundaries and requested height

The datum and initial still-water level are explicit. `flather_stage` combines the outgoing interior characteristic with an incoming external-stage characteristic and zero external velocity. It assumes subcritical flow. Both interior and ghost normal Froude numbers are checked at RK evaluation stages. Any observed value at least 1 rejects the final scenario.

`flather_stage` requires originally wet edges and adjacent cells. An explicit `dry_edges: 'reflective'` enables mixed sea/land boundaries; originally dry portions stay closed and are audited. `open_still_water` uses the same originally wet classification with no incoming stage anomaly. Reflective computational edges are not assumed to be actual seawalls. Reaching an originally dry numerical cut face at the declared depth threshold rejects the result. A 0.01 m depth disturbance at an originally wet reflective cut also rejects it. The actual boundary flux uses the cell bed because boundary-normal reconstruction slopes are zero; the cut guard therefore uses actual face depth, at the retained first RK stage and final state. The supplied source edge bed is used for initial open/closed classification and an independently reported screening depth. A high source edge must never hide water already reflected at the numerical face. This is finite-domain screening, not a proof of boundary suitability.

The requested number is the maximum modeled rise above initial still water in a declared, originally wet nearshore cell. It is **not** the forcing amplitude or a uniform inundation depth. Calibration:

1. Runs zero forcing and checks maximum depth drift everywhere, with a maximum 0.001 m tolerance.
2. Runs a bounded positive raised-cosine external-stage pulse.
3. Expands a bounded forcing bracket when necessary, then uses safeguarded inverse interpolation. Every candidate is a new simulation.
4. Rejects nonmonotonic response, an unreachable target, exhausted iterations, poor mass balance, invalid open-boundary flow, or reached cuts.

Target zero returns the verified baseline. Small positive targets automatically tighten the configured tolerance. No target range is silently clamped and no flood mask is scaled or interpolated. Fixed period, direction, tide, roughness and bathymetry are conditional assumptions; height alone cannot uniquely specify a real tsunami.

### Optional extrapolation outlets, version 2.2

`type: 'transmissive'` copies the adjacent numerical depth and both momenta to the ghost cell on wet **and** dry ground. This is a separate boundary policy, not Flather and not a disguised reflective cut. [GeoClaw's boundary documentation](https://www.clawpack.org/bc.html#boundary-conditions-for-geoclaw) supports extrapolation where artificial boundaries are sufficiently far from the region of interest and no outside waves enter. It also warns that outgoing oblique 2D waves can reflect imperfectly. This implementation therefore does not assume every domain or every requested height is suitable for extrapolation.

Each side records outgoing and incoming water volumes separately, integrated with both accepted RK flux weights. These are signed-flow accounting, not proof that no incoming external wave exists. Initially dry outlet length reached by water is recorded as `wet_ground_outlet_length_m`, with `inundation_continues_beyond_domain: true` when appropriate. The output never claims a globally complete inundation extent. Froude numbers on extrapolation outlets remain diagnostic; the subcritical rejection applies specifically to Flather characteristic boundaries. Actual reflective cuts elsewhere continue to be screened without exemption.

Any extrapolation boundary sets `boundary_review_required: true`. Numerical calibration acceptance alone cannot approve a real-site dataset. The displayed central region must be buffered from the artificial outlet, and independent solver, boundary-condition and domain-size sensitivity comparisons must support that region. A stationary partly dry sloping lake, a moving wet/dry outlet front, signed mass flux, and a 1D outgoing-wave reflection comparison are covered by tests. They do not prove adequate performance for oblique real-site flows.

### Optional mixed forced-sea/dry-ground outlet, version 2.3

`{ type: 'flather_stage', dry_edges: 'transmissive' }` fixes a per-edge classification at initialization. Only edges with both source-edge bed and adjacent cell bed below `baseline - 0.001m` receive the unchanged Flather external-stage characteristic. All other edges copy depth and momenta outward using the already-tested transmissive policy. The new option neither imposes the external wave on originally dry land nor inserts a reflecting wall there. The existing strict all-wet default and `dry_edges: 'reflective'` policy remain available and unchanged.

The audit reports forced Flather length and transmissive length separately, with separate maximum characteristic and transmissive Froude numbers. Wetting-front outflow, signed boundary volumes, and required external/domain review use the same rules as full transmissive sides. Added coastline length can change the forced wet length and must be documented; this option is not a device for hiding boundary reach. A dry outlet still assumes no incoming exterior wave, and independent numerical and domain-buffer sensitivity checks remain mandatory before publishing a new dataset.

Version 2.2 source was preserved at `.implementation/solver2.2/tsunami-solver.mjs`, SHA-256 `6d4d60c7df1e924ec786e7f9036e65bac870777ff8b3ea3a4c65b6f74e2a9a20`. Version 2.3 changes only the optional boundary dispatch and its audit metadata; it does not alter the finite-volume flux, reconstruction, integration, friction, or calibration algorithms.

## Reproduction and evidence

Run `node --test lab/test/tsunami-solver.test.mjs` for still-water wet/dry balance, mass conservation with boundary fluxes, Ritter's analytic dry-bed dam break and refinement, friction, 2D radial wetting/drying with transpose symmetry, planar runup, calibration, rejected cuts, invalid input, bounded execution and cancellation.

Run `python -m tools.tsunami.compare_browser_solver` for an independent ANUGA DE1 comparison. Its synthetic beach is `z=max(-5,0.025*(x-400))`, initially still water plus a 0.5 m Gaussian right-going pulse centered at x=150 m. Both solvers receive the same continuous functions on their respective meshes, zero friction, reflective boundaries and 65 seconds. The comparison also includes a circular stage perturbation on a 2D oblique plane. Output is `.implementation/tsunami-browser-comparison.json`.

The beach comparison before the final diagnostic-only changes gave:

| Cell width | Mean absolute final-depth difference | JS maximum runup elevation, 0.01m depth threshold | ANUGA runup elevation |
| --- | --- | --- | --- |
| 10 m | 0.006589 m | 1.375 m | 1.625 m |
| 5 m | 0.002817 m | 1.6875 m | 1.9375 m |
| 2.5 m | 0.001297 m | 1.90625 m | 2.21875 m |

Water-volume residuals were below 1e-12 relative. The roughly 14% finest-grid runup-elevation difference at a 0.01m threshold is material; the decreasing mean depth difference does not remove it. Threshold sensitivity is included in the generated evidence. This is a software comparison, not tsunami observation validation or acceptance of a real site's 20m terrain grid.

At the finest 2.5m grid, increasing the wet-depth threshold to 0.05m gives JS/ANUGA runup elevations 1.78125/1.63542m; at 0.1m they are 1.28125/1.19792m. The extreme thin-film front is substantially more sensitive than a fixed 0.1m inundation contour. On the fully wet 2D oblique-plane benchmark, the mean/L-infinity depth differences decrease from 0.001279/0.005035m at 10m to 0.000444/0.002626m at 5m. Relative mass residual remains below 3e-15.

`node tools/tsunami/review_browser_domain.mjs domain.json 0 3` records actual site scenario trials, maximum-depth fields, boundary checks and timing. Browser timing fields are wall-clock time; browser CPU time is unavailable and is reported as null. The local Node/Python review harness records process CPU time separately. Do not describe wall-clock duration as CPU time.

Worker cancellation terminates the dedicated worker immediately; the owner discards stale request IDs. Progress carries solver time, steps, candidate forcing and completed coastal-crest measurements. No cancelled calculation yields a reusable result.
