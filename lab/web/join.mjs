// Invites stay in the fragment and local DOM. Never persist, log, or fetch them.
const INVITE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:[0-9a-f]{64}$/i;
const validInvite = value => typeof value === 'string' && value.length === 101 && INVITE.test(value);
export function parseInvite(fragment) {
  if (typeof fragment !== 'string') return null;
  try {
    const value = decodeURIComponent(fragment.startsWith('#') ? fragment.slice(1) : fragment);
    return validInvite(value) ? value : null;
  } catch { return null; }
}
export function androidIntent(invite) {
  if (!validInvite(invite)) throw new Error('Invalid invite');
  return `intent://join?invite=${encodeURIComponent(invite)}#Intent;scheme=pland;package=io.github.nevaseme.plandmk1;end`;
}
export function shareUrl(pageUrl, invite) {
  if (!validInvite(invite)) throw new Error('Invalid invite');
  const url = new URL(pageUrl);
  if (url.protocol !== 'https:' || url.username || url.password) throw new Error('HTTPS required');
  url.search = '';
  url.hash = invite;
  return url.href;
}
export function parseRelease(value, pageUrl) {
  if (!value || typeof value.version !== 'string' || !value.version.trim() || value.version.length > 80 ||
      typeof value.url !== 'string' || !value.url.trim() || typeof value.sha256 !== 'string' || !/^[0-9a-f]{64}$/i.test(value.sha256) ||
      !Number.isSafeInteger(value.bytes) || value.bytes <= 0) return null;
  try {
    const url = new URL(value.url, pageUrl);
    if (url.protocol !== 'https:' || url.username || url.password || url.hash) return null;
    return {version:value.version.trim(),url:url.href,sha256:value.sha256.toLowerCase(),bytes:value.bytes};
  } catch { return null; }
}
function start() {
  const $ = id => document.getElementById(id);
  let invite = null;
  function renderInvite() {
    invite = parseInvite(location.hash);
    const ready = Boolean(invite), malformed = Boolean(location.hash) && !ready;
    $('invite-state').textContent = ready ? '招待を確認しました。この招待のグループに参加します。' : malformed ? '招待コードを確認できません。主催者に新しい参加用QRをもらってください。' : '主催者から参加用QRまたは招待コードを受け取ってください。';
    $('invite-state').classList.toggle('invalid', malformed);
    $('app-join').setAttribute('aria-disabled', String(!ready));
    if (ready) $('app-join').href = androidIntent(invite); else $('app-join').removeAttribute('href');
    $('manual-invite').hidden = !ready;
    $('invite-code').value = invite || '';
    $('copy-state').textContent = '';
    $('qr-section').hidden = !ready;
    $('qr').replaceChildren();
    $('print-qr').disabled = true;
    if (ready) {
      try {
        const qr = globalThis.qrcode(0, 'M');
        qr.addData(shareUrl(location.href, invite), 'Byte');
        qr.make();
        // The vendored generator emits only SVG geometry; the invite is not SVG markup.
        $('qr').innerHTML = qr.createSvgTag({cellSize:5,margin:20,scalable:true});
        $('qr-state').textContent = 'Androidのカメラで読み取り、訓練に参加してください。';
        $('print-qr').disabled = false;
      } catch { $('qr-state').textContent = '参加用QRを表示できません。HTTPSの参加ページを開くか、招待コードをコピーしてください。'; }
    }
  }
  $('app-join').addEventListener('click', event => {
    if (!invite) event.preventDefault();
  });
  $('apk-download').addEventListener('click', event => {
    if ($('apk-download').getAttribute('aria-disabled') === 'true') event.preventDefault();
  });
  $('invite-form').addEventListener('submit', event => {
    event.preventDefault();
    const value = parseInvite($('invite-input').value.trim());
    if (!value) { $('invite-input-state').textContent = '招待コードの形式が違います。受け取ったコード全体を貼り付けてください。'; return; }
    history.replaceState(null, '', `${location.pathname}#${value}`);
    $('invite-input').value = '';
    $('invite-input-state').textContent = '招待を確認しました。';
    $('invite-entry').open = false;
    renderInvite();
  });
  $('copy-invite').addEventListener('click', async () => {
    if (!invite) return;
    try { await navigator.clipboard.writeText(invite); $('copy-state').textContent = '招待コードをコピーしました。'; }
    catch { $('invite-code').focus(); $('invite-code').select(); $('copy-state').textContent = 'コードを選択しました。長押しまたはコピー操作でコピーしてください。'; }
  });
  $('print-qr').addEventListener('click', () => { $('qr-section').open = true; window.print(); });
  window.addEventListener('hashchange', renderInvite);
  renderInvite();
  fetch('/demo-release.json', {cache:'no-store',credentials:'omit',referrerPolicy:'no-referrer'})
    .then(async response => {
      if (!response.ok) throw new Error('Unavailable');
      const release = parseRelease(await response.json(), location.href);
      if (!release) throw new Error('Invalid release');
      $('apk-download').href = release.url;
      $('apk-download').setAttribute('aria-disabled', 'false');
      $('apk-download').setAttribute('rel', 'noopener noreferrer');
      $('release-state').textContent = `Android版 ${release.version} · ${(release.bytes / 1048576).toFixed(1)} MB`;
      $('release-version').textContent = release.version;
      $('release-size').textContent = `${release.bytes.toLocaleString('ja-JP')} bytes`;
      $('release-sha').textContent = release.sha256;
      $('release-details').hidden = false;
    })
    .catch(() => { $('release-state').textContent = 'APKは現在ダウンロードできません。通信を確認して再読み込みするか、主催者に確認してください。'; });
}
if (typeof document !== 'undefined') start();
