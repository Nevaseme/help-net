import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RelayEngine } from '../src/relay.mjs';
import { parseWolfx, prepareWolfx, wolfxDiagnostic } from '../src/wolfx.mjs';

const id = 'f249e2e9-d957-48a7-89dd-e07c60e5614d';
const token = 'synthetic-device-token-0123456789';
function report(serial = 1, cancelled = false) {
  return JSON.stringify({ id: `message-${serial}`, code: 556, test: false, cancelled,
    issue: { eventId: '20261025120000', serial: String(serial), time: '2026/10/25 12:00:00' },
    earthquake: {}, areas: cancelled ? [] : [{ name: '京都府南部', pref: '京都府', scaleFrom: 45, scaleTo: 55, kindCode: '19' }] });
}
class MemoryStorage {
  data = new Map(); alarm = null;
  async get(key) { return this.data.get(key); }
  async put(key, value) { this.data.set(key, structuredClone(value)); }
  async delete(key) { return this.data.delete(key); }
  async list({ prefix = '', startAfter = '', limit = 1000 } = {}) {
    return new Map([...this.data].filter(([key]) => key.startsWith(prefix) && key > startAfter).sort(([a], [b]) => a.localeCompare(b)).slice(0, limit));
  }
  async setAlarm(at) { this.alarm = at; }
  async getAlarm() { return this.alarm; }
}
class Socket extends EventTarget {
  readyState = 1;
  sent = [];
  send(value) { this.sent.push(value); }
  accept() {}
  message(raw) { this.dispatchEvent(new MessageEvent('message', { data: raw })); }
  disconnect() { this.readyState = 3; this.dispatchEvent(new Event('close')); }
}
function setup(overrides = {}) {
  const storage = new MemoryStorage(), sockets = [], sends = [];
  const engine = new RelayEngine({ storage, configured: true, adminToken: 'synthetic-admin', now: () => 1780000000000,
    connect: async () => { const socket = new Socket(); sockets.push(socket); return socket; },
    sendBatch: async (devices, data) => { sends.push({ devices, data }); return devices.map(device => ({ id: device.id, token: device.token, ok: true, invalidToken: false, status: 200 })); },
    defer: promise => { promise.catch(() => {}); }, ...overrides });
  return { engine, storage, sockets, sends };
}

const wolfReport = (serial = 1, time = '2026/10/25 12:00:00') => JSON.stringify({ type: 'jma_eew', EventID: '20261025120000', Serial: serial,
  AnnouncedTime: time, Issue: { Status: '通常' }, isTraining: false, isWarn: true, isCancel: false,
  WarnArea: [{ Chiiki: '京都府南部', Type: '警報' }] });
