import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, createVerify } from 'node:crypto';
import { FcmClient } from '../src/fcm.mjs';

test('signs a Google service-account assertion and reuses the access token', async () => {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const account = { project_id: 'synthetic-project', client_email: 'sender@synthetic-project.iam.gserviceaccount.com',
    private_key: privateKey.export({ format: 'pem', type: 'pkcs8' }) };
  let oauthCalls = 0, sends = 0;
  const now = 1780000000000;
  const client = new FcmClient(account, async (url, options) => {
    if (url === 'https://oauth2.googleapis.com/token') {
      oauthCalls++;
      const form = new URLSearchParams(options.body);
      assert.equal(form.get('grant_type'), 'urn:ietf:params:oauth:grant-type:jwt-bearer');
      const assertion = form.get('assertion');
      const [header, payload, signature] = assertion.split('.');
      const claims = JSON.parse(Buffer.from(payload, 'base64url'));
      assert.equal(claims.iss, account.client_email);
      assert.equal(claims.scope, 'https://www.googleapis.com/auth/firebase.messaging');
      assert.equal(claims.aud, 'https://oauth2.googleapis.com/token');
      assert.equal(claims.iat, Math.floor(now / 1000));
      assert.equal(claims.exp - claims.iat, 3600);
      assert.ok(createVerify('RSA-SHA256').update(`${header}.${payload}`).verify(publicKey, Buffer.from(signature, 'base64url')));
      return Response.json({ access_token: 'synthetic-access-token', expires_in: 3600 });
    }
    assert.equal(url, 'https://fcm.googleapis.com/v1/projects/synthetic-project/messages:send');
    assert.equal(options.headers.Authorization, 'Bearer synthetic-access-token');
    sends++;
    return sends === 1 ? Response.json({ name: 'sent' }) : Response.json({ error: {
      status: 'NOT_FOUND', details: [{ '@type': 'type.googleapis.com/google.firebase.fcm.v1.FcmError', errorCode: 'UNREGISTERED' }]
    } }, { status: 404 });
  }, () => now);
  assert.equal((await client.send('device', { raw: 'synthetic' })).ok, true);
  assert.equal((await client.send('device', { raw: 'synthetic' })).invalidToken, true);
  assert.equal(oauthCalls, 1);
  assert.equal(sends, 2);
});

test('does not mistake project/authentication errors for an invalid device', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const client = new FcmClient({ project_id: 'test-project', client_email: 'test@example.invalid',
    private_key: privateKey.export({ format: 'pem', type: 'pkcs8' }) }, async url =>
    url.includes('oauth2') ? Response.json({ access_token: 'access', expires_in: 3600 }) :
      Response.json({ error: { status: 'PERMISSION_DENIED' } }, { status: 403 }));
  const result = await client.send('device', { raw: 'test' });
  assert.equal(result.ok, false);
  assert.equal(result.invalidToken, false);
});

test('calls a host fetch function without changing its receiver', async () => {
  const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const client = new FcmClient({ project_id: 'test-project', client_email: 'test@example.invalid',
    private_key: privateKey.export({ format: 'pem', type: 'pkcs8' }) }, async function(url) {
      assert.equal(this, undefined);
      return url.includes('oauth2') ? Response.json({ access_token: 'access', expires_in: 3600 }) : Response.json({ name: 'sent' });
    });
  assert.equal((await client.send('device', { raw: 'test' })).ok, true);
});
