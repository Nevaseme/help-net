import { parseRegistration, parseWarning, prepareDelivery, diagnosticDelivery } from './protocol.mjs';

const json = (value, status = 200) => Response.json(value, { status });

export class RelayEngine {
  constructor({ storage, configured, adminToken, connect, sendBatch, defer = p => p.catch(() => {}), now = Date.now,
    source = 'p2pquake', parse = parseWarning, prepare = prepareDelivery, diagnostic = diagnosticDelivery, maxAgeMs = null }) {
    Object.assign(this, { storage, configured, adminToken, connect, sendBatch, defer, now, source, parse, prepare, diagnostic, maxAgeMs });
    this.socket = null; this.connecting = null; this.processing = Promise.resolve();
    this.lastTrafficAt = null; this.lastError = null;
    this.statistics = { connections: 0, heartbeats: 0, dataPackets: 0, failedConnections: 0, connectedAt: null, lastHeartbeatAt: null };
    this.restored = false; this.lastActivityAt = 0;
  }
  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (request.method === 'GET' && path === '/health') {
      const saved = await this.storage.get('status');
      const connected = this.socket?.readyState === 1;
      return json({ configured: this.configured, enabled: Boolean(await this.storage.get('enabled')), source: this.source,
        ...(this.restored ? this.statistics : saved?.source === this.source ? saved.statistics : this.statistics),
        upstream: connected ? 'connected' : 'disconnected', lastTrafficAt: this.lastTrafficAt ?? saved?.lastTrafficAt ?? null,
        lastError: connected ? null : this.lastError ?? saved?.lastError ?? null,
        nextCheckAt: await this.storage.getAlarm?.() ?? null });
    }
    if (request.method !== 'POST' || !['/register', '/start', '/test'].includes(path)) return json({ error: 'Not found' }, 404);
    if (path !== '/register' && (!this.adminToken || request.headers.get('Authorization') !== `Bearer ${this.adminToken}`)) return json({ error: 'Unauthorized' }, 401);
    let body;
    try {
      const text = await request.text();
      if (text.length > 8192) return json({ error: 'Request too large' }, 413);
      body = text ? JSON.parse(text) : {};
      if (path === '/register') body = parseRegistration(body);
    } catch { return json({ error: 'Invalid request' }, 400); }
    if (!this.configured) return json({ error: 'Sender is not configured' }, 503);
    if (path === '/register') {
      await this.storage.put(`device:${body.id}`, body);
      await this.start();
      return json({ registered: true });
    }
    if (path === '/start') { await this.start(); return json({ started: true }); }
    const device = typeof body.id === 'string' ? await this.storage.get(`device:${body.id}`) : null;
    if (!device) return json({ error: 'Device is not registered' }, 404);
    const result = await this.sendBatch([device], this.diagnostic(this.now()));
    return json({ accepted: result[0]?.ok === true, status: result[0]?.status ?? 503, error: result[0]?.error }, result[0]?.ok ? 200 : 502);
  }
  async start() { await this.storage.put('enabled', true); await this.tick(); }
  async tick() {
    if (!this.configured || !await this.storage.get('enabled')) return;
    if (!this.restored) {
      const saved = await this.storage.get('status');
      if (saved?.source === this.source) { Object.assign(this.statistics, saved.statistics ?? {}); this.lastTrafficAt = saved.lastTrafficAt ?? null; }
      this.restored = true;
    }
    try { await this.ensureConnected(); this.lastError = null; }
    catch (error) { this.statistics.failedConnections++; this.lastError = `UPSTREAM_CONNECTION_FAILED:${String(error?.message ?? 'unknown').slice(0, 160)}`; }
    finally {
      await this.saveStatus();
      await this.storage.setAlarm(this.now() + 60_000);
    }
  }
  async ensureConnected() {
    if (this.source === 'wolfx' && this.socket?.readyState === 1 && this.now() - this.lastActivityAt > 150000) {
      const silent = this.socket; this.socket = null; silent.close?.(1000, 'Heartbeat timeout');
    }
    if (this.socket?.readyState === 1) return;
    if (this.connecting) return this.connecting;
    this.connecting = (async () => {
      const socket = await this.connect();
      if (!socket) throw Error('Upstream did not accept a WebSocket');
      this.socket = socket;
      this.statistics.connections++; this.statistics.connectedAt = this.now(); this.lastActivityAt = this.now();
      socket.accept();
      socket.addEventListener('message', event => {
        this.lastTrafficAt = this.now();
        this.lastActivityAt = this.lastTrafficAt;
        if (typeof event.data !== 'string') return;
        this.processing = this.processing.then(() => this.receive(event.data)).catch(() => { this.lastError = 'DELIVERY_FAILED'; });
        this.defer(this.processing);
      });
      const disconnected = event => {
        if (this.socket !== socket) return;
        this.socket = null;
        this.lastError = event.type === 'close' ? `UPSTREAM_CLOSED:${event.code ?? 'unknown'}:${String(event.reason ?? '').slice(0, 100)}` : 'UPSTREAM_SOCKET_ERROR';
        this.defer((async () => {
          await this.saveStatus();
          await this.storage.setAlarm(this.now() + 5_000);
        })());
      };
      socket.addEventListener('close', disconnected);
      socket.addEventListener('error', disconnected);
      if (this.source === 'wolfx') socket.send?.('query_jmaeew');
    })().finally(() => { this.connecting = null; });
    return this.connecting;
  }
  async receive(raw) {
    if (this.source === 'wolfx') {
      let message; try { message = JSON.parse(raw); } catch { return; }
      if (message.type === 'heartbeat') {
        this.statistics.heartbeats++; this.statistics.lastHeartbeatAt = this.now();
        this.socket?.send?.('ping'); await this.saveStatus(); return;
      }
      if (message.type === 'pong') return;
    }
    const parsed = this.parse(raw);
    if (!parsed || parsed.test) return;
    this.statistics.dataPackets++;
    const key = this.source === 'p2pquake' ? `event:${parsed.eventId}` : `event:${this.source}:${parsed.eventId}`;
    const previous = await this.storage.get(key);
    const result = this.prepare(raw, previous);
    if (!result) return;
    const stale = this.maxAgeMs !== null && (this.now() - parsed.issuedAt > this.maxAgeMs || parsed.issuedAt > this.now());
    if (this.maxAgeMs !== null) result.state.stale = stale;
    if (stale) { result.state.targeted = previous?.targeted ?? false; result.data = null; }
    if (this.source === 'p2pquake' && !result.state.targeted) return;
    await this.storage.put(key, result.state);
    if (!result.data) return;
    let startAfter = '';
    do {
      const page = await this.storage.list({ prefix: 'device:', startAfter, limit: 1000 });
      const devices = [...page.values()];
      for (let offset = 0; offset < devices.length; offset += 40) {
        // A separate service invocation gives each batch its own Free-plan fetch budget.
        const results = await this.sendBatch(devices.slice(offset, offset + 40), result.data);
        for (const delivery of results) {
          if (delivery.invalidToken) {
            const current = await this.storage.get(`device:${delivery.id}`);
            if (current?.token === delivery.token) await this.storage.delete(`device:${delivery.id}`);
          } else if (!delivery.ok) this.lastError = `FCM_${delivery.status}`;
        }
      }
      if (page.size < 1000) break;
      startAfter = [...page.keys()].at(-1);
    } while (true);
  }
  async drain() { await this.processing; }
  async saveStatus() { await this.storage.put('status', { source: this.source, statistics: this.statistics, lastTrafficAt: this.lastTrafficAt, lastError: this.lastError }); }
}
