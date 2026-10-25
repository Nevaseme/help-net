import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepareDelivery, diagnosticDelivery, fcmMessage, parseRegistration } from '../src/protocol.mjs';

export function report({ id = 'one', serial = 1, cancelled = false, test = false, kyoto = true } = {}) {
  return JSON.stringify({ id, code: 556, test, cancelled,
    issue: { eventId: '20261025120000', serial: String(serial), time: '2026/10/25 12:00:00' },
    earthquake: { originTime: '2026/10/25 11:59:55', hypocenter: { name: '試験震源' } },
    areas: kyoto ? [{ name: '京都府南部', pref: '京都府', scaleFrom: 45, scaleTo: 55, kindCode: '19' },
      { name: '東京都２３区', pref: '東京都', scaleFrom: 10, scaleTo: 20, kindCode: '00' }] : [] });
}
test('projects Kyoto while keeping the original and the source identity', () => {
  const raw = report();
  const result = prepareDelivery(raw);
  assert.equal(result.state.raw, raw);
  assert.equal(result.data.schema, 'pland-eew-1');
  assert.equal(result.data.source, 'p2pquake');
  assert.equal(result.data.region, '京都府南部');
  assert.equal(JSON.parse(result.data.raw).areas.length, 1);
  assert.equal(result.state.targeted, true);
});
test('deduplicates reconnect traffic and retains the latest cancellation', () => {
  const first = prepareDelivery(report());
  assert.equal(prepareDelivery(report(), first.state), null);
  const cancelled = prepareDelivery(report({ id: 'cancel', serial: 3, cancelled: true, kyoto: false }), first.state);
  assert.ok(cancelled.data);
  assert.equal(prepareDelivery(report({ id: 'late', serial: 2 }), cancelled.state), null);
});
test('scope removal and cancellation go to previously targeted devices', () => {
  const first = prepareDelivery(report());
  const removed = prepareDelivery(report({ id: 'two', serial: 2, kyoto: false }), first.state);
  assert.ok(removed.data);
  assert.equal(removed.state.targeted, true);
  assert.equal(prepareDelivery(report({ kyoto: false })).data, null);
});
test('does not forward upstream tests or malformed traffic', () => {
  assert.equal(prepareDelivery(report({ test: true })), null);
  assert.equal(prepareDelivery('not json'), null);
  assert.equal(prepareDelivery(report().replace('"code":556', '"code":554')), null);
  assert.equal(prepareDelivery(report().replace('2026/10/25 12:00:00', '2026/02/30 12:00:00')), null);
});
test('uses immediate high-priority data delivery, without a tray-only payload', () => {
  const data = prepareDelivery(report()).data;
  const message = fcmMessage('device-token', data);
  assert.equal(message.message.android.priority, 'high');
  assert.equal(message.message.android.ttl, '0s');
  assert.equal(message.message.token, 'device-token');
  assert.equal(message.message.notification, undefined);
  assert.equal(message.message.data, data);
});
test('diagnostics are explicitly marked and cannot look like a live earthquake', () => {
  const data = diagnosticDelivery(Date.parse('2026-10-25T03:00:00Z'));
  assert.equal(data.diagnostic, 'true');
  assert.equal(JSON.parse(data.raw).test, true);
  assert.equal(JSON.parse(data.raw).issue.eventId, '20261025120000');
});
test('registration accepts only an installation and its push token', () => {
  const value = { id: 'f249e2e9-d957-48a7-89dd-e07c60e5614d', token: 'synthetic-device-token-0123456789' };
  assert.deepEqual(parseRegistration(value), value);
  assert.throws(() => parseRegistration({ ...value, id: '' }));
  assert.throws(() => parseRegistration({ ...value, token: 'with spaces' }));
});
