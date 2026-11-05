/** Experimental Cartesian finite-volume shallow-water solver.
 * Audusse et al. (2004), hydrostatic reconstruction, HLL flux,
 * limited MUSCL depth/stage/velocity, SSPRK2, semi-implicit Manning friction.
 * https://www.math.univ-paris13.fr/~audusse/articles/hydro.pdf
 * Known slope/grid/depth limitation: https://arxiv.org/abs/1206.4986
 * Flather: linear incoming/outgoing characteristics, zero external velocity;
 * same characteristic combination as ANUGA 3.3.10 (not identical discretization).
 * Calibration is NOT validation against a real tsunami. No dispersive waves,
 * buildings, erosion, tide evolution or earthquake source reconstruction.
 */
export const SOLVER_VERSION = 'pland-cartesian-swe-2.3';
const G = 9.81, DRY = 1e-8;
const SIDES = ['left', 'right', 'bottom', 'top'];
const now = () => globalThis.performance?.now() ?? Date.now();
function requireNumber(value, name, minimum = -Infinity) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < minimum) throw new Error(`Invalid ${name}`);
  return value;
}
function field(value, n, name) {
  if (typeof value === 'number') { requireNumber(value, name); return new Float64Array(n).fill(value); }
  if (!value || value.length !== n) throw new Error(`Invalid ${name} length`);
  for (let i = 0; i < n; i++) requireNumber(value[i], name);
  const result = Float64Array.from(value);
  for (const v of result) requireNumber(v, name);
  return result;
}
export function pulse(t, amplitude, delay, period) {
  return t <= delay || t >= delay + period ? 0 : amplitude * .5 * (1 - Math.cos(2 * Math.PI * (t - delay) / period));
}
export function createSimulation(config) {
  const { nx, ny, dx, dy } = config;
  if (!Number.isInteger(nx) || nx < 1 || !Number.isInteger(ny) || ny < 1 || nx * ny > 1000000) throw new Error('Invalid grid dimensions');
  requireNumber(dx, 'dx', Number.MIN_VALUE); requireNumber(dy, 'dy', Number.MIN_VALUE);
  const n = nx * ny, area = dx * dy, bed = field(config.bed, n, 'bed');
  const baseline = requireNumber(config.baseline_m ?? 0, 'baseline');
  const duration = requireNumber(config.duration_s ?? 900, 'duration', Number.MIN_VALUE);
  const cfl = requireNumber(config.cfl ?? .4, 'CFL', Number.MIN_VALUE);
  if (cfl > .45) throw new Error('CFL must not exceed 0.45');
  const maxDt = requireNumber(config.max_dt_s ?? 2, 'maximum time step', Number.MIN_VALUE);
  const maxSteps = config.max_steps ?? 1000000;
  if (!Number.isInteger(maxSteps) || maxSteps < 1) throw new Error('Invalid maximum steps');
  const roughness = field(config.manning_n ?? .025, n, 'Manning n');
  if (roughness.some(x => x < 0)) throw new Error('Negative Manning coefficient');
  const stage = field(config.initial_stage_m ?? baseline, n, 'initial stage');
  const h = new Float64Array(n), qx = field(config.initial_xmomentum ?? 0, n, 'initial x momentum'), qy = field(config.initial_ymomentum ?? 0, n, 'initial y momentum');
  for (let i = 0; i < n; i++) { h[i] = Math.max(0, stage[i] - bed[i]); if (h[i] < DRY && (qx[i] !== 0 || qy[i] !== 0)) throw new Error('Momentum in a dry cell'); }
  const initialDepth = h.slice(), maxDepth = h.slice(), dh = new Float64Array(n), dqx = new Float64Array(n), dqy = new Float64Array(n);
  const oldH = new Float64Array(n), oldX = new Float64Array(n), oldY = new Float64Array(n);
  const eta = new Float64Array(n), u = new Float64Array(n), v = new Float64Array(n);
  const active = new Uint8Array(n);
  const sx = Array.from({ length: 4 }, () => new Float64Array(n)), sy = Array.from({ length: 4 }, () => new Float64Array(n));
  function minmod(a, b) { return a * b <= 0 ? 0 : Math.sign(a) * Math.min(1.3 * Math.abs(a), 1.3 * Math.abs(b), .5 * Math.abs(a + b)); }
  function slopes() {
    active.fill(0);
    for (let i = 0; i < n; i++) { eta[i] = h[i] + bed[i]; u[i] = h[i] > DRY ? qx[i] / h[i] : 0; v[i] = h[i] > DRY ? qy[i] / h[i] : 0; }
    for (let i = 0; i < n; i++) if (h[i] > 0) {
      active[i] = 1;
      if (i % nx > 0) active[i - 1] = 1;
      if (i % nx < nx - 1) active[i + 1] = 1;
      if (i >= nx) active[i - nx] = 1;
      if (i + nx < n) active[i + nx] = 1;
    }
    const fields = [h, eta, u, v];
    for (let k = 0; k < 4; k++) for (let y = 0; y < ny; y++) for (let x = 0; x < nx; x++) {
      const i = y * nx + x, f = fields[k];
      if (!active[i]) { sx[k][i] = 0; sy[k][i] = 0; continue; }
      sx[k][i] = x > 0 && x < nx - 1 ? minmod(f[i] - f[i - 1], f[i + 1] - f[i]) : 0;
      sy[k][i] = y > 0 && y < ny - 1 ? minmod(f[i] - f[i - nx], f[i + nx] - f[i]) : 0;
    }
  }
  const reference = config.reference_index;
  if (reference !== undefined && (!Number.isInteger(reference) || reference < 0 || reference >= n || h[reference] < .001)) throw new Error('Reference cell must be inside and initially wet');
  const boundaryThreshold = requireNumber(config.cut_boundary_threshold_m ?? .1, 'cut depth threshold', Number.MIN_VALUE);
  const boundaries = {}, edges = [];
  for (const side of SIDES) {
    const b = { type: 'reflective', amplitude_m: 0, delay_s: 0, period_s: 600, ...config.boundaries?.[side] };
    if (!['reflective', 'flather_stage', 'open_still_water', 'transmissive'].includes(b.type)) throw new Error(`Unsupported boundary ${b.type}`);
    if (b.dry_edges !== undefined && !['reflective', 'transmissive'].includes(b.dry_edges)) throw new Error('Invalid dry edge policy');
    if (b.dry_edges === 'transmissive' && b.type !== 'flather_stage') throw new Error('Mixed dry-edge outflow is supported only on flather_stage');
    if (b.type === 'transmissive' && b.dry_edges !== undefined) throw new Error('Transmissive boundaries extrapolate wet and dry cells; no dry-edge wall option');
    requireNumber(b.amplitude_m, 'amplitude', 0); requireNumber(b.delay_s, 'pulse delay', 0); requireNumber(b.period_s, 'pulse period', Number.MIN_VALUE);
    if (b.type === 'open_still_water' && b.amplitude_m !== 0) throw new Error('Still water boundary cannot be forced');
    if (b.type === 'transmissive' && b.amplitude_m !== 0) throw new Error('Transmissive boundary cannot prescribe an incoming wave');
    boundaries[side] = b;
    const axis = side === 'left' || side === 'right' ? 0 : 1, size = axis === 0 ? ny : nx;
    const edgeBeds = config.boundary_bed?.[side];
    if (edgeBeds !== undefined && edgeBeds.length !== size) throw new Error('Invalid boundary bed length');
    for (let k = 0; k < size; k++) {
      const i = side === 'left' ? k * nx : side === 'right' ? k * nx + nx - 1 : side === 'bottom' ? k : (ny - 1) * nx + k;
      const z = requireNumber(edgeBeds?.[k] ?? bed[i], 'boundary bed');
      if (b.type === 'flather_stage' && b.dry_edges === undefined && !(bed[i] < baseline - .001 && z < baseline - .001)) throw new Error('Flather boundary requires wet edges or explicit dry_edges policy');
      const characteristic = (b.type === 'flather_stage' || b.type === 'open_still_water') && bed[i] < baseline - .001 && z < baseline - .001;
      const transmissive = b.type === 'transmissive' || (b.type === 'flather_stage' && b.dry_edges === 'transmissive' && !characteristic);
      edges.push({ side, i, axis, sign: side === 'left' || side === 'bottom' ? -1 : 1, z,
        open: transmissive || characteristic, characteristic, transmissive,
        peakFroude: 0, peakDisturbance: 0, peakFaceDepth: 0, peakSourceEdgeDepth: 0, reached: false,
        wetGroundOutlet: false, outwardRate: 0, firstRate: 0, outgoingVolume: 0, incomingVolume: 0 });
    }
  }
  let t = 0, steps = 0, netBoundaryVolume = 0, roundoffVolume = 0, maxDrift = 0, computeMs = 0;
  const initialVolume = h.reduce((s, v) => s + v * area, 0);
  let ax = 0, ay = 0, boundaryRate = 0;
  // One face contributes identical mass flux to both cells. Hydrostatic pressure
  // corrections differ on each side, balancing that cell's bed source term.
  function face(l, r, axis, hl, xl, yl, zl, hr, xr, yr, zr) {
    if (hl === 0 && hr === 0) return 0;
    const ul = hl > DRY ? xl / hl : 0, vl = hl > DRY ? yl / hl : 0;
    const ur = hr > DRY ? xr / hr : 0, vr = hr > DRY ? yr / hr : 0;
    const z = Math.max(zl, zr), a = Math.max(0, hl + zl - z), b = Math.max(0, hr + zr - z);
    const unL = axis === 0 ? ul : vl, unR = axis === 0 ? ur : vr;
    const sm = Math.min(0, unL - Math.sqrt(G * a), unR - Math.sqrt(G * b)), sp = Math.max(0, unL + Math.sqrt(G * a), unR + Math.sqrt(G * b));
    const speed = Math.max(-sm, sp), span = sp - sm;
    if (axis === 0) ax = Math.max(ax, speed); else ay = Math.max(ay, speed);
    const mass = span > 0 ? (sp * a * unL - sm * b * unR + sp * sm * (b - a)) / span : 0;
    const fx = span > 0 ? (sp * a * unL * ul - sm * b * unR * ur + sp * sm * (b * ur - a * ul)) / span : 0;
    const fy = span > 0 ? (sp * a * unL * vl - sm * b * unR * vr + sp * sm * (b * vr - a * vl)) / span : 0;
    const pressure = span > 0 ? .5 * G * (sp * a * a - sm * b * b) / span : 0, inv = axis === 0 ? 1 / dx : 1 / dy;
    const correctionL = pressure + .5 * G * (hl * hl - a * a), correctionR = pressure + .5 * G * (hr * hr - b * b);
    if (l >= 0) { dh[l] -= mass * inv; dqx[l] -= (fx + (axis === 0 ? correctionL : 0)) * inv; dqy[l] -= (fy + (axis === 1 ? correctionL : 0)) * inv; }
    if (r >= 0) { dh[r] += mass * inv; dqx[r] += (fx + (axis === 0 ? correctionR : 0)) * inv; dqy[r] += (fy + (axis === 1 ? correctionR : 0)) * inv; }
    if (l < 0) boundaryRate += mass * (axis === 0 ? dy : dx);
    if (r < 0) boundaryRate -= mass * (axis === 0 ? dy : dx);
    return mass;
  }
  function interiorFace(i, j, axis) {
    if (h[i] === 0 && h[j] === 0) return;
    const s = axis === 0 ? sx : sy;
    const hl = h[i] + .5 * s[0][i], hr = h[j] - .5 * s[0][j];
    face(i, j, axis, hl, hl * (u[i] + .5 * s[2][i]), hl * (v[i] + .5 * s[3][i]), eta[i] + .5 * s[1][i] - hl,
      hr, hr * (u[j] - .5 * s[2][j]), hr * (v[j] - .5 * s[3][j]), eta[j] - .5 * s[1][j] - hr);
  }
  function rhs(at) {
    dh.fill(0); dqx.fill(0); dqy.fill(0); ax = 0; ay = 0; boundaryRate = 0; slopes();
    for (let y = 0; y < ny; y++) for (let x = 0; x < nx; x++) {
      const i = y * nx + x;
      if (x + 1 < nx) interiorFace(i, i + 1, 0);
      if (y + 1 < ny) interiorFace(i, i + nx, 1);
    }
    for (const e of edges) {
      const { i, axis, sign } = e, b = boundaries[e.side];
      let hg = h[i], xg = qx[i], yg = qy[i];
      if (e.transmissive) {
        // Zero-gradient extrapolation of depth and momenta, including dry land.
        // GeoClaw describes this for boundaries far from the region of interest
        // with no incoming waves; it is imperfect for oblique 2D outgoing waves.
        // https://www.clawpack.org/bc.html#boundary-conditions-for-geoclaw
        if (h[i] > DRY) e.peakFroude = Math.max(e.peakFroude, Math.abs(axis === 0 ? qx[i] : qy[i]) / (h[i] * Math.sqrt(G * h[i])));
      }
      else if (!e.open) { if (axis === 0) xg = -xg; else yg = -yg; }
      else if (h[i] > DRY) {
        const normal = sign * (axis === 0 ? qx[i] : qy[i]) / h[i], k = Math.sqrt(G / h[i]);
        const eta = baseline + pulse(at, b.amplitude_m, b.delay_s, b.period_s);
        const incoming = -k * eta, outgoing = normal + k * (h[i] + bed[i]);
        hg = Math.max(0, (outgoing - incoming) / (2 * k) - bed[i]);
        const normalQ = .5 * (outgoing + incoming) * h[i];
        if (axis === 0) { xg = sign * normalQ; yg = normal > 0 ? qy[i] : 0; }
        else { yg = sign * normalQ; xg = normal > 0 ? qx[i] : 0; }
        const fr = Math.abs(normal) / Math.sqrt(G * h[i]);
        const ghostFr = hg > .001 ? Math.abs(normalQ) / (hg * Math.sqrt(G * hg)) : (Math.abs(normalQ) > DRY ? Infinity : 0);
        e.peakFroude = Math.max(e.peakFroude, fr, ghostFr);
      } else { hg = Math.max(0, baseline + pulse(at, b.amplitude_m, b.delay_s, b.period_s) - bed[i]); xg = 0; yg = 0; }
      const mass = sign < 0 ? face(-1, i, axis, hg, xg, yg, bed[i], h[i], qx[i], qy[i], bed[i])
        : face(i, -1, axis, h[i], qx[i], qy[i], bed[i], hg, xg, yg, bed[i]);
      e.outwardRate = sign * mass * (axis === 0 ? dy : dx);
    }
    for (let i = 0; i < n; i++) {
      dqx[i] -= G * h[i] * (sx[1][i] - sx[0][i]) / dx;
      dqy[i] -= G * h[i] * (sy[1][i] - sy[0][i]) / dy;
    }
    // Half-cell reconstruction requires half of the piecewise-constant CFL.
    let dt = Math.min(maxDt, duration - t, .5 * cfl / Math.max(1e-12, ax / dx + ay / dy));
    for (const side of SIDES) if (boundaries[side].amplitude_m > 0) dt = Math.min(dt, boundaries[side].period_s / 128);
    if (!Number.isFinite(dt) || dt < 1e-10) throw new Error('Time step collapsed');
    return dt;
  }
  function euler(dt) {
    for (let i = 0; i < n; i++) {
      let next = h[i] + dt * dh[i];
      if (!Number.isFinite(next) || next < -1e-9) throw new Error('Positivity or finite-state failure');
      if (next < 0) { roundoffVolume -= .5 * next * area; next = 0; }
      h[i] = next; qx[i] += dt * dqx[i]; qy[i] += dt * dqy[i];
      if (next <= DRY) { qx[i] = 0; qy[i] = 0; }
      if (!Number.isFinite(qx[i]) || !Number.isFinite(qy[i])) throw new Error('Non-finite momentum');
    }
  }
  function step() {
    if (t >= duration) return false;
    if (steps >= maxSteps) throw new Error('Maximum solver steps exceeded');
    const started = now(); oldH.set(h); oldX.set(qx); oldY.set(qy);
    const savedRoundoff = roundoffVolume;
    for (const e of edges) e.savedFroude = e.peakFroude;
    let dt = rhs(t), rate1 = boundaryRate;
    for (const e of edges) e.firstRate = e.outwardRate;
    euler(dt);
    let secondLimit = rhs(t + dt);
    // Re-evaluate both RK stages after reducing an unexpectedly restrictive CFL.
    for (let retry = 0; dt > secondLimit * (1 + 1e-12); retry++) {
      if (retry >= 10) throw new Error('RK CFL did not converge');
      dt = Math.min(dt * .8, secondLimit * .9);
      roundoffVolume = savedRoundoff;
      for (const e of edges) e.peakFroude = e.savedFroude;
      h.set(oldH); qx.set(oldX); qy.set(oldY); rhs(t); rate1 = boundaryRate;
      for (const e of edges) e.firstRate = e.outwardRate;
      euler(dt); secondLimit = rhs(t + dt);
    }
    // Record the retained first RK stage only after retry acceptance.
    monitorEdges();
    for (const e of edges) {
      e.outgoingVolume += .5 * dt * (Math.max(0, e.firstRate) + Math.max(0, e.outwardRate));
      e.incomingVolume += .5 * dt * (Math.max(0, -e.firstRate) + Math.max(0, -e.outwardRate));
    }
    const rate2 = boundaryRate; euler(dt);
    for (let i = 0; i < n; i++) {
      h[i] = .5 * (oldH[i] + h[i]); qx[i] = .5 * (oldX[i] + qx[i]); qy[i] = .5 * (oldY[i] + qy[i]);
      if (h[i] <= DRY) { qx[i] = 0; qy[i] = 0; }
      else { const damping = 1 + dt * G * roughness[i] ** 2 * Math.hypot(qx[i], qy[i]) / h[i] ** (7 / 3); qx[i] /= damping; qy[i] /= damping; }
      maxDepth[i] = Math.max(maxDepth[i], h[i]); maxDrift = Math.max(maxDrift, Math.abs(h[i] - initialDepth[i]));
    }
    monitorEdges();
    netBoundaryVolume += dt * .5 * (rate1 + rate2); t += dt; steps++; computeMs += now() - started;
    return t < duration;
  }
  function monitorEdges() {
    for (const e of edges) {
      e.peakDisturbance = Math.max(e.peakDisturbance, Math.abs(h[e.i] - initialDepth[e.i]));
      const edgeDepth = h[e.i] > DRY ? Math.max(0, h[e.i] + bed[e.i] - e.z) : 0;
      e.peakFaceDepth = Math.max(e.peakFaceDepth, h[e.i]);
      e.peakSourceEdgeDepth = Math.max(e.peakSourceEdgeDepth, edgeDepth);
      if (e.transmissive && initialDepth[e.i] < .001 && h[e.i] >= boundaryThreshold) e.wetGroundOutlet = true;
      // Boundary-normal reconstruction slopes are zero: the actual numerical
      // reflective face has cell bed and depth. A higher source edge sample must
      // never hide water that the numerical flux already reflects at this cut.
      if (!e.open && (initialDepth[e.i] < .001 ? h[e.i] >= boundaryThreshold : e.peakDisturbance >= .01)) e.reached = true;
    }
  }
  function result() {
    const finalVolume = h.reduce((s, v) => s + v * area, 0), sides = {};
    for (const side of SIDES) {
      const es = edges.filter(e => e.side === side), length = side === 'left' || side === 'right' ? dy : dx;
      sides[side] = { policy: boundaries[side].type, open_length_m: es.filter(e => e.open).length * length,
        dry_edge_policy: boundaries[side].dry_edges ?? null,
        characteristic_length_m: es.filter(e => e.characteristic).length * length,
        forced_flather_length_m: boundaries[side].type === 'flather_stage' ? es.filter(e => e.characteristic).length * length : 0,
        transmissive_length_m: es.filter(e => e.transmissive).length * length,
        closed_cut_length_m: es.filter(e => !e.open).length * length,
        closed_cut_inundated_length_m: es.filter(e => e.reached).length * length,
        maximum_normal_froude: Math.max(...es.map(e => e.peakFroude)), maximum_closed_cut_disturbance_m: Math.max(0, ...es.filter(e => !e.open).map(e => e.peakDisturbance)),
        maximum_characteristic_normal_froude: Math.max(0, ...es.filter(e => e.characteristic).map(e => e.peakFroude)),
        maximum_transmissive_normal_froude: Math.max(0, ...es.filter(e => e.transmissive).map(e => e.peakFroude)),
        maximum_closed_cut_face_depth_m: Math.max(0, ...es.filter(e => !e.open).map(e => e.peakFaceDepth)),
        maximum_source_edge_screen_depth_m: Math.max(0, ...es.filter(e => !e.open).map(e => e.peakSourceEdgeDepth)),
        wet_ground_outlet_length_m: es.filter(e => e.wetGroundOutlet).length * length,
        outgoing_boundary_volume_m3: es.reduce((s, e) => s + e.outgoingVolume, 0),
        incoming_boundary_volume_m3: es.reduce((s, e) => s + e.incomingVolume, 0),
        froude_guard_applies: es.some(e => e.characteristic) };
    }
    const massResidual = finalVolume - initialVolume - netBoundaryVolume - roundoffVolume;
    return { maxDepth, depth: h, xMomentum: qx, yMomentum: qy,
      actualGaugeHeight: reference === undefined ? null : maxDepth[reference] + bed[reference] - baseline,
      boundaryAudit: { sides, inundation_reaches_closed_cut_boundary: edges.some(e => e.reached),
        closed_cut_inundated_length_m: Object.values(sides).reduce((s, e) => s + e.closed_cut_inundated_length_m, 0),
        supercritical_open_boundary: edges.some(e => e.characteristic && e.peakFroude >= 1),
        froude_guard_scope: 'Flather characteristic boundaries only; transmissive outlet Froude is diagnostic',
        transmissive_boundary_used: edges.some(e => e.transmissive),
        boundary_review_required: edges.some(e => e.transmissive),
        wet_ground_outlet_length_m: Object.values(sides).reduce((s, e) => s + e.wet_ground_outlet_length_m, 0),
        inundation_continues_beyond_domain: edges.some(e => e.wetGroundOutlet),
        global_inundation_extent_complete: false,
        transmissive_assumptions: edges.some(e => e.transmissive) ? 'Outgoing-wave extrapolation on wet/dry ground, with no external incoming waves and boundaries sufficiently far from the displayed region; oblique-wave reflection and domain-size sensitivity require independent review.' : null,
        sampling: 'retained RK stage and final state; actual numerical reflective-face depth and initially-wet depth disturbance; source edge screen reported separately; boundary volumes integrate both accepted RK flux stages', threshold_m: boundaryThreshold },
      diagnostics: { solver: SOLVER_VERSION, steps, simulated_s: t, compute_wall_ms: computeMs, cpu_time_ms: null,
        initial_volume_m3: initialVolume, final_volume_m3: finalVolume, net_boundary_volume_m3: netBoundaryVolume,
        roundoff_volume_m3: roundoffVolume, mass_residual_m3: massResidual,
        relative_mass_residual: Math.abs(massResidual) / Math.max(1, initialVolume, Math.abs(netBoundaryVolume)),
        maximum_depth_change_m: maxDrift, real_world_validated: false, completed: t >= duration } };
  }
  return { step, result, get time() { return t; }, get steps() { return steps; } };
}
export function simulate(config, { onProgress, isCancelled } = {}) {
  const sim = createSimulation(config), started = now();
  do {
    if (isCancelled?.()) throw new Error('Cancelled');
    sim.step();
    if (sim.steps % 50 === 0) onProgress?.({ time_s: sim.time, duration_s: config.duration_s ?? 900, steps: sim.steps });
  } while (sim.time < (config.duration_s ?? 900));
  const result = sim.result(); result.diagnostics.elapsed_wall_ms = now() - started;
  return result;
}
export function calibrateHeight(config, targetHeight, { onProgress, isCancelled } = {}) {
  requireNumber(targetHeight, 'target coastal height', 0);
  const c = config.calibration ?? {}, maximum = requireNumber(c.maximum_forcing_m, 'maximum forcing', Number.MIN_VALUE);
  const configuredTolerance = requireNumber(c.tolerance_m, 'height tolerance', Number.MIN_VALUE);
  const tolerance = Math.min(configuredTolerance, targetHeight === 0 ? .001 : targetHeight / 4);
  if (tolerance === 0) throw new Error('Requested height is below representable calibration precision');
  if (!Number.isInteger(c.max_iterations) || c.max_iterations < 1 || c.max_iterations > 20) throw new Error('Invalid calibration iteration limit');
  if (!SIDES.includes(c.boundary)) throw new Error('Missing forcing boundary');
  const period = requireNumber(c.pulse_duration_s, 'pulse duration', Number.MIN_VALUE), delay = requireNumber(c.pulse_delay_s ?? 0, 'pulse delay', 0);
  if (period + delay > (config.duration_s ?? 900)) throw new Error('Pulse exceeds simulation duration');
  if (config.reference_index === undefined) throw new Error('Missing coastal reference cell');
  if (config.initial_stage_m !== undefined || config.initial_xmomentum !== undefined || config.initial_ymomentum !== undefined) throw new Error('Calibration requires baseline still water');
  if (config.datum_verified === false) throw new Error('Terrain vertical datum is unverified');
  for (const side of SIDES) if (side !== c.boundary && (config.boundaries?.[side]?.amplitude_m ?? 0) !== 0) throw new Error('Only one forced side is supported');
  const runs = [], started = now(); let last, reason;
  const run = amplitude => {
    const trial = { ...config, boundaries: { ...config.boundaries, [c.boundary]: { ...config.boundaries?.[c.boundary], type: 'flather_stage', amplitude_m: amplitude, delay_s: delay, period_s: period } } };
    last = simulate(trial, { isCancelled, onProgress: p => onProgress?.({ ...p, run: runs.length + 1, forcing_amplitude_m: amplitude }) });
    const achieved = last.actualGaugeHeight;
    runs.push({ forcing_amplitude_m: amplitude, achieved_height_m: achieved, elapsed_wall_ms: last.diagnostics.elapsed_wall_ms, steps: last.diagnostics.steps });
    onProgress?.({ run: runs.length, run_complete: true, forcing_amplitude_m: amplitude, achieved_height_m: achieved });
    const sorted = [...runs].sort((a, b) => a.forcing_amplitude_m - b.forcing_amplitude_m);
    if (sorted.some((r, i) => i && r.achieved_height_m + tolerance < sorted[i - 1].achieved_height_m)) reason = 'nonmonotone_response';
    if (last.diagnostics.relative_mass_residual > 1e-8) reason = 'mass_balance_failure';
    return achieved;
  };
  let low = 0, lowHeight = run(0), achieved = lowHeight, high = Math.min(maximum, Math.max(.1, targetHeight)), highHeight;
  const baselineDrift = last.diagnostics.maximum_depth_change_m;
  if (Math.max(Math.abs(lowHeight), baselineDrift) > Math.min(.001, tolerance)) reason = 'baseline_not_stationary';
  if (!reason && Math.abs(achieved - targetHeight) > tolerance) {
    // Bounded adaptive bracket: avoid an unnecessarily extreme first forcing.
    highHeight = achieved = run(high);
    let used = 1;
    while (!reason && achieved < targetHeight - tolerance && high < maximum && used < c.max_iterations) {
      low = high; lowHeight = highHeight; high = Math.min(maximum, high * 2); highHeight = achieved = run(high); used++;
    }
    if (!reason && achieved < targetHeight - tolerance) reason = high === maximum ? 'target_outside_forcing_bracket' : 'calibration_iteration_limit';
    while (!reason && Math.abs(achieved - targetHeight) > tolerance && used < c.max_iterations) {
      const f = highHeight > lowHeight ? (targetHeight - lowHeight) / (highHeight - lowHeight) : .5;
      const amplitude = low + (high - low) * Math.min(.9, Math.max(.1, f));
      achieved = run(amplitude); used++;
      if (achieved < targetHeight) { low = amplitude; lowHeight = achieved; } else { high = amplitude; highHeight = achieved; }
    }
    if (!reason && Math.abs(achieved - targetHeight) > tolerance) reason = 'calibration_iteration_limit';
  }
  if (!reason && last.boundaryAudit.inundation_reaches_closed_cut_boundary) reason = 'inundation_reaches_closed_cut_boundary';
  if (!reason && last.boundaryAudit.supercritical_open_boundary) reason = 'supercritical_open_boundary';
  const calibration = { target_height_m: targetHeight, achieved_height_m: achieved, residual_m: achieved - targetHeight,
    tolerance_m: tolerance, forcing_amplitude_m: runs.at(-1).forcing_amplitude_m, reference_index: config.reference_index,
    baseline_maximum_depth_drift_m: baselineDrift, elapsed_wall_ms: now() - started, cpu_time_ms: null, runs,
    definition: 'maximum model coastal stage rise above initial baseline at declared wet reference cell', real_world_validated: false };
  return { status: reason ? 'rejected' : 'accepted', reason: reason ?? null, result: last, calibration };
}
