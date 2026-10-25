import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HttpProbe } from './probe.mjs';
function setup(fetcher) {
  let time = 0;
  const data = new Map();
  const storage = { get: async k => structuredClone(data.get(k)), put: async (k, v) => { for (const [key, value] of typeof k === 'string' ? [[k, v]] : Object.entries(k)) data.set(key, structuredClone(value)); }, list: async ({ prefix }) => new Map([...data].filter(([k]) => k.startsWith(prefix))), setAlarm: async at => { storage.alarm = at; } };
  const options = { fetcher: fetcher ?? (async () => { time += 120; return Response.json([{ id: 'old', time: '2020/01/01 00:00:00' }]); }), now: () => time, phases: [{ intervalMs: 5000, durationMs: 15000 }, { intervalMs: 2000, durationMs: 6000 }] };
  return { storage, options, probe: new HttpProbe(storage, options), advance: t => { time = t; } };
}
test('finite autonomous phases survive recreation and mark initial history as baseline', async () => {
  const t = setup(); await t.probe.start();
  t.advance(5000); await t.probe.tick();
  assert.equal(t.storage.alarm, 10000);
  assert.equal((await t.probe.results()).samples[0].newRecords[0].baseline, true);
  const recreated = new HttpProbe(t.storage, t.options);
  await recreated.start(); assert.equal((await recreated.state()).count, 1);
  t.advance(20000); await recreated.tick();
  const results = await recreated.results();
  assert.equal(results.samples[1].phase, 1);
  assert.equal(results.samples[1].newRecords.length, 0);
  assert.equal(t.storage.alarm, 22000);
  t.advance(26000); assert.equal(await recreated.tick(), false);
  assert.equal((await recreated.state()).completed, true);
});
test('rate limit response respects Retry-After instead of repeated polling', async () => {
  const t = setup(async () => new Response('', { status: 429, headers: { 'Retry-After': '120' } }));
  await t.probe.start(); t.advance(5000); await t.probe.tick();
  assert.equal(t.storage.alarm, 125000);
  t.advance(10000); await t.probe.tick();
  assert.equal((await t.probe.state()).count, 1);
});
test('slow response skips missed slots without bursts', async () => {
  const t = setup(async () => { t.advance(16200); return Response.json([]); });
  await t.probe.start(); t.advance(5000); await t.probe.tick();
  assert.equal(t.storage.alarm, 20000);
  assert.equal((await t.probe.results()).samples[0].elapsedMs, 11200);
});
