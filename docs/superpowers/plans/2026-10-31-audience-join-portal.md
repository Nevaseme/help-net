# Audience join portal — implemented static interface

> Deferred draft, not deployed or wired into Android. The later [venue demo plan](2026-10-31-venue-demo.md) supersedes its default invitation-in-QR flow: attendees will enter the group name and password shown on the slide. Preserve this draft as previous work; do not treat its deep link as an implemented Android capability.

`lab/web/join.html` is a separate Android demo entry page. Existing PWA navigation and comparison pages are untouched. It uses the existing typography and white row layout with the requested teal accent; the current PWA stylesheet itself has a blue primary color.

- Open `https://<deployment>/join.html#<group UUID>:<64 hex invite code>` using a real administrator-issued invitation. The fragment may percent-encode the colon. No invitation is embedded in source or release metadata.
- Without an invite, the page asks the attendee to obtain one from the organizer. A manual input accepts the same exact format and changes only the current browser URL fragment. No group is created by this page.
- The download link loads `/demo-release.json`, a JSON object with `version` (nonempty string), `url` (HTTPS URL or root-relative path on HTTPS), `sha256` (64 hex characters), and `bytes` (positive integer). Missing, invalid, or unavailable metadata leaves the download control disabled. The digest is shown for comparison; the page does not independently download and verify the APK. Release publishing and metadata generation are owned by deployment tooling.
- The join action produces `intent://join?invite=<encoded full invite>#Intent;scheme=pland;package=io.github.nevaseme.plandmk1;end`, equivalent to the app's `pland://join?invite=...` deep link. The app is responsible for validating/accepting the invite and showing join results. The page never claims that app launch equals successful membership.
- The optional display/print QR contains the current HTTPS page URL and validated invite fragment, with query parameters removed. QR generation runs locally with vendored `qrcode-generator` 1.4.4 (Kazuhiko Arase, MIT; full notice in `join-qrcode.js`). No third-party QR API, analytics, invitation fetch, or invitation persistence is used. A clipboard/manual-select fallback is available.
- The initial group join requires connectivity. Offline nearby sharing and permission checks are performed inside Android after joining; this static page does not provide Bluetooth or offline-presence functionality.

The cloud build copies top-level HTML/CSS/JS/MJS assets automatically, including this page and the vendor license header. The experimental lab server's explicit asset allowlist does not expose the new page; the browser test uses an isolated local test server and a synthetic HTTPS origin.

Validation: `node --test lab/test/join.test.mjs`; `node lab/test/join.browser.mjs` (optional `PLAYWRIGHT_MODULE`, `PLAYWRIGHT_CHROMIUM_EXECUTABLE`, and `JOIN_TEST_OUTPUT`). Browser fixtures use fictional invitations only. The portal is implemented separately from public deployment, APK availability, and physical Android device confirmation, which must be verified by the release coordinator.
