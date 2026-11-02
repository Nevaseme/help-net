# Home selection UX and Mi 9T verification

Scope: targeted Android/PWA home registration fixes. Preserve tabs, comparison HTML, data and sensor WIP.

Audit of current public1.6.22: profile has competing home-selection entries; Android address field only edits a label while appearing searchable. Registry combines creation/global search with unrelated care explanations. Picker lacks current-location/explicit clear; keyboard dismissal inconsistent. Native registry input is multiline. Current-run screenshots: visualization home-ux/01-profile-before.png and02-buildings-before.png.

Design: preserve existing fonts (system Japanese/Noto Sans JP), type scale, left alignment. Existing palette: paper #FFFFFF, text #183447, muted #536478, action #155EEF, border #D6E0E8, dark surface #152A34. Controls >=48dp/px. No decorative motion or new visual identity.

```
住居
[住所・建物名を検索          ×]
[住所を検索] [地図で選ぶ]
選択した住居: …
登録済みの建物・部屋から選ぶ
[階] [寝る部屋・場所]
[登録情報を保存]
```

Registry filters already-shared buildings; separate action adds a building through the same picker. Search input is not the confirmed address. Map includes clearable search and current-location button. Dismiss IME on search, map gesture, locate and confirmation. Confirmed address uses Done. Permission denial/failure leaves pin intact; search/locate/cancel never saves. Review: simplify functional hierarchy, no generic cards or branding changes.

Validation: emulator/vivo IME, clear, map gesture, location success/failure, cancel preserves home; PWA equivalent with mocked geolocation/narrow view and publish. Mi9T final APK copied for manual install, then disposable database/group real NearbyTransport/SharingSession bidirectional offline home/provenance/reopen test. Preserve memberships, restore radio settings, distinguish two-device evidence from multi-hop/20devices.


## Results (2026-11-03)

- Android1.5.48 (61): production APK installed on vivo and manually installed on Mi9T. App JVM tests/build passed. Emulator HomeUxTest2/2 passed; vivo AddressPickerDeviceTest1/1 passed (21.809s): rendered Kyoto map,203 address, visible selection pin, cancel preserves previous records.
- PWA1.6.23 published to existing tasukeai-sync Worker, deployment852d00ca8f8340ce93d5552bad338170. No backend binding/migration/price-plan changes. Public UI confirms new controls, Enter search and clear. Saved screenshot home-ux/08-public-picker.png. Existing comparison pages/tabs unchanged.
- Node suites239/239 passed. Dedicated browser test passed search/clear/locate/Done keyboard behavior, confirmed-address preservation, registered-building filtering,320px width. Location success uses injected browser position; this does not constitute a fresh iPhone GPS/keyboard test.
- Dark-theme search/locate icons checked; programmatic building address changes refresh clear-button visibility.
- First radio attempt invalid: both devices initially had no default network, but vivo later had a validated Wi-Fi network. No offline success claimed. Stopped and restored Wi-Fi/data/Bluetooth.
- Test activity startup stalled; diagnostic harness removed the unnecessary activity launch and now reaches Nearby discovery. Vivo single-device diagnostic waits then times out as expected without a peer.
- Mi9T test harness v2 installed manually. Fine location authorized by user and remains granted. User now controls radio and Wi-Fi auto-connect settings; do not modify or restore these automatically. Read-only monitored rerun pending Mi9T internet disconnection. Vivo no-default-network confirmed; Mi9T still had default network105 at last preflight, so no transfer test started.


### Completed two-device radio check

2026-11-03 around06:36 JST: Mi9T and vivo each passed AddressNearbyDeviceTest1/1 with production NearbyTransport+SharingSession and disposable Room databases. Bidirectional initial home receipt and subsequent changed-home receipt passed; old unit/floor/room links were absent, searchReference stayed intact, and closing/reopening the database retained the received home/profile. This is a real two-device radio test, not a fake port. It does not claim a three-device relay or20-device physical test.

Both devices had no active default network before the run and throughout approximately2-second polling through the run. Logs: .implementation/home-radio-manual/*-test.log and *-network-timeline.txt. No HTTP client is used by the fixture. All four checks (initial/updated at A/B) observed around06:36:33–34 JST. App1.5.48 and test harness v2 on both phones. The last run issued no radio/autojoin setting changes: user handled disconnection and retains control of reconnection. Location permission stays granted as authorized.

Earlier attempted Wi-Fi automation was explicitly stopped at the user's request. Do not run vivo-autojoin.py or older radio scripts without new authorization; home-radio-run-manual.py reads network state only.


## Follow-up: camera regression and safety/report radio test

Android1.5.49(62) installed on vivo. Profile search now resolves the candidate before opening the picker; multiple candidates remain on profile until selected. Previously it immediately opened the old registered location (Tokyo in this device) while asynchronous lookup ran. Search through the exact profile-button route passed on vivo before and after this change; the old implementation could show the old location during loading even when it eventually reached Kyoto.

Confirmed independent camera regression: after locating, moving the map, and locating to the same coordinates, the old center-only Compose effect did not run. HomeUxTest failed at the second locate camera assertion. Added an explicit center request counter; now3/3 HomeUxTests pass (53.859s). Map initialization/programmatic idle notifications no longer erase the searched address: only gesture completion/map taps feed picker movement. Camera requests replay after style readiness. vivo profile-button search/cancel test passed20.595s. Actual vivo current-location button moved the map; captures09-current-before.png and10-current-after.png stay local. Closed without saving.

OfflineSafetyRadioDeviceTest added: real SharingService/NearbyTransport/Room, isolated temporary group, both-device SAFE/report creation, screen-off SOS/report receipt, queued SAFE/report after disconnection, automatic reconnect and independent database-reader verification. Native radios are controlled by user only. Fixture backs up/restores selected group and prior automatic-sharing group; test GPS factory supplies fictional coordinates. Existing real records remain. Production service foreground startup and explicit abort cleanup verified on emulator; deliberate preflight abort is expected test failure and is not a radio pass.

Mi9T was disconnected by user for PC charging before the new two-device safety/report test began. Accordingly safety/report radio, screen-off reception and automatic reconnect have NOT yet been verified with this pair. On resumption rebuild/copy latest communication-test-v3 APK (latest fixture adds fake GPS and seeded data preflight), verify Mi9T normal app1.5.49 and both devices no default internet network; run .implementation/safety-radio-run.py. Wait initial-pass, ask user to turn off both displays, issue `screen`; wait screen-pass, request vivo Bluetooth off, issue `disconnect`; wait queued, request Bluetooth on with app unopened, issue `reconnect`; after both all-pass issue `finish`. Do not change radio/autojoin settings automatically. Logs/backup handling and offline monitoring are required; distinguish this from previous address-only pass.

The optional registered-building entry lists buildings/units created within the current group (including own prior registrations), not a separate address search. It allows residents of the same apartment building to associate with the same building/unit records. Ordinary home registration can use address search alone. No label/layout redesign was made in this follow-up.
