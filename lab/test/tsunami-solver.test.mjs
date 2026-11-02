import test from 'node:test';
import assert from 'node:assert/strict';
import { createSimulation, simulate, calibrateHeight } from '../web/tsunami-solver.mjs';

test('irregular partly dry lake stays at rest and conserves mass', () => {
  const nx = 31, ny = 23, bed = Array.from({ length: nx * ny }, (_, i) => -.6 + .9 * Math.sin(i % nx / 5) * Math.cos(Math.floor(i / nx) / 4));
  const r = simulate({ nx, ny, dx: 2, dy: 3, bed, duration_s: 20 });
  assert.ok(r.diagnostics.maximum_depth_change_m < 1e-12);
  assert.ok(r.diagnostics.relative_mass_residual < 1e-13);
  assert.ok(r.xMomentum.every(v => Math.abs(v) < 1e-12));
  assert.ok(r.yMomentum.every(v => Math.abs(v) < 1e-12));
  assert.ok(r.depth.every(v => v >= 0));
});

function damBreak(nx) {
  const dx = 100 / nx, t = 2, c = Math.sqrt(9.81);
  const r = simulate({ nx, ny: 1, dx, dy: 100, bed: 0, initial_stage_m: Array.from({ length: nx }, (_, i) => i < nx / 2 ? 1 : 0), manning_n: 0, duration_s: t });
  let l1 = 0;
  for (let i = 0; i < nx; i++) {
    const x = (i + .5) * dx - 50;
    const exact = x < -c * t ? 1 : x > 2 * c * t ? 0 : (2 * c - x / t) ** 2 / (9 * 9.81);
    l1 += Math.abs(r.depth[i] - exact) * dx / 100;
  }
  return { r, l1 };
}
test('dry-bed Ritter dam break approaches analytic solution under refinement', () => {
  const coarse = damBreak(100), fine = damBreak(400);
  assert.ok(fine.l1 < coarse.l1 * .65, `${fine.l1} versus ${coarse.l1}`);
  assert.ok(fine.l1 < .009, `fine L1 ${fine.l1}`);
  assert.ok(fine.r.diagnostics.relative_mass_residual < 1e-12);
  assert.ok(fine.r.depth.every(v => v >= 0));
});

function waveConfig(nx = 100) {
  return { nx, ny: 1, dx: 1000 / nx, dy: 1000, bed: -10, baseline_m: 0, duration_s: 160, manning_n: 0,
    reference_index: Math.floor(nx / 2), boundaries: { left: { type: 'flather_stage', amplitude_m: .5, period_s: 80 }, right: { type: 'open_still_water' } } };
}
test('forced open channel accounts for signed boundary mass flux', () => {
  const r = simulate(waveConfig());
  assert.ok(r.actualGaugeHeight > .1);
  assert.ok(Math.abs(r.diagnostics.net_boundary_volume_m3) > 1);
  assert.ok(r.diagnostics.relative_mass_residual < 1e-12);
  assert.equal(r.boundaryAudit.supercritical_open_boundary, false);
});

test('positive Manning friction dissipates uniform momentum without removing water', () => {
  const config = { nx: 1, ny: 1, dx: 1e8, dy: 1e8, bed: 0, initial_stage_m: 2, initial_xmomentum: 2, duration_s: 1, max_dt_s: 1, manning_n: .1 };
  const r = simulate(config);
  assert.ok(r.xMomentum[0] > 0 && r.xMomentum[0] < 2);
  assert.equal(r.depth[0], 2);
});

test('closed computational cut reached by flow is recorded at solver steps', () => {
  const r = simulate({ nx: 40, ny: 1, dx: 1, dy: 100, bed: 0, initial_stage_m: Array.from({ length: 40 }, (_, i) => i < 20 ? 1 : 0), duration_s: 15, manning_n: 0 });
  assert.equal(r.boundaryAudit.inundation_reaches_closed_cut_boundary, true);
  assert.ok(r.boundaryAudit.sides.right.closed_cut_inundated_length_m > 0);
});

