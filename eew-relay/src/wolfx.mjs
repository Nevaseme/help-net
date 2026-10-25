import { REGION, SCHEMA } from './protocol.mjs';
const scales = new Map([['0',0],['1',10],['2',20],['3',30],['4',40],['5弱',45],['5-',45],['5強',50],['5+',50],['6弱',55],['6-',55],['6強',60],['6+',60],['7',70]]);
const scale = value => typeof value === 'string' ? scales.get(value) ?? -1 : -1;
function jst(value) {
  if (typeof value !== 'string' || !/^\d{4}\/\d{2}\/\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) throw Error('Invalid time');
  const iso = value.replaceAll('/', '-').replace(' ', 'T'), at = Date.parse(iso + '+09:00');
  if (!Number.isFinite(at) || new Date(at + 9 * 3600000).toISOString().slice(0, 19) !== iso) throw Error('Invalid date');
  return at;
}
export function parseWolfx(raw) {
  try {
    const r = JSON.parse(raw);
    if (r.type !== 'jma_eew' || typeof r.EventID !== 'string' || !/^\d{14}$/.test(r.EventID) || !Number.isSafeInteger(r.Serial) || r.Serial <= 0 || r.Serial > 2147483647) return null;
    if (typeof r.isCancel !== 'boolean' || typeof r.isTraining !== 'boolean' || typeof r.isWarn !== 'boolean') return null;
    if (!r.Issue || !['通常', '訓練', '試験'].includes(r.Issue.Status)) return null;
    const issuedAt = jst(r.AnnouncedTime), test = r.isTraining || r.Issue.Status !== '通常';
    const list = r.WarnArea ?? [];
    if (!Array.isArray(list)) return null;
    const areas = list.map(a => {
      if (!a || typeof a.Chiiki !== 'string') throw Error('Invalid area');
      let low = scale(a.Shindo2), high = scale(a.Shindo1);
      if (low !== -1 && high !== -1 && low > high) { low = -1; high = -1; }
      return { name: a.Chiiki, pref: '', scaleFrom: low, scaleTo: high, kindCode: r.isWarn && a.Type === '警報' ? '19' : null };
    });
    return { source: r, sourceName: 'wolfx', eventId: r.EventID, messageId: `wolfx:${r.EventID}:${r.Serial}:${issuedAt}:${Number(r.isCancel)}`,
      serial: r.Serial, issuedAt, test, cancelled: r.isCancel, areas };
  } catch { return null; }
}
function envelope(r, diagnostic = false) {
  const projected = { type: 'jma_eew', Title: r.Title, Issue: r.Issue, EventID: r.EventID, Serial: r.Serial, AnnouncedTime: r.AnnouncedTime,
    OriginTime: r.OriginTime, Hypocenter: r.Hypocenter, isWarn: r.isWarn, isTraining: r.isTraining, isCancel: r.isCancel,
    WarnArea: (r.WarnArea ?? []).filter(a => a.Chiiki === REGION).map(a => ({ Chiiki: a.Chiiki, Type: a.Type, Shindo1: a.Shindo1, Shindo2: a.Shindo2 })) };
  const data = { schema: SCHEMA, source: 'wolfx', region: REGION, diagnostic: String(diagnostic), raw: JSON.stringify(projected) };
  if (new TextEncoder().encode(JSON.stringify(data)).length > 3600) throw Error('Regional packet exceeds FCM budget');
  return data;
}
export function prepareWolfx(raw, previous = null) {
  const w = parseWolfx(raw);
  if (!w || w.test) return null;
  if (previous) {
    const a = [w.serial, w.issuedAt, Number(w.cancelled)], b = [previous.serial, previous.issuedAt, Number(previous.cancelled)];
    let order = 0;
    for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) { order = a[i] > b[i] ? 1 : -1; break; }
    if (order <= 0) return null;
  }
  const relevant = !w.cancelled && w.areas.some(a => a.name === REGION && a.kindCode === '19');
  const targeted = relevant || Boolean(previous?.targeted);
  const state = { source: 'wolfx', eventId: w.eventId, messageId: w.messageId, serial: w.serial, issuedAt: w.issuedAt, cancelled: w.cancelled, targeted, raw };
  return { state, data: targeted ? envelope(w.source) : null };
}
export function wolfxDiagnostic(now = Date.now()) {
  const local = new Date(now + 9 * 3600000).toISOString().slice(0, 19);
  return envelope({ type: 'jma_eew', Title: 'PlanD 受信試験', EventID: local.replaceAll(/[-T:]/g, ''), Serial: 1,
    AnnouncedTime: local.replaceAll('-', '/').replace('T', ' '), Issue: { Source: 'PlanD', Status: '訓練' }, isWarn: true, isTraining: true, isCancel: false,
    WarnArea: [{ Chiiki: REGION, Type: '警報' }] }, true);
}
