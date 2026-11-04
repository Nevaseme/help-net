# Demo residents, buildings and anonymous status verification

Verified 2026-11-04. Android 1.5.65 (78), public PWA 1.6.37.

## Delivered behavior

- New venue sessions have 64 scenario residents attached to 12 reviewed PLATEAU buildings: 40 safe, 12 unanswered, 6 SOS, 6 rescued. Audience identities are separate. Existing sessions are not rewritten.
- New sessions store longitude cuts 135.760 and 135.7644, placing four reviewed buildings in each of the three demo neighborhoods. Old sessions retain their original assignment rules.
- Building totals count all linked residents. Identifying resident rows still obey neighborhood/home disclosure rules, including the three-minute unanswered gate. The disclosed row moves to the recorded phone/scenario point, never a home-location fallback.
- Safe/rescued taps show anonymous status. Rescue time and an administrator-only reversal action are available. Reversals append history rather than rewriting signed originals. All concurrent active rescue heads are reversed; the underlying answer becomes visible again.
- Exact English display aliases cover the 12 reviewed addresses. Other addresses and free text remain unchanged. Residence floor and building storey count are distinct.
- Public comparison pages and tab layout were not changed.

## Evidence

- JavaScript and server automated tests: 305 passed, 0 failed (`output/demo64-final-js.log`). Includes cross-author authorization, delayed-reference retry, same-group restrictions and multiple rescue reversal persistence.
- Android unit tests: 289 passed, 0 failed. Debug app and instrumented test builds passed (`output/demo64-android-final.log`).
- Vivo V2417A, serial ending 00JU: updated to final APK; `BuildingStatusPrivacyDeviceTest` passed both tests (`output/demo64-vivo-final.log`). Production detail components were exercised using in-memory people, without changing saved group/profile/home data. Checks cover hidden identities, building counts, three-minute disclosure, phone-position callback, rescue timestamp, and administrator-only undo callback. This is component-level device verification, not a multi-phone radio test.
- Public PWA: eight end-to-end flows passed (`output/demo64-public-recheck/result.json`): group/name/safety/map, 66 total including two test attendees, report privacy, injury update, Japanese/English and 320px layout, remote update, offline restart/report persistence, offline SOS recovery and next-session isolation.
- PWA regressions: 115 baseline assertions / 53 screenshots; 22 permission assertions; dedicated status/building browser suite and existing web1617 flow passed. Safe/rescued personal details stay suppressed.
- The first public browser attempt opened a summary before advancing the fixture clock; its injury selector remained pre-disclosure. Reopening the summary and selecting a known same-neighborhood resident fixed the test; no production permission was loosened.
- The first device component launch waited on the launcher; foregrounding PlanD before instrumentation allowed the two tests to run. It was aborted before the final successful run, not counted as success.

## Publication and limits

- Worker deployment: `61db5e28ccf44ec7b57ae2fbc9289b71`.
- Worker SHA-256: `bbb8c3e69fe9c20eb7d0cda48a04c4aa9d0c86e079c86e1dacda5f5a3625ff46`.
- Final Android APK SHA-256: `ba85b558ea5999585a4d74b79dcac956fba77a2b8c76a11f4e2607e525ba0921`.
- Public URL: https://tasukeai-sync.tomodachi-takumi.workers.dev/
- Existing ongoing demos keep their original population; start a new demo to use the 64-resident scenario. No user's ongoing session was ended automatically.
- English aliases are reviewed display romanizations, not nationwide address translation or new address evidence. Real building attributes missing from the source remain unknown.
- This change does not claim a new iPhone hardware test or multi-device offline radio test; browser offline recovery and the vivo detail components were verified as described above.