test('high source edge cannot hide inundation reaching the actual numerical reflective face', () => {
  const r = simulate({ nx: 40, ny: 1, dx: 1, dy: 100, bed: 0,
    initial_stage_m: Array.from({ length: 40 }, (_, i) => i < 20 ? 1 : 0),
    boundary_bed: { right: [100] }, duration_s: 15, manning_n: 0 });
  assert.equal(r.boundaryAudit.sides.right.maximum_source_edge_screen_depth_m, 0);
  assert.ok(r.boundaryAudit.sides.right.maximum_closed_cut_face_depth_m >= .1);
  assert.equal(r.boundaryAudit.sides.right.closed_cut_inundated_length_m, 100);
  assert.equal(r.boundaryAudit.inundation_reaches_closed_cut_boundary, true);
});

test('stationary water beside a closed cut does not count as wave arrival', () => {
  const r = simulate({ nx: 4, ny: 4, dx: 1, dy: 1, bed: -2, duration_s: 3, boundary_bed: { right: [100, 100, 100, 100] } });
  assert.equal(r.boundaryAudit.inundation_reaches_closed_cut_boundary, false);
  assert.equal(r.boundaryAudit.sides.right.maximum_closed_cut_face_depth_m, 2);
});

test('strict Flather rejects dry boundary; mixed policy preserves dry-edge walls', () => {
  const c = { nx: 2, ny: 2, dx: 10, dy: 10, bed: [-10, -10, 5, 5], duration_s: 1, boundaries: { right: { type: 'flather_stage' } } };
  assert.throws(() => simulate(c), /wet edges/);
  c.boundaries.right.dry_edges = 'reflective';
  const r = simulate(c);
  assert.equal(r.boundaryAudit.sides.right.open_length_m, 10);
  assert.equal(r.boundaryAudit.sides.right.closed_cut_length_m, 10);
  assert.ok(r.diagnostics.maximum_depth_change_m < 1e-12);
});

function calibrationConfig() {
  // Shore-parallel sides are open, so artificial wall disturbance cannot reject
  // this channel calibration benchmark. The genuine finite-domain guard is
  // tested independently above and below.
  const c = waveConfig(40);
  c.boundaries.bottom = c.boundaries.top = { type: 'open_still_water' };
  c.calibration = { maximum_forcing_m: 4, tolerance_m: .002, max_iterations: 14, pulse_duration_s: 80, pulse_delay_s: 0, boundary: 'left' };
  return c;
}
test('height calibration runs a zero baseline and solves target independently of forcing', () => {
  const out = calibrateHeight(calibrationConfig(), .2);
  assert.equal(out.status, 'accepted', out.reason);
  assert.ok(Math.abs(out.result.actualGaugeHeight - .2) <= .002);
  assert.equal(out.calibration.runs[0].forcing_amplitude_m, 0);
  assert.ok(Math.abs(out.calibration.forcing_amplitude_m - .2) > .01);
});
test('zero height is the verified stationary baseline, not an empty placeholder', () => {
  const out = calibrateHeight(calibrationConfig(), 0);
  assert.equal(out.status, 'accepted');
  assert.equal(out.calibration.runs.length, 1);
  assert.equal(out.result.maxDepth[0], 10);
  assert.equal(out.result.actualGaugeHeight, 0);
});
test('invalid/unreachable targets and unverified datum are not clamped or accepted', () => {
  assert.throws(() => calibrateHeight(calibrationConfig(), -1), /target coastal/);
  const c = calibrationConfig(); c.calibration.maximum_forcing_m = .01;
  assert.equal(calibrateHeight(c, .2).reason, 'target_outside_forcing_bracket');
  c.datum_verified = false;
  assert.throws(() => calibrateHeight(c, .2), /datum/);
});
test('matching coastal height is rejected when computational cuts affect the result', () => {
  const c = calibrationConfig(); c.boundaries.top = { type: 'reflective' };
  const out = calibrateHeight(c, .2);
  assert.equal(out.status, 'rejected');
  assert.equal(out.reason, 'inundation_reaches_closed_cut_boundary');
});
test('stepper completion, step limit and cancellation are explicit', () => {
  const c = { nx: 2, ny: 2, dx: 1, dy: 1, bed: -1, duration_s: 1 };
  const s = createSimulation(c); while (s.step()); assert.equal(s.result().diagnostics.completed, true);
  assert.throws(() => simulate({ ...c, max_steps: 1 }), /Maximum solver steps/);
  assert.throws(() => simulate(c, { isCancelled: () => true }), /Cancelled/);
});

