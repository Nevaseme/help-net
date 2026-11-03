# Search speed and community demo — execution ledger

User instruction, 2026-11-03: finish search/picker performance and UX; then issue regional reports and provide one-screen neighborhood counts, injuries, materials; add fictional residents and reports alongside audience members, all linked to buildings; Japanese/English interface, English-only authored sample content; verify complete demo; periodic GitHub backups, no paid services. No interactive approvals while user sleeps.

## Sequence and completion evidence
1. Search/picker: measure same native cold/warm flow, optimize without query-specific behavior; verify broad deterministic samples and precision; simplify picker, IME/clear/current location; Android/PWA regression and device where possible.
2. Community model/report: immutable issued snapshots with source IDs/time; neighborhood overview, independent injury evidence (never equate SOS with injured), material shortages/unknowns; current records/corrections and offline persistence.
3. Venue scenario: stable session-scoped fictional residents, SAFE/UNREPORTED/SOS/rescued history and reports/materials; PLATEAU homes for sample and Kyoto-assigned audience; normal home data untouched; UI language independent of authored English content.
4. Integration: server/client compatible changes, Japanese/English overview/report, native/PWA joins + answers + map + report + restart/offline tests, simulated audience scale.
5. Backups and publication: source-only checkpoints to codex branch; preserve all prior WIP and sensor work; published PWA after verification; native install only if no human prompt blocks it; no public comparison-page changes.

## Rulings / design
- Existing stack and approved visual style retained. User explicitly opted out of design/permission questions; ordinary implementation choices proceed and are recorded. No change to plan.txt/add.txt.
- Read-only generated address search representation may accelerate Android cold loading, while original source assets/provenance remain unchanged. No hardcoded favorite-address results.
- Picker: existing surface/ink/primary tokens, 20sp title, 16sp body, 48dp actions. Header+search, flexible map, compact selected address with explicit edit, confirmation. Keyboard resizes content instead of panning the header offscreen. Same hierarchy for PWA.
- Neighborhoods for venue data are clearly demo neighborhoods, not assertions about real neighborhood-association boundaries. Source buildings remain real PLATEAU footprints. Regional report is an issued HTML document plus structured JSON snapshot, with Japanese/English UI labels and authored text unchanged.
- Fictional records are scoped to the venue session, visibly identified, do not consume audience login slots, and do not masquerade as authenticated physical devices. Repeated synchronization/restart must not reseed or duplicate them.
- Physical participants' injury information remains unknown unless explicitly recorded; counts distinguish known injured from unknown. No inference from SOS, rescue status, or narrative text.
- Existing archived checkpoint text is historical: address integration, 36 tsunami cases, participant onboarding are already implemented. Sensor monitoring exists, but generic impact classification is not completed and remains outside this task.

## Initial evidence
- Vivo V2417A, debug1.5.49: cold first Kyoto search17,567ms; mixed24 subsequent searches median93.25ms, p95140.68ms, including one initial cross-city load2,914ms. This measures candidates(), not map render/GPS latency. Logs `.implementation/address-speed-before-*`.
- Current picker screenshot: keyboard pans header/search out of view, map ~200dp high, large unused lower surface. No resident data saved during inspection.
- Mi9T disconnected for PC charging; new two-device safety/report/screen-off/reconnect test remains pending, not replaced by browser/emulator simulation.

## Progress
- Baseline measured. Implementation pending.

