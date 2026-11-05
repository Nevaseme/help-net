# Person detail restoration — 2026-11-04

User reference: attached person-detail screenshot and the previously adopted Web 1.6.17 design record. The previous update restored map/building interactions but had not restored the complete person-detail composition; its completion description overstated the scope.

## Changes

- PWA 1.6.31: status symbol beside name/status; compact last-phone-position line; usual home/sleeping-area heading; address with View building action; bedroom panel; age/structure/storeys in three columns; usual daytime-location section; phone observations; rescue/check history; rescue action outside scrolling content in a fixed footer.
- Building attributes are loaded from the same verified PLATEAU building linked to the home. Results are cached per home reference; stale results do not update another person's sheet. Missing information is displayed as unknown/not registered, without importing example data from the screenshot.
- Removed Fictional resident and Simulated demo observations labels from the rendered person detail; removed fictional-count/name annotations from regional overview, injury rows and generated HTML reports. Scenario provenance flags remain internal and are not converted into authenticated real sensor records.
- Android 1.5.59 / code 72 removes the corresponding fictional/simulated labels in person detail and regional overview/report. This is not a claim that the Android person-detail layout was newly rebuilt or visually verified in this patch.
- Later approved compact header and tsunami controls remain intact. Public comparison pages are unchanged.

## Validation

- Added browser assertions first; original UI failed because the person sheet contained the unwanted fictional/simulated labels.
- Node suite: 192 tests passed. Android JVM suite: 263 passed, 0 failures; APK built successfully.
- Local and public `building-map.browser.mjs`: real roof tap → linked resident → person details → building; person header symbol, fixed footer, loaded building attributes; no fictional labels; Japanese/English; readable address width at 390px and 320px; no page errors or visible JavaScript error banner.
- Visual comparison found an address-width defect missed by structural assertions. Fixed action button width, added width checks and repeated screenshots. Observation cards use the original saved icons.
- `demo-map.browser.mjs` passed, including offline restart and unchanged SOS/phone coordinates after home edits.
- Public screenshots: `C:/Users/TAKUMI/.codex/visualizations/2026/10/24/01a16c0c-f86e-7220-ad05-e96afa7746d4/person-reference-public/`.
- No physical iPhone test this turn. Only emulator-5554 was connected; vivo was not updated.

Cloudflare deployment succeeded with existing bindings preserved. Bundle SHA-256: `1c28cc308621306e055906e4cd137915b01f23bb8caeeb5988fb0e8864e6ce98`. PWA cache: `pland-lab-v1.6.31-r54-person-detail-baseline`.
