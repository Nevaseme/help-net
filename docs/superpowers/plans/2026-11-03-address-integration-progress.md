# Address integration — implementation ledger

Spec: user-approved plan, 2026-11-03. Research: 2026-11-02-address-services-evaluation.md.
Base: efc13f4d6c1677e0b5ea0edf6be446758280a2f8; existing isolated detached worktree, pre-existing changes retained.

1. Data and shared search contract: implemented and published.
2. Android/PWA selection and home persistence: implemented.
3. Demo assignments and sync compatibility: implemented; regression suites and public Android/PWA interoperability passed.
4. Automated, emulator/vivo/browser verification: passed as detailed below; iPhone search/save/offline reopen confirmed by user.
5. Compatible publication and public verification: public PWA/backend and shared data published; vivo updated. Native rendered map verification passed.

Pre-flight: existing address rows combine label precision and coordinate precision; extend rows with optional metadata, keep old five fields readable. Existing signed events cannot be rewritten; optional metadata needs server and client capability handling.
Ruling: existing comparison-page files remain untouched; public city data beneath demo/data/cities is a shared production resource and may be updated as authorized by the plan.
Ruling: retain this progress document and existing worktree; do not delete shared scratch data or commit unrelated historical changes.
Browser plugin not available; dedicated Playwright test browser is already authorized in this conversation.

## Checkpoint: device setup and automated verification
- Both vivo V2417A and additional Mi 9T authorize USB debugging. Mi 9T is Android 10 / MIUI 12.
- Emulator and vivo: AddressIntegrationTest 2/2 passed. Separate disposable database verifies atomic home changes, reopening, forced SQLite failure rollback. Packaged search checks Kyoto 203, town fallback, Kyoto street names, Atami, Roppongi, and warm p95 <=500 ms.
- PWA dedicated Chrome: search -> pin confirmation -> save -> reload -> offline search/save passed. 40 warm searches p95 111.3 ms. Not iPhone device verification.
- Full pre-check suites: lab 176/176, cloud 56/56, Android JVM and debug builds passed before subsequent hardening edits.
- Added regression: foreign-building unit rejection (RED -> GREEN); failed city package update retains previous complete cache (RED -> GREEN).
- Mi 9T USB install is disabled by OEM SIM prerequisite. APK copied to Download/PlanD-1.5.47.apk and user confirmed manual installation succeeded.
- Mi 9T PC UI input test (Back key) fails with INJECT_EVENTS SecurityException. Thus current setup cannot automate installer taps. No security settings or device-owner state changed. Direct APK file URI intent also unsupported; content URI needed.
- Remaining: final hardening build/tests, shared normalization fixture, physical UI checks, metadata sync and simulated 40-client downloads, publication, final review. No public deployment or completion claim yet.

Mi 9T automation update: UHID keyboard Esc/Tab worked; direct UHID touch did not produce observed changes. UiAutomation accessibility ACTION_CLICK opened File Manager search, but this remains UI automation, not silent install. User chose manual final installation to avoid token/time cost. STOP further installer automation; copy APK then ask user only when a new installation is actually required. Current Mi 9T app 1.5.47 installed. Temporary helper commands have ended, no persistent permissions/settings altered.
Latest local suites: 237/237 Node tests; final Android unit/build SUCCESS (1m1s). Shared Android/JS normalization fixture added.

Fresh final reviewer: address_final_review (gpt-6-astra, read-only) found three important issues. Fix pass: canceled PWA building search location mutation reproduced in browser (35.096327185 instead of35.011046); stale search cache after city save reproduced in Node; cross-city facility exclusion reproduced in Node and Android emulator. Fixes applied, final green runs underway. Source extraction:1,077 PLATEAU named facilities from242 checked original Kyoto CityGMLs; added per-row provenance.

## Final verification and publication, 2026-11-03

