import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseWolfx, prepareWolfx, wolfxDiagnostic } from '../src/wolfx.mjs';
export function packet(overrides = {}) {
  return JSON.stringify({ type: 'jma_eew', EventID: '20261025120000', Serial: 1, AnnouncedTime: '2026/10/25 12:00:00',
    Issue: { Source: '東京', Status: '通常' }, Title: '緊急地震速報（警報）', isTraining: false, isWarn: true, isCancel: false,
    WarnArea: [{ Chiiki: '京都府南部', Type: '警報', Shindo1: '6弱', Shindo2: '5弱' }], ...overrides });
}
test('preserves Wolfx identity and permits a regional warning without intensity', () => {
  const raw = packet({ WarnArea: [{ Chiiki: '京都府南部', Type: '警報' }] });
  const warning = parseWolfx(raw);
  assert.equal(warning.areas[0].scaleFrom, -1);
  const result = prepareWolfx(raw);
  assert.equal(result.state.targeted, true);
  assert.equal(result.data.source, 'wolfx');
  assert.equal(JSON.parse(result.data.raw).WarnArea[0].Shindo1, undefined);
  assert.equal(result.state.raw, raw);
});
test('converts upper and lower intensity bounds without using nationwide maximum', () => {
  const w = parseWolfx(packet({ MaxIntensity: '7' }));
  assert.equal(w.areas[0].scaleFrom, 45); assert.equal(w.areas[0].scaleTo, 55);
  const unknown = parseWolfx(packet({ MaxIntensity: '7', WarnArea: [{ Chiiki: '京都府南部', Type: '警報' }] }));
  assert.equal(unknown.areas[0].scaleTo, -1);
});
test('heartbeat, invalid time and training never become live alerts', () => {
  assert.equal(parseWolfx('{"type":"heartbeat","timestamp":100}'), null);
  assert.equal(parseWolfx(packet({ AnnouncedTime: '2026/02/31 12:00:00' })), null);
  assert.equal(prepareWolfx(packet({ isTraining: true })), null);
  assert.equal(prepareWolfx(packet({ Issue: { Status: '訓練' } })), null);
  assert.equal(parseWolfx(packet({ isTraining: 'false' })), null);
});
test('deduplicates updates and delivers cancellation and scope removal to prior targets', () => {
  const first = prepareWolfx(packet());
  assert.equal(prepareWolfx(packet(), first.state), null);
  assert.equal(prepareWolfx(packet({ Serial: 2, WarnArea: [], isWarn: false }), first.state).data.source, 'wolfx');
  const cancelled = prepareWolfx(packet({ Serial: 3, isCancel: true, WarnArea: [] }), first.state);
  assert.equal(cancelled.state.cancelled, true);
  assert.equal(prepareWolfx(packet({ Serial: 2 }), cancelled.state), null);
  assert.equal(prepareWolfx(packet({ WarnArea: [], isWarn: false })).data, null);
});
test('projects only the selected region and diagnostics stay explicit', () => {
  const d = prepareWolfx(packet({ OriginalText: 'x'.repeat(20000), WarnArea: [{ Chiiki: '京都府南部', Type: '警報' }, { Chiiki: '東京都', Type: '警報' }] })).data;
  assert.equal(JSON.parse(d.raw).WarnArea.length, 1);
  assert.ok(new TextEncoder().encode(JSON.stringify(d)).length < 3600);
  const diagnostic = wolfxDiagnostic(Date.parse('2026-10-25T03:00:00Z'));
  assert.equal(diagnostic.source, 'wolfx'); assert.equal(diagnostic.diagnostic, 'true');
  assert.equal(parseWolfx(diagnostic.raw).test, true);
});
