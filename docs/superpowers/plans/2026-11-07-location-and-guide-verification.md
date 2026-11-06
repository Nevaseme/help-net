# Help Net 1.7.5: location and participant guide verification

- Android and PWA About text no longer explains ANUGA work that is separate from the displayed calculation. The visible method remains the shallow-water calculation and precomputed Atami results.
- Android now requests foreground location access for a joined group member, including Kyoto participants without a regular resident profile. Previously the UI accepted the derived venue profile while the permission controller accepted only ordinary profiles or ACTUAL placements; this mismatch made Start recording do nothing.
- Kyoto recording retains assigned coordinates and saves the actual GPS acquisition timestamp. ACTUAL mode retains the measured coordinates. A joining member waits for venue/profile context before saving positions.
- PWA joining before an event requests location without blocking registration. An already active event uses its existing location watcher. Denying permission still permits safety submission and map access.
- The participant guide supports manual iPhone/iPad, Android, and PC/Mac selection through an expandable control. Other-browser installation fallback instructions and a copy-link button are available. Android APK remains first and recommended.

## Verified

- Android: 4 VenueLocationTimeTest and 5 PositionRecoveryTest checks passed; assembleDebug and assembleDebugAndroidTest passed.
- Physical Android: updated to 1.7.5; location saved with assigned Kyoto coordinates and a measured timestamp; Stop then Start recording returned to active recording.
- Isolated on-device database: VenuePositionStorageTest passed (joined member without profile, assigned coordinates, measured time, older measurement rejection, nonmember rejection). The test does not change real participant records or OS permissions.
- PWA local and public assets: About in EN/JP, no ANUGA sentence; joining before and during an event; permission denial followed by safety submission and map display; startup screen and no initial map flash.
- Existing Kyoto browser position test passed: successful GPS updates real timestamps while retaining assigned coordinates; denied acquisition leaves the timestamp unchanged; visibility return restarts acquisition.
- Guide local: eight user-agent profiles passed device detection, manual device selection, copy/fallback, EN/JP, and layout checks. These Chromium profiles do not certify installation in each actual mobile browser.

## Deployment

- PWA 1.7.5 deployed to the existing Worker URL. Four frontend assets changed; server code, bindings and stored groups were retained. Deployment: ed8730cd7a5e41459608b84f1ff75f62.
- Android 1.7.5 APK published to the existing GitHub Releases repository and installed on the physical test device.
- Participant-guide publication and public browser checks are recorded after this document's initial commit.

## Group naming

No group was ended, renamed or recreated during these checks. Name lookup applies NFKC normalization, trimming and lowercase conversion; Tomodachi and TOMODACHI are the same lookup name. A new group needs a genuinely different name. The requested name-reuse change is cancelled pending any new instruction.
