export const REGION = '京都府南部';
export const SCHEMA = 'pland-eew-1';
const scales = new Set([-1, 0, 10, 20, 30, 40, 45, 50, 55, 60, 70]);
const warnings = new Set(['10', '11', '19']);

function issueTime(value) {
  if (typeof value !== 'string' || !/^\d{4}\/\d{2}\/\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) throw Error('Invalid issue time');
  const iso = value.replaceAll('/', '-').replace(' ', 'T');
  const millis = Date.parse(`${iso}+09:00`);
  if (!Number.isFinite(millis) || new Date(millis + 9 * 3600_000).toISOString().slice(0, 19) !== iso) throw Error('Invalid issue date');
  return millis;
}

export function parseWarning(raw) {
  try {
    const r = JSON.parse(raw);
    if (r.code !== 556 || typeof r.id !== 'string' || !r.id.trim() || typeof r.cancelled !== 'boolean') return null;
    if (r.test !== undefined && typeof r.test !== 'boolean') return null;
    const issue = r.issue;
    if (typeof issue?.eventId !== 'string' || !/^\d{14}$/.test(issue.eventId) || typeof issue.serial !== 'string' || !/^[1-9]\d*$/.test(issue.serial)) return null;
    const serial = Number(issue.serial);
    if (!Number.isSafeInteger(serial) || serial > 2147483647) return null;
    const issuedAt = issueTime(issue.time);
    if (!r.cancelled && (!r.earthquake || typeof r.earthquake !== 'object' || Array.isArray(r.earthquake) || !Array.isArray(r.areas))) return null;
    const areas = r.areas ?? [];
    if (!Array.isArray(areas) || areas.some(a => typeof a.name !== 'string' || typeof a.pref !== 'string' ||
      !scales.has(a.scaleFrom) || !(scales.has(a.scaleTo) || a.scaleTo === 99) ||
      (a.scaleFrom !== -1 && a.scaleTo !== -1 && a.scaleTo !== 99 && a.scaleFrom > a.scaleTo))) return null;
    return { source: r, eventId: issue.eventId, messageId: r.id, serial, issuedAt, cancelled: r.cancelled, test: r.test ?? false, areas };
  } catch { return null; }
}

function envelope(source, diagnostic = false) {
  const earthquake = source.earthquake ?? {};
  const projected = { id: source.id, code: 556, test: source.test ?? false, issue: source.issue, cancelled: source.cancelled,
    earthquake: { originTime: earthquake.originTime, hypocenter: { name: earthquake.hypocenter?.name } },
    areas: (source.areas ?? []).filter(a => a.name === REGION).map(a => ({ name: a.name, pref: a.pref, scaleFrom: a.scaleFrom, scaleTo: a.scaleTo, kindCode: a.kindCode })) };
  const data = { schema: SCHEMA, source: 'p2pquake', region: REGION, diagnostic: String(diagnostic), raw: JSON.stringify(projected) };
  if (new TextEncoder().encode(JSON.stringify(data)).length > 3600) throw Error('Regional packet exceeds the FCM size budget');
  return data;
}

export function prepareDelivery(raw, previous = null) {
  const warning = parseWarning(raw);
  if (!warning || warning.test) return null;
  if (previous) {
    const a = [warning.serial, warning.issuedAt, Number(warning.cancelled), warning.messageId];
    const b = [previous.serial, previous.issuedAt, Number(previous.cancelled), previous.messageId];
    let order = 0;
    for (let i = 0; i < a.length; i++) { if (a[i] !== b[i]) { order = a[i] > b[i] ? 1 : -1; break; } }
    if (order <= 0) return null;
  }
  const relevant = !warning.cancelled && warning.areas.some(a => a.name === REGION && warnings.has(a.kindCode));
  const targeted = relevant || Boolean(previous?.targeted);
  const state = { eventId: warning.eventId, messageId: warning.messageId, serial: warning.serial, issuedAt: warning.issuedAt,
    cancelled: warning.cancelled, targeted, raw };
  return { state, data: targeted ? envelope(warning.source) : null };
}

export function diagnosticDelivery(now = Date.now()) {
  const local = new Date(now + 9 * 3600_000).toISOString().slice(0, 19);
  const eventId = local.replaceAll(/[-T:]/g, '');
  const time = local.replaceAll('-', '/').replace('T', ' ');
  return envelope({ id: `pland-diagnostic-${now}`, code: 556, test: true, cancelled: false,
    issue: { eventId, serial: '1', time }, earthquake: {}, areas: [] }, true);
}

export function fcmMessage(token, data) {
  return { message: { token, data, android: { priority: 'high', ttl: '0s', restricted_package_name: 'io.github.nevaseme.plandmk1' } } };
}

export function parseRegistration(value) {
  if (!value || typeof value.id !== 'string' || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value.id) ||
    typeof value.token !== 'string' || !/^\S{20,4096}$/.test(value.token)) throw Error('Invalid registration');
  return { id: value.id, token: value.token };
}
