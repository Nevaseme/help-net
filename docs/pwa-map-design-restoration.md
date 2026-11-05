# PWA map interaction and saved-design restoration

Published version: PWA 1.6.30. Scope: `lab/web` production PWA. The comparison page and Android app are unchanged by this patch.

## Reference and findings

Read the referenced chat `01a1730a-acec-7d41-8e9b-4af7419e2c8b` and its saved Web 1.6.17 design: `docs/superpowers/plans/2026-10-30-web-1617-design-baseline.md`, component specification, source archive, screenshots 08/16/17, and original observation assets.

- Previous PWA implementation used `!` and `▣` instead of the agreed collision and enclosed-person assets. Restored original images, corner badges, paired observation cards, and legend images.
- Neighborhood markers omitted safe/rescued counts. Tapping opened the regional report screen rather than the agreed summary. Restored four counts, open high-priority report count, material-shortfall indication, zoom action, and a separate aggregate-only materials detail sheet. Regional reporting under Tasks remains available.
- Risk footprints were only painted. Leaflet did not connect map clicks to building selection. Added precise rendered-feature selection (individual footprints only), with verified local/city geometry fallback. No nearest-building inference. Duplicate tile fragments are deduplicated; overlapping buildings offer a choice.
- Building sheet now shows year, structure, storeys, and residents linked through current HOME/BUILDING records. Phone proximity does not establish residence. Current disclosure and neighborhood filters apply. Person details link back to the registered building.
- Later approved compact header, inline tsunami controls, tabs and home/phone separation remain intact.

## Verification

- Before fix, browser test clicked an actual building polygon but no building detail sheet appeared (7-second timeout).
- Node tests: 192 passed, including tile selection, home-link changes and open urgent-report aggregation.
- Browser test `lab/test/building-map.browser.mjs`: passed locally and on production, using a separate fictional test group (demo ended afterward). Actual roof tap → attributes/resident → person → building; neighborhood summary/detail; original sensor image paths; Japanese/English; 320px width; no page errors or visible JS error banner.
- Regression `lab/test/demo-map.browser.mjs`: passed, including home move with unchanged SOS location, synchronization, offline restart and simulated observations.
- Regression `lab/test/community-demo.browser.mjs`: passed, including group/password → name → safety → map, 14 people, building links, injury update, regional report, offline SOS delivery and second-drill isolation.
- Chromium mobile viewport: 390×844 and narrow 320px. Browser plugin unavailable; used existing authorized isolated Playwright workflow. No new physical iPhone or native Android testing in this patch.
- Screenshots saved outside repository under `C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/building-map-public/`.

## Deployment

Cloudflare plugin uploaded the verified standalone bundle to existing `tasukeai-sync`, preserving bindings and settings without database migration. SHA-256: `9bcb0e0d50b82d6a603dbc0b4d5a9f27d6a75cd93221f903e4d0cfd23f973ab9`.

Public URL: https://tasukeai-sync.tomodachi-takumi.workers.dev/
Cache version: `pland-lab-v1.6.30-r53-building-details`.
