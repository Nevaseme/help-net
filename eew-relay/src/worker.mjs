import { DurableObject, WorkerEntrypoint } from 'cloudflare:workers';
import { RelayEngine } from './relay.mjs';
import { FcmClient } from './fcm.mjs';
import { parseWolfx, prepareWolfx, wolfxDiagnostic } from './wolfx.mjs';

const UPSTREAM = 'https://ws-api.wolfx.jp/jma_eew';
let sender;
let senderConfiguration;
function getSender(env) {
  if (!sender || senderConfiguration !== env.FIREBASE_SERVICE_ACCOUNT) {
    sender = new FcmClient(JSON.parse(env.FIREBASE_SERVICE_ACCOUNT));
    senderConfiguration = env.FIREBASE_SERVICE_ACCOUNT;
  }
  return sender;
}

export class FcmSender extends WorkerEntrypoint {
  async sendBatch(devices, data) {
    if (!Array.isArray(devices) || devices.length > 40) throw Error('Invalid send batch');
    const client = getSender(this.env);
    const results = [];
    for (let offset = 0; offset < devices.length; offset += 5) {
      results.push(...await Promise.all(devices.slice(offset, offset + 5).map(async device => {
        try { return { id: device.id, token: device.token, ...await client.send(device.token, data) }; }
        catch (error) { return { id: device.id, token: device.token, ok: false, invalidToken: false, status: 503, error: String(error?.message ?? 'unknown').slice(0, 160) }; }
      })));
    }
    return results;
  }
}

export class EewRelay extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    let configured = false;
    try { const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT); configured = Boolean(account.project_id && account.client_email && account.private_key && env.ADMIN_TOKEN); } catch { /* health stays accessible before setup */ }
    this.engine = new RelayEngine({ storage: ctx.storage, configured, adminToken: env.ADMIN_TOKEN,
      source: 'wolfx', parse: parseWolfx, prepare: prepareWolfx, diagnostic: wolfxDiagnostic, maxAgeMs: 180_000,
      defer: promise => ctx.waitUntil(promise),
      connect: async () => {
        const response = await fetch(UPSTREAM, { headers: { Upgrade: 'websocket',
          'User-Agent': 'PlanD-EEW-relay/1.0 (+https://nevaseme.github.io/safety_confirm/)', Accept: 'application/json' } });
        if (!response.webSocket) {
          const reason = (await response.text()).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 100);
          const headers = Object.fromEntries(['server', 'retry-after', 'cf-ray', 'cf-mitigated'].map(name => [name, response.headers.get(name)]).filter(([, value]) => value));
          throw Error(`WOLFX_HTTP_${response.status}:${JSON.stringify(headers)}:${reason}`);
        }
        return response.webSocket;
      },
      sendBatch: (devices, data) => env.FCM_SENDER.sendBatch(devices, data)
    });
  }
  fetch(request) { return this.engine.fetch(request); }
  alarm() { return this.engine.tick(); }
}

export default {
  fetch(request, env) {
    // One upstream connection, regardless of the number of phones or public requests.
    return env.RELAY.getByName('kyoto-south').fetch(request);
  }
};
