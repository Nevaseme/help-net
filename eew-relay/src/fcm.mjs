import { fcmMessage } from './protocol.mjs';

const bytes = value => new TextEncoder().encode(value);
const base64url = value => btoa(String.fromCharCode(...new Uint8Array(value))).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');

export class FcmClient {
  constructor(account, fetcher = fetch, now = Date.now) {
    if (!account?.project_id || !account?.client_email || !account?.private_key) throw Error('Firebase service account is not configured');
    this.account = account; this.fetch = (...args) => fetcher(...args); this.now = now; this.access = null; this.refreshing = null;
  }
  async accessToken() {
    if (this.access && this.access.until > this.now() + 300_000) return this.access.value;
    if (!this.refreshing) this.refreshing = this.refresh().finally(() => { this.refreshing = null; });
    return this.refreshing;
  }
  async refresh() {
    const now = Math.floor(this.now() / 1000);
    const header = base64url(bytes(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
    const payload = base64url(bytes(JSON.stringify({ iss: this.account.client_email,
      scope: 'https://www.googleapis.com/auth/firebase.messaging', aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600 })));
    const encoded = this.account.private_key.replace(/-----[^-]+-----/g, '').replace(/\s/g, '');
    const key = await crypto.subtle.importKey('pkcs8', Uint8Array.from(atob(encoded), c => c.charCodeAt(0)),
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
    const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, bytes(`${header}.${payload}`));
    const response = await this.fetch('https://oauth2.googleapis.com/token', {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${header}.${payload}.${base64url(signature)}` }).toString()
    });
    if (!response.ok) { await response.body?.cancel(); throw Error(`Google sender authentication failed (${response.status})`); }
    const result = await response.json();
    if (!result.access_token || !(result.expires_in > 0)) throw Error('Google returned an invalid access token response');
    this.access = { value: result.access_token, until: this.now() + result.expires_in * 1000 };
    return this.access.value;
  }
  async send(token, data) {
    const access = await this.accessToken();
    const response = await this.fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(this.account.project_id)}/messages:send`, {
      method: 'POST', headers: { Authorization: `Bearer ${access}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(fcmMessage(token, data))
    });
    const result = await response.json();
    const invalidToken = result.error?.details?.some(d => d['@type'] === 'type.googleapis.com/google.firebase.fcm.v1.FcmError' && d.errorCode === 'UNREGISTERED') ?? false;
    if (response.status === 401) this.access = null;
    return { ok: response.ok, invalidToken, status: response.status };
  }
}