test('two-dimensional radial wet/dry collapse conserves mass and transpose symmetry', () => {
  const n = 32, stage = Array.from({ length: n * n }, (_, i) => Math.hypot(i % n - 15.5, Math.floor(i / n) - 15.5) < 5 ? 1 : 0);
  const r = simulate({ nx: n, ny: n, dx: 1, dy: 1, bed: 0, initial_stage_m: stage, duration_s: 2, manning_n: 0 });
  assert.ok(r.diagnostics.relative_mass_residual < 1e-12);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
    assert.ok(Math.abs(r.depth[y * n + x] - r.depth[x * n + y]) < 1e-12);
    assert.ok(Math.abs(r.xMomentum[y * n + x] - r.yMomentum[x * n + y]) < 1e-12);
    assert.ok(r.depth[y * n + x] >= 0);
  }
});

function beach(nx) {
  const dx = 600 / nx, bed = [], stage = [], momentum = [];
  for (let i = 0; i < nx; i++) {
    const x = (i + .5) * dx, z = Math.max(-5, .025 * (x - 400)), a = z < 0 ? .5 * Math.exp(-(((x - 150) / 40) ** 2)) : 0;
    bed.push(z); stage.push(a); momentum.push(a * Math.sqrt(9.81 * Math.max(0, a - z)));
  }
  const r = simulate({ nx, ny: 1, dx, dy: dx, bed, initial_stage_m: stage, initial_xmomentum: momentum, duration_s: 65, manning_n: 0 });
  return { r, runup: Math.max(0, ...bed.filter((z, i) => z >= 0 && r.maxDepth[i] >= .01)) };
}
test('sloping beach runup is resolved and approaches independent ANUGA benchmark', () => {
  const coarse = beach(60), fine = beach(240);
  // Independent ANUGA DE1 values are generated by compare_browser_solver.py.
  // This 0.4m bound is only a synthetic software criterion, not site accuracy.
  assert.ok(Math.abs(fine.runup - 2.21875) < .4, `runup ${fine.runup}`);
  assert.ok(fine.runup > coarse.runup);
  assert.ok(fine.r.diagnostics.relative_mass_residual < 1e-12);
});

test('null, text and missing numerical cells are not coerced into terrain', () => {
  for (const bed of [[null], ['0'], [undefined]]) assert.throws(() => simulate({ nx: 1, ny: 1, dx: 1, dy: 1, bed }), /Invalid bed/);
  assert.throws(() => simulate({ nx: 1, ny: 1, dx: 1, dy: 1, bed: -1, boundaries: { left: { type: 'flather_stage', dry_edges: 'guess' } } }), /dry edge policy/);
});

test('small arbitrary target tightens configured tolerance and zero remains supported', () => {
  const c = calibrationConfig(); c.calibration.tolerance_m = .05;
  assert.equal(calibrateHeight(c, 0).status, 'accepted');
  const r = calibrateHeight(c, .01);
  assert.equal(r.status, 'accepted');
  assert.ok(Math.abs(r.calibration.residual_m) <= .0025);
});