const wolfOptions = { source: 'wolfx', parse: parseWolfx, prepare: prepareWolfx, diagnostic: wolfxDiagnostic, maxAgeMs: 180000 };
test('Wolfx archives old snapshots, sends timely warnings once, and namespaces its source', async () => {
  const issued = Date.parse('2026-10-25T03:00:00Z');
  let time = issued + 180001;
  const { engine, storage, sends, sockets } = setup({ ...wolfOptions, now: () => time });
  await engine.fetch(post('/register', { id, token }));
  sockets[0].message(wolfReport()); await engine.drain();
  assert.equal(sends.length, 0);
  assert.equal((await storage.get('event:wolfx:20261025120000')).stale, true);
  time = issued + 190000;
  sockets[0].message(wolfReport(2, '2026/10/25 12:03:00')); await engine.drain();
  assert.equal(sends.length, 1); assert.equal(sends[0].data.source, 'wolfx');
  sockets[0].message(wolfReport(2, '2026/10/25 12:03:00')); await engine.drain();
  assert.equal(sends.length, 1);
  assert.equal((await storage.get('event:wolfx:20261025120000')).stale, false);
});
test('Wolfx accepts the 3-minute boundary and issues Wolfx diagnostics', async () => {
  const { engine, sockets, sends } = setup({ ...wolfOptions, now: () => Date.parse('2026-10-25T03:03:00Z') });
  await engine.fetch(post('/register', { id, token }));
  sockets[0].message(wolfReport()); await engine.drain(); assert.equal(sends.length, 1);
  await engine.fetch(post('/test', { id }, true));
  assert.equal(sends[1].data.source, 'wolfx'); assert.equal(sends[1].data.diagnostic, 'true');
});
test('a silent Wolfx connection is replaced and status persists reception counts', async () => {
  let time = 1000;
  const { engine, storage, sockets } = setup({ ...wolfOptions, now: () => time });
  await engine.fetch(post('/start', {}, true));
  assert.deepEqual(sockets[0].sent, ['query_jmaeew']);
  sockets[0].message('{"type":"heartbeat","timestamp":1000}'); await engine.drain();
  assert.deepEqual(sockets[0].sent, ['query_jmaeew', 'ping']);
  time += 160000; await engine.tick();
  assert.equal(sockets.length, 2);
  assert.deepEqual(sockets[1].sent, ['query_jmaeew']);
  const status = await (await engine.fetch(new Request('https://relay.example/health'))).json();
  assert.equal(status.source, 'wolfx'); assert.equal(status.connections, 2); assert.equal(status.heartbeats, 1);
  assert.ok(await storage.get('status'));
});
const post = (path, value, admin = false) => new Request(`https://relay.example${path}`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', ...(admin ? { Authorization: 'Bearer synthetic-admin' } : {}) }, body: JSON.stringify(value) });

test('registers a device, receives upstream data and sends only the latest reports', async () => {
  const { engine, storage, sockets, sends } = setup();
  assert.equal((await engine.fetch(post('/register', { id, token }))).status, 200);
  assert.equal(sockets.length, 1);
  sockets[0].message(report()); sockets[0].message(report()); sockets[0].message(report(3, true)); sockets[0].message(report(2));
  await engine.drain();
  assert.equal(sends.length, 2);
  assert.equal(sends[0].devices[0].token, token);
  assert.equal(JSON.parse(sends[1].data.raw).cancelled, true);
  assert.equal((await storage.get('event:20261025120000')).serial, 3);
  assert.ok(storage.alarm);
});
test('reconnects after a close and after the durable object is recreated', async () => {
  const first = setup();
  await first.engine.fetch(post('/register', { id, token }));
  first.sockets[0].disconnect();
  await first.engine.tick();
  assert.equal(first.sockets.length, 2);
  const restored = new RelayEngine({ storage: first.storage, configured: true, adminToken: 'synthetic-admin',
    connect: async () => new Socket(), sendBatch: async () => [], defer: promise => promise.catch(() => {}) });
  await restored.tick();
  assert.equal((await (await restored.fetch(new Request('https://relay.example/health'))).json()).upstream, 'connected');
});
test('keeps one registration when the FCM token changes and exposes no device tokens', async () => {
  const { engine, storage } = setup();
  await engine.fetch(post('/register', { id, token }));
  await engine.fetch(post('/register', { id, token: token + '-new' }));
  assert.equal((await storage.list({ prefix: 'device:' })).size, 1);
  assert.equal((await storage.get(`device:${id}`)).token, token + '-new');
  const health = await (await engine.fetch(new Request('https://relay.example/health'))).text();
  assert.equal(health.includes(token), false);
  assert.equal((await engine.fetch(post('/test', { id }))).status, 401);
});
test('only the protected test route generates a clearly marked diagnostic', async () => {
  const { engine, sends } = setup();
  await engine.fetch(post('/register', { id, token }));
  assert.equal((await engine.fetch(post('/test', { id }, true))).status, 200);
  assert.equal(sends.length, 1);
  assert.equal(sends[0].data.diagnostic, 'true');
  assert.equal(JSON.parse(sends[0].data.raw).test, true);
});
test('does not accept a registration without sender configuration', async () => {
  const engine = new RelayEngine({ storage: new MemoryStorage(), configured: false, adminToken: '', connect: async () => {}, sendBatch: async () => [] });
  assert.equal((await engine.fetch(post('/register', { id, token }))).status, 503);
  assert.equal((await engine.fetch(post('/register', { id: '', token }))).status, 400);
});

test('connection status identifies an upstream HTTP refusal', async () => {
  const storage = new MemoryStorage();
  const engine = new RelayEngine({ storage, configured: true, adminToken: 'synthetic-admin',
    connect: async () => { throw Error('P2P_HTTP_403'); }, sendBatch: async () => [] });
  await engine.start();
  const health = await (await engine.fetch(new Request('https://relay.example/health'))).json();
  assert.equal(health.lastError, 'UPSTREAM_CONNECTION_FAILED:P2P_HTTP_403');
  const recreated = new RelayEngine({ storage, configured: true, adminToken: 'synthetic-admin',
    connect: async () => {}, sendBatch: async () => [] });
  const restoredHealth = await (await recreated.fetch(new Request('https://relay.example/health'))).json();
  assert.equal(restoredHealth.lastError, health.lastError);
  assert.equal(restoredHealth.nextCheckAt, storage.alarm);
});
