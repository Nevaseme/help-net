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