test('transmissive boundaries preserve a sloping irregular partly dry lake at rest', () => {
  const nx = 31, ny = 23, bed = Array.from({ length: nx * ny }, (_, i) => -.6 + .9 * Math.sin(i % nx / 5) * Math.cos(Math.floor(i / nx) / 4));
  const r = simulate({ nx, ny, dx: 2, dy: 3, bed, duration_s: 20,
    boundaries: Object.fromEntries(['left', 'right', 'top', 'bottom'].map(side => [side, { type: 'transmissive' }])) });
  assert.ok(r.diagnostics.maximum_depth_change_m < 1e-12);
  assert.ok(r.diagnostics.relative_mass_residual < 1e-13);
  assert.equal(r.boundaryAudit.inundation_continues_beyond_domain, false);
  assert.equal(r.boundaryAudit.inundation_reaches_closed_cut_boundary, false);
  assert.equal(r.boundaryAudit.boundary_review_required, true);
});

test('transmissive boundary flux records both signed inflow and outflow, not only their net', () => {
  const r = simulate({ nx: 8, ny: 1, dx: 1, dy: 100, bed: -2, initial_xmomentum: 2, manning_n: 0, duration_s: 1,
    boundaries: { left: { type: 'transmissive' }, right: { type: 'transmissive' } } });
  assert.ok(Math.abs(r.boundaryAudit.sides.left.incoming_boundary_volume_m3 - 200) < 1e-9);
  assert.ok(Math.abs(r.boundaryAudit.sides.right.outgoing_boundary_volume_m3 - 200) < 1e-9);
  assert.equal(r.boundaryAudit.sides.left.outgoing_boundary_volume_m3, 0);
  assert.equal(r.boundaryAudit.sides.right.incoming_boundary_volume_m3, 0);
  assert.ok(Math.abs(r.diagnostics.net_boundary_volume_m3) < 1e-10);
  assert.ok(r.depth.every(h => Math.abs(h - 2) < 1e-12));
});

test('a wetting front can exit an initially dry transmissive edge and is reported as continued extent', () => {
  const c = { nx: 40, ny: 1, dx: 1, dy: 100, bed: 0, initial_stage_m: Array.from({ length: 40 }, (_, i) => i < 20 ? 1 : 0), duration_s: 15, manning_n: 0 };
  const wall = simulate(c), open = simulate({ ...c, boundaries: { right: { type: 'transmissive' } } });
  assert.ok(open.boundaryAudit.sides.right.outgoing_boundary_volume_m3 > 100);
  assert.equal(open.boundaryAudit.sides.right.closed_cut_length_m, 0);
  assert.equal(open.boundaryAudit.sides.right.closed_cut_inundated_length_m, 0);
  assert.equal(open.boundaryAudit.sides.right.wet_ground_outlet_length_m, 100);
  assert.equal(open.boundaryAudit.inundation_continues_beyond_domain, true);
  assert.equal(open.boundaryAudit.global_inundation_extent_complete, false);
  assert.equal(open.boundaryAudit.sides.right.froude_guard_applies, false);
  assert.equal(wall.boundaryAudit.sides.right.outgoing_boundary_volume_m3, 0);
  assert.ok(open.diagnostics.final_volume_m3 < wall.diagnostics.final_volume_m3);
  assert.ok(open.diagnostics.relative_mass_residual < 1e-12);
  const sides = Object.values(open.boundaryAudit.sides);
  const net = sides.reduce((sum, s) => sum + s.incoming_boundary_volume_m3 - s.outgoing_boundary_volume_m3, 0);
  assert.ok(Math.abs(net - open.diagnostics.net_boundary_volume_m3) < 1e-9);
});

test('small outgoing wave has much less reflected energy at extrapolation outlet than at a wall', () => {
  const nx = 240, dx = .5;
  const eta = Array.from({ length: nx }, (_, i) => .01 * Math.exp(-((((i + .5) * dx - 40) / 6) ** 2)));
  const c = { nx, ny: 1, dx, dy: 1000, bed: -1, initial_stage_m: eta,
    initial_xmomentum: eta.map(x => x * Math.sqrt(9.81)), manning_n: 0, duration_s: 40 };
  const wall = simulate(c), outlet = simulate({ ...c, boundaries: { right: { type: 'transmissive' } } });
  const residualEnergy = r => r.depth.reduce((sum, h, i) => i > 40 && i < 200 ? sum + (h - 1) ** 2 : sum, 0);
  assert.ok(residualEnergy(outlet) < .1 * residualEnergy(wall));
  assert.ok(outlet.diagnostics.relative_mass_residual < 1e-12);
  assert.ok(outlet.boundaryAudit.sides.right.outgoing_boundary_volume_m3 > 0);
});

