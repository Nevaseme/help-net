# Help Net icons and participant guide

## Authorized scope

- Preserve the supplied mark, package it on white for Android and PWA launchers.
- Rename GitHub repository `Nevaseme/safety_confirm` to `Nevaseme/help-net`; serve the participant guide at `https://nevaseme.github.io/help-net/`.
- Show Tomodachi / Boeing before platform instructions. English first, EN / JP switch. iOS: PWA and Home Screen instructions. Android: browser/PWA and APK choices.
- PWA origin migration to GitHub Pages is **not approved**. Keep the current Worker URL and existing device records. Investigate migration separately and seek approval before implementation/deployment.

## Implementation

- Original mark: `image/branding/help-net-mark.png`, SHA-256 `5588f8cee11cf9d6521f8e22e3764cfaa4db28df2eb8982cdffdf7ad63c6397e`.
- `tools/build-app-icons.mjs` packages the original mark into Android adaptive/legacy assets, PWA 192/512 PNGs, maskable PNG and iOS touch icon. The generated design preview is not used as source artwork.
- Android 1.5.88 / 101; PWA 1.6.63 / r109. No database, identity, membership or application-package changes.
- `demo/help-net/` holds the English/Japanese participant guide. `tools/build-pages.mjs` produces the website root and retains the previous comparison entry as `comparison.html`; all historical comparison assets remain.
- Repository rename changes the city dataset URL. Update `city-addresses.mjs` and service-worker recognition to `/help-net/data/cities/`.
- APK is distributed as a GitHub release asset, never a Git-tracked binary. The guide links to Android 1.5.88.

## Verification so far

- Android `:app:assembleDebug`: passed. Installed on the existing device with `adb install -r`; package version confirmed as 1.5.88 / 101.
- Guide tested in Chrome at 320, 390 and 900 pixels: English default even with Japanese browser locale; EN / JP switching; preference persistence; no horizontal overflow or JavaScript errors. Screenshots in local `output/helpnet-guide/`.
- GitHub repository rename: verified stable repository ID 1425879692 and new full name `Nevaseme/help-net`.
- Publication, APK download verification and PWA public verification will be recorded after they complete.

## Documentation discrepancies

- The old participant entry claimed Android distribution was pending and redirected iOS without instructions. The new guide replaces that behavior.
- `2026-10-31-audience-join-portal.md` describes a deferred invitation/deep-link draft, not the implemented name/password venue entry. This change uses the actual group-name/password flow.
- Historical documentation and comparison captures may still contain `safety_confirm`; they describe historical releases. Active publishing and city-data references use `help-net`.

## Instruction sources

- [Apple: Add a website to the Home Screen](https://support.apple.com/guide/iphone/open-as-web-app-iphea86e5236/ios)
- [Google: Use web apps on Android](https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DAndroid&hl=en)
- [GitHub: Renaming a repository](https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository) — project-site URLs are not redirected by a repository rename.