## Checkpoint 2026-11-03 08:30 JST
- Task 1 implemented: canonical pre-normalized Android binary search packages; original source assets retained. Vivo cold candidates 17,567→3,196 ms; warm median 51.12 ms / p95 80.83 ms (24 searches), versus baseline median93.25/p95140.68. This is candidates(), not total map/GPS time. Broad package tests cover 80 evenly spaced entries per package.
- Picker compact address summary/edit, IME resize, search/clear/current-location checks passed on emulator and dedicated PWA browser. Updated native final UI test pending.
- Community model and issued HTML/JSON snapshots implemented in both clients. Injury remains independent, conflicting injury becomes unknown; concurrent material requests counted once using largest shortfall and conflict label. Reports persist offline; old issued reports immutable.
- 12 session-scoped fictional residents, 3 reports/material requests, 2 rescued histories seeded once. Scene people never occupy authenticated member slots. Demo neighborhoods partition the catalogue at longitudes135.760 and135.7615; these are fictional boundaries. ACTUAL participants stay in a separate Venue participants row.
- PWA browser passed group/password→name→answer→map,14 persons including12 samples/all building links,injury correction,JP/EN,320px,issued report persistence after offline reopen,automatic offline SOS upload after reconnect. `.implementation/community-browser/result.json`.
- Node243/243 passed including40-client Worker test; scoped native unit/build passed. New native device community test and publication pending. Separate final reviewer dispatched per executing-plans skill.
- Ruling: response commands recognize active venue participants/sample identities without writing fictional resident profiles into real-home tables. Membership/incident authorization unchanged; new injury wire17/cloudcapability6 prevents older decoders receiving unknown types.
- Backup1 pushed284b576 to codex/2026-11-03-community-demo. Comparison pages and plan.txt/add.txt untouched.

## Final verification / release
- Reviewer found prior-incident contamination and stale open PWA summary; fixed by current-incident filtering and content-signature updates that preserve disclosures/scroll. Added second-drill and open-summary remote update browser regressions; both passed locally and against published PWA. BUILDING_DAMAGE label corrected separately.
- Final native unit tests259/259; Node243/243. Emulator CommunityDeviceTest + HomeUxTest4/4. Published-service CommunityCloudDeviceTest1/1: imports12 injury records/3 reports, updates injury, old capability5 gets426 without consuming unsupported records.
- Published PWA1.6.24/r46 on existing Worker: deployment86efe242b3814e96acb8cc17f4035f41; bundle SHA25650051861c763b816f07d0fb9980376bfbe68798c9f2e55a492a3cce2daa63b83. Public full-flow browser pass in `.implementation/community-public/result.json`.
- Android1.5.50/code63 APK built. Vivo install rejected while locked (INSTALL_FAILED_ABORTED); no unlock bypass, reboot, repeated prompt or user question. Earlier speed measurement succeeded. Latest physical UI check remains unperformed.
- No new iPhone or Mi9T interactions possible while user sleeps. Multi-device radio safety/report/screen-off/reconnect remains separate unfinished verification, not claimed by simulated/browser tests.
- Completion steps follow finishing-a-development-branch skill with user's explicit backup/no-questions choice: keep named worktree/branch, push checkpoints, no merge or comparison-site changes. User-owned working tree and prior unrelated WIP retained.
- Operation guide and limitations saved in docs/community-demo-2026-11-03.md. No paid service operations.

## Vivo verification after user unlocked device
- Updated normal vivo app successfully to 1.5.50/code63; existing app data retained. Test APK also updated.
- ActivityScenario's automatic activity launch stalled on this OEM. Replaced it in two UI test classes with an explicit instrumentation-shell launch and an empty Compose rule. Select the exact test ComponentActivity, not its MainActivity subclass; the latter caused transient wrong-host failures during the first harness attempt. Product source unchanged.
- Final combined CommunityDeviceTest + HomeUxTest: 4/4 passed in 12.672 seconds (`.implementation/vivo-final-ui.log`). Independent camera test also passed (5.463s), edit/IME test (6.602s), and location-error preservation test (2.016s).
- CommunityCloudDeviceTest against published endpoint: 1/1 passed in 2.131 seconds (`.implementation/vivo-community-cloud.log`). Uses an isolated test database/preferences and fictional server group; not the user's group.
- Verified saved report totals/building links, immutable issued snapshot after injury correction, Japanese/English UI, and actual MapLibre camera movements for search and injected current-location coordinates. GPS accuracy itself was not tested. Screenshot saved privately under `.implementation/community-overview-vivo-en.png`.
- Community test restores original group in finally; home tests do not save residence changes. Normal MainActivity reopened after tests. No reboot or radio-setting changes.
- Mi9T two-device radio tests and iPhone-specific community UI validation remain separate pending checks.