test('mixed Flather/dry-outflow keeps a partly dry lake balanced and classifies edge and cell beds', () => {
  const r = simulate({ nx: 2, ny: 3, dx: 10, dy: 10, bed: [-2, -2, 2, 2, -2, -2], duration_s: 5,
    boundary_bed: { right: [-2, 2, 5] }, boundaries: { right: { type: 'flather_stage', dry_edges: 'transmissive' } } });
  const side = r.boundaryAudit.sides.right;
  assert.equal(side.characteristic_length_m, 10);
  assert.equal(side.forced_flather_length_m, 10);
  assert.equal(side.transmissive_length_m, 20);
  assert.equal(side.closed_cut_length_m, 0);
  assert.equal(side.maximum_characteristic_normal_froude, 0);
  assert.equal(r.boundaryAudit.boundary_review_required, true);
  assert.ok(r.diagnostics.maximum_depth_change_m < 1e-12);
  assert.ok(r.diagnostics.relative_mass_residual < 1e-12);
});

test('the dry part of a mixed inlet is not inundated by prescribing an external pulse', () => {
  const r = simulate({ nx: 2, ny: 2, dx: 10, dy: 10, bed: 5, duration_s: 10,
    boundaries: { right: { type: 'flather_stage', dry_edges: 'transmissive', amplitude_m: 50, period_s: 5 } } });
  assert.ok(r.maxDepth.every(x => x === 0));
  assert.equal(r.boundaryAudit.sides.right.forced_flather_length_m, 0);
  assert.equal(r.boundaryAudit.sides.right.transmissive_length_m, 20);
  assert.equal(r.boundaryAudit.sides.right.incoming_boundary_volume_m3, 0);
});

test('a mixed initially dry inlet segment passes an outgoing wetting front exactly like transmissive', () => {
  const c = { nx: 40, ny: 1, dx: 1, dy: 100, bed: 0, initial_stage_m: Array.from({ length: 40 }, (_, i) => i < 20 ? 1 : 0), duration_s: 15, manning_n: 0 };
  const normal = simulate({ ...c, boundaries: { right: { type: 'transmissive' } } });
  const mixed = simulate({ ...c, boundaries: { right: { type: 'flather_stage', dry_edges: 'transmissive', amplitude_m: 20 } } });
  assert.deepEqual(mixed.depth, normal.depth);
  assert.deepEqual(mixed.maxDepth, normal.maxDepth);
  assert.equal(mixed.boundaryAudit.sides.right.wet_ground_outlet_length_m, 100);
  assert.equal(mixed.boundaryAudit.sides.right.outgoing_boundary_volume_m3, normal.boundaryAudit.sides.right.outgoing_boundary_volume_m3);
  assert.equal(mixed.boundaryAudit.sides.right.froude_guard_applies, false);
});

test('fully wet Flather forcing is unchanged by opting into the unused dry-outflow policy', () => {
  const c = waveConfig(40), regular = simulate(c);
  const mixed = simulate({ ...c, boundaries: { ...c.boundaries, left: { ...c.boundaries.left, dry_edges: 'transmissive' } } });
  assert.deepEqual(mixed.maxDepth, regular.maxDepth);
  assert.deepEqual(mixed.depth, regular.depth);
  assert.equal(mixed.boundaryAudit.sides.left.transmissive_length_m, 0);
  assert.equal(mixed.boundaryAudit.sides.left.maximum_characteristic_normal_froude, regular.boundaryAudit.sides.left.maximum_characteristic_normal_froude);
  assert.throws(() => simulate({ ...c, boundaries: { left: { type: 'reflective', dry_edges: 'transmissive' } } }), /only on flather_stage/);
});
