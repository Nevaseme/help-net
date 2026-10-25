import { readFile } from 'node:fs/promises';
const data = JSON.parse((await readFile(process.argv[2], 'utf8')).replace(/^\uFEFF/, ''));
const stats = values => {
  if (!values.length) return null;
  const sorted = values.toSorted((a, b) => a - b);
  return { n: values.length, min: sorted[0], mean: values.reduce((a, b) => a + b, 0) / values.length,
    p50: sorted[Math.ceil(sorted.length * .5) - 1], p95: sorted[Math.ceil(sorted.length * .95) - 1], max: sorted.at(-1) };
};
const phases = data.phases.map((phase, index) => {
  const samples = data.samples.filter(s => s.phase === index);
  const gaps = samples.slice(1).map((s, i) => s.startedAt - samples[i].startedAt);
  const successful = samples.filter(s => s.status === 200 && !s.error);
  const successGaps = successful.slice(1).map((s, i) => s.startedAt - successful[i].startedAt);
  let totalMs = 0, weightedDetectionMs = 0, maxDetectionMs = 0;
  for (let i = 1; i < successful.length; i++) {
    const previous = successful[i - 1], current = successful[i];
    const gap = current.startedAt - previous.startedAt;
    totalMs += gap;
    weightedDetectionMs += gap * (gap / 2 + current.elapsedMs);
    maxDetectionMs = Math.max(maxDetectionMs, gap + current.elapsedMs);
  }
  const statusCounts = {};
  for (const s of samples) { const key = s.error ? 'error:' + s.error : String(s.status); statusCounts[key] = (statusCounts[key] ?? 0) + 1; }
  return { index, ...phase, samples: samples.length, statusCounts, firstAt: samples[0]?.startedAt, lastAt: samples.at(-1)?.finishedAt,
    communicationMs: stats(successful.map(s => s.elapsedMs)), startGapMs: stats(gaps), successfulStartGapMs: stats(successGaps), plannedSlotLagMs: stats(samples.map(s => s.scheduleLagMs)),
    estimatedPublishedToReadMs: { assumption: 'Uniform publication times, API snapshot at request start; inferred from actual request gaps and durations. Not a measured earthquake latency.', mean: totalMs ? weightedDetectionMs / totalMs : null, maximumWithinObservedGaps: maxDetectionMs },
    bytes: samples.reduce((n, s) => n + (s.bytes ?? 0), 0), newLiveRecords: samples.flatMap(s => (s.newRecords ?? []).filter(r => !r.baseline).map(r => ({ ...r, fetchedAt: s.finishedAt }))) };
});
console.log(JSON.stringify({ state: data.state, phases }, null, 2));
