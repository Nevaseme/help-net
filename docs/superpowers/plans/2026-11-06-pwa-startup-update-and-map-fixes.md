# PWA 1.6.64: startup, update detection and repository-rename compatibility

## Reported symptoms and findings

- The user saw map/display/download errors in PWA 1.6.62 after deleting and reinstalling its Home Screen entry. Relaunching into 1.6.63 restored the visible map. Version 1.6.62 uses the old GitHub Pages city-data path; it was affected by the authorized repository rename. Deleting a Home Screen entry did not reliably replace the running/cached web code.
- Initial group entry awaited a full synchronization, including a second roster fetch even though registration had just supplied it. Holding that roster request reproduced a blocked safety screen. Registration now pulls the incident proof, presents Safety, and starts the full synchronization afterward. It does not invent an incident locally or skip the safety gate.
- The base renderer wrote a legacy status badge into `#own`, then awaited the current renderer, which removed it. A DOM mutation observer recorded three `No response` flashes and four `Safe` flashes. The legacy writer now runs only when the current renderer is absent; the same test records zero flashes.
- Update detection relied on a `controllerchange` listener attached after initialization. A suspended/restored document could miss that event. Update setup now starts independently of group initialization and compares its version with the active Service Worker at startup, visibility restoration and pageshow. It retains manual confirmation and unsaved-input protection.
- Appearance was already set to `Use device setting`. The user explicitly chose to retain this behavior. No theme-default change was made.
- City-data network URLs, three map manifests, their generator and browser fixtures use the renamed repository. Existing completed city/download/search caches remain readable through old cache-key aliases; old network URLs are never used for retrieval.
- The local experiment server whitelist now includes the new PNG icon assets. The regression test caught this omission; it is fixed.

## Verification

- `lab/test/pwa-startup.browser.mjs`: failed before the fix (safety screen blocked by redundant roster refresh, seven legacy badge flashes); passed after (safety screen available before full sync, zero badge flashes).
- `lab/test/app-update.browser.mjs`: real Service Worker test passes when the active worker changed before listeners attached; protects unsaved edits; no false notice for equal versions.
- `lab/test/late-join.browser.mjs`: earthquake and drill both pass; correct current incident, assigned home, initial safety gate, notice once, subsequent safety updates remain in Safety, membership survives reload. All group/API writes use a disposable local server.
- `node --test lab/test/*.test.mjs`: 257 passed, zero failed.
- City-cache regression: completed files saved under the old repository URL remain readable offline, including the address search package; old entries are preserved.
- Actual public Kyoto download: all 3,538 files / 286,538,547 bytes saved in 382.7 seconds. With network disabled, the city package and map style returned successfully (55 style layers). Evidence: `output/helpnet-city-download-result.json`.
- This test also exposed missing compressed byte sizes in Kyoto's building index, making the pre-download size estimate NaN. Sizes were added from existing files, and the generator now emits them. No building geometry, coordinates, residents or chunk content changed. A regression test verifies that the actual published dataset produces a finite size estimate.
- PWA 1.6.64 published: deployment `a721b3c3ab2a4d96b0eaf3d6e564c27d`, Worker SHA-256 `d086a7b35a491a31af84f6da2fb02cb354ea6baa84bdf3da4f412ed9285f2391`. All seven changed public files matched local build output. The startup/flash regression also passed against these public assets.
- User confirmed that relaunching into 1.6.63 restored map visibility. Final iPhone confirmation of the 1.6.64 startup/flash fix is still pending.

## Limits / pending approval

- The PWA origin remains the Cloudflare Worker URL. GitHub Pages migration is investigated in `2026-11-06-pwa-url-migration-audit.md` and is not approved for execution.
- An already-running old version cannot be rewritten remotely. A reload/relaunch may be needed once to obtain the update-detection fix; no app-data clearing is required.
- Group withdrawal is not implemented; it requires a later proposal and user approval, after current fixes and URL investigation.