- All three review findings fixed and verified green. Node suites 239/239 passed; app JVM tests/build passed. An accidentally broad all-module test build hit a pre-existing enclosure-probe generated R reference; scoped `:app:assembleDebugAndroidTest` passed. Sensor probe source remains untouched and outside address completion.
- Address counts: Kyoto 259,772; Atami 31,164; Minato 4,257. Facility counts: Kyoto 25,364 (including 1,077 PLATEAU names), Atami 697, Minato 7,902. Each PLATEAU input SHA checked against retained building index before extraction.
- `AddressIntegrationTest`: emulator 2/2, warm search p95 299ms; vivo 2/2, p95 110ms. Room tests use disposable databases and cover atomic change, reopen and forced-write rollback.
- Public PWA browser: search 203 -> confirm -> save -> reload; canceled Atami selection preserves original coordinates; all Kyoto city assets saved, offline reload/search/save passed. Map rendered features checked. Search p95 76.3ms, Kyoto Tower/Roppongi Hills/cross-city Atami Station found, no page errors.
- 40 simultaneous public address-package downloads passed SHA verification in 8,563ms total. This is 40 HTTP clients on one PC, not 40 physical devices or 40 concurrent full-city downloads.
- `AddressCloudInteropTest` + `address-cloud-interop.browser.mjs`: public server, emulator and vivo each passed Android -> PWA -> Android with confirmed coordinates, address precision/source, valid home relationship and Room reopen. Uses isolated fictional groups/databases; real vivo registration not changed.
- Native screen test searched 203 and canceled; existing resident/home records unchanged. Final 20.394s run verifies rendered street/building features and map center 35.011046,135.774524 (tolerance0.0001deg), then captures screenshot and cancels. 1/1 passed.
- Published shared city resources only via commit `53deddb` to safety_confirm main; no comparison HTML or tab arrangement changed. GitHub Pages manifest verified (Kyoto 259,772 addresses /25,364 facilities).
- Deployed existing tasukeai-sync Worker (same Durable Object namespace/bindings) with compatible backend+PWA atomically after shared data; PWA1.6.22, SW r44. Standalone bundle SHA256 `10f7a618a7031df060a3f4830d64eb06986b23a382b81bfcd8edd68835a586ee`. Public browser tests used the deployed URL, not a local substitute.
- Android1.5.47 final app installed on vivo/emulator. Mi9T has intermediate1.5.47; final manual update not performed or claimed. User prefers manual installer interaction.
- iPhone user reached participation screen without credentials. Created fictional group `住所確認1103` for explicit user verification; no existing group modified. User confirmed joining and map/「讃州寺町203 付近」 displayed. Then confirmed adjusted pin/address saved and still present after airplane mode + Wi-Fi off, closing/reopening PWA. This is user-observed iPhone evidence, separate from automated desktop Chrome tests.

Additional native visual investigation: first screenshot was taken as MapLibre began rendering; center pin absent in that captured frame. Added a settled-frame pixel assertion (750ms after rendered features) to distinguish a persistent overlay defect from screenshot timing. Emulator passed unchanged production code (51.345s); vivo also passed (21.226s), visible blue center pin and rendered map. This was capture timing; no production overlay fix made or claimed. Final screenshot replaced with settled-frame evidence.

Final scope: address integration implemented and published. Physical Android/PWA provenance interoperability and iPhone offline home persistence verified. Mi9T final update remains separate and manual if subsequently needed. Shock sensor integration and unrelated historical WIP are preserved and not included in this completion statement. Documentation's earlier pending statuses above are historical checkpoints; the numbered status list is current. No changes to user-managed plan.txt/add.txt or comparison-page HTML.

Evidence logs (ignored scratch): address-final-suites.log, address-final-build.log, address-vivo-final.log, address-emulator-final.log, address-public.log, address-public-interop.log, address-vivo-public-interop.log, address-vivo-ui.log. Browser/device screenshots under visualization address-integration directory. Keep historical checkpoints above as history, not current status.
