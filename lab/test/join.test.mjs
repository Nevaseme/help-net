import test from 'node:test';
import assert from 'node:assert/strict';
import {parseInvite,androidIntent,shareUrl,parseRelease} from '../web/join.mjs';

const invite = '12345678-1234-1234-1234-123456789abc:' + 'a'.repeat(64);
const page = 'https://example.test/join.html';
test('invite parsing accepts exactly a UUID and 64 hex characters, raw or URI encoded', () => {
  assert.equal(parseInvite('#' + invite), invite);
  assert.equal(parseInvite('#' + encodeURIComponent(invite)), invite);
  assert.equal(parseInvite(invite.toUpperCase()), invite.toUpperCase());
  for (const invalid of ['',null,undefined,'#%ZZ','#' + invite + '\n',invite + ':extra',invite.slice(1),invite + ';scheme=https',page + '#' + invite,'javascript:alert(1)',invite.replace(/a{64}$/, 'z'.repeat(64)),encodeURIComponent(encodeURIComponent(invite))]) assert.equal(parseInvite(invalid), null, String(invalid));
});
test('Android intent is fixed to the PlanD app and preserves the entire invite as one value', () => {
  assert.equal(androidIntent(invite), `intent://join?invite=${encodeURIComponent(invite)}#Intent;scheme=pland;package=io.github.nevaseme.plandmk1;end`);
  assert.equal(new URL(androidIntent(invite)).searchParams.get('invite'), invite);
  for (const invalid of [invite + '\n','https://evil.test','',null]) assert.throws(() => androidIntent(invalid));
});
test('QR link keeps the invite in the fragment and removes query data', () => {
  const link = shareUrl(page + '?tracking=discard#old', invite);
  assert.equal(link, page + '#' + invite);
  assert.equal(new URL(link).search, '');
  assert.throws(() => shareUrl('http://example.test/join.html', invite));
  assert.throws(() => shareUrl(page, 'https://evil.test'));
});
test('release metadata requires a complete HTTPS artifact and digest', () => {
  const release = {version:'1.7.0',url:'/releases/demo.apk',sha256:'b'.repeat(64),bytes:12000000};
  assert.equal(parseRelease(release, page).url, 'https://example.test/releases/demo.apk');
  assert.equal(parseRelease({...release,url:'https://downloads.test/demo.apk'},page).url,'https://downloads.test/demo.apk');
  for (const change of [{url:'javascript:alert(1)'},{url:'http://example.test/demo.apk'},{url:'https://user:pass@example.test/demo.apk'},{url:'https://example.test/demo.apk#secret'},{url:''},{sha256:'bad'},{bytes:0},{bytes:1.5},{version:''}]) assert.equal(parseRelease({...release,...change},page),null);
  assert.equal(parseRelease(null,page),null);
});
