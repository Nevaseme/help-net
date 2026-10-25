// Finite, read-only upstream experiment. Never calls FCM or disaster formation.
export const RUN_ID = 'http-20261025-a';
export const PHASES = [{ intervalMs: 5000, durationMs: 600000 }, { intervalMs: 2000, durationMs: 300000 }, { intervalMs: 5000, durationMs: 600000 }];
const PREFIX = `probe:${RUN_ID}:`;
const URL = 'https://api.p2pquake.net/v2/history?codes=556&limit=20';

export class HttpProbe {
  constructor(storage, { fetcher = (...args) => fetch(...args), now = Date.now, phases = PHASES } = {}) {
    Object.assign(this, { storage, fetcher, now, phases });
    this.running = false;
  }
  async state() { return this.storage.get(PREFIX + 'state'); }
  async start() {
    if (await this.state()) return; // A repeated admin request must not restart the experiment.
    const startAt = this.now() + 5000;
    await this.storage.put(PREFIX + 'state', { runId: RUN_ID, startAt, phase: 0, phaseStartAt: startAt, nextAt: startAt, count: 0, seen: [], completed: false });
    await this.storage.setAlarm(startAt);
  }
  async tick() {
    const state = await this.state();
    if (!state || state.completed) return false;
    if (this.running) return true;
    if (this.now() < state.nextAt) { await this.storage.setAlarm(state.nextAt); return true; }
    this.running = true;
    try {
      while (this.now() >= state.phaseStartAt + this.phases[state.phase].durationMs) {
        state.phaseStartAt += this.phases[state.phase].durationMs;
        state.phase++;
        if (state.phase === this.phases.length) {
          state.completed = true; state.completedAt = this.now();
          await this.storage.put(PREFIX + 'state', state);
          return false;
        }
        state.nextAt = state.phaseStartAt;
      }
      const phase = this.phases[state.phase];
      const sample = { n: state.count, phase: state.phase, intervalMs: phase.intervalMs, scheduledAt: state.nextAt, startedAt: this.now() };
      let retryMs = 0;
      try {
        const response = await this.fetcher(URL, { headers: { Accept: 'application/json', 'User-Agent': 'PlanD-EEW-relay/1.0 (+https://nevaseme.github.io/safety_confirm/)' }, signal: AbortSignal.timeout(10000) });
        sample.status = response.status;
        sample.headers = Object.fromEntries(['date', 'age', 'cf-cache-status', 'cf-ray', 'retry-after'].map(k => [k, response.headers.get(k)]).filter(([, v]) => v !== null));
        const body = await response.text();
        sample.bytes = new TextEncoder().encode(body).length;
        if (response.ok) {
          const records = JSON.parse(body);
          if (!Array.isArray(records)) throw Error('Expected history array');
          sample.records = records.length;
          sample.newRecords = [];
          for (const item of records) {
            const id = String(item.id ?? `${item.issue?.eventId}:${item.issue?.serial}:${item.cancelled}`);
            if (state.seen.includes(id)) continue;
            // The first response is a historical baseline, not measured delivery latency.
            sample.newRecords.push({ id, time: item.time ?? null, issue: item.issue ?? null, baseline: state.count === 0 });
            state.seen.push(id);
          }
          state.seen = state.seen.slice(-200);
        }
        if (response.status === 429 || response.status >= 500) {
          const value = response.headers.get('retry-after');
          const parsed = value && /^\d+$/.test(value) ? Number(value) * 1000 : Date.parse(value ?? '') - this.now();
          retryMs = Math.max(60000, Number.isFinite(parsed) ? parsed : 0);
        }
      } catch (error) { sample.error = String(error?.message ?? error).slice(0, 160); retryMs = 60000; }
      sample.finishedAt = this.now();
      sample.elapsedMs = sample.finishedAt - sample.startedAt;
      sample.scheduleLagMs = sample.startedAt - sample.scheduledAt;
      state.count++;
      // Skip missed slots; never catch up with a burst of requests.
      const slot = Math.floor((sample.finishedAt - state.phaseStartAt) / phase.intervalMs) + 1;
      state.nextAt = Math.max(state.phaseStartAt + slot * phase.intervalMs, sample.finishedAt + retryMs);
      await this.storage.put({ [PREFIX + 'state']: state, [PREFIX + 'sample:' + String(sample.n).padStart(5, '0')]: sample });
      await this.storage.setAlarm(state.nextAt);
      return true;
    } finally { this.running = false; }
  }
  async results() {
    const samples = [...(await this.storage.list({ prefix: PREFIX + 'sample:' })).values()];
    return { state: await this.state(), phases: this.phases, samples };
  }
}
