# PWA URL migration audit — not approved for execution

## Scope and actual state

- Authorized and completed: rename GitHub repository to `Nevaseme/help-net`, update the participant guide URL to `https://nevaseme.github.io/help-net/`.
- Not authorized for execution: moving the PWA origin to `https://nevaseme.github.io/help-net/app/`. The PWA remains at `https://tasukeai-sync.tomodachi-takumi.workers.dev/`.
- Existing PWA databases, group records and device identities have not been deleted or moved.
- Group withdrawal is a separate future proposal; do not implement it before the user's approval, and do not prioritize it over this audit and current defects.

## Verified blockers, not hypothetical risks

1. An unmodified copy of the current public PWA under `/help-net/app/` fails to boot. A real Chrome test returned 404 for 15 requested files, including `/app.mjs`, `/leaflet.js`, styles and icons. Its absolute paths resolve outside the repository path.
2. `CloudClient` defaults to a same-origin `/api/v1` endpoint. GitHub Pages cannot run this API. The browser client must retain the current Cloudflare API origin explicitly.
3. Cloudflare connector inspection found no `ALLOWED_ORIGINS` binding. An actual OPTIONS request from `https://nevaseme.github.io` returned 403 without `Access-Control-Allow-Origin`. An explicit origin allowance is required; no wildcard or authentication bypass is proposed.
4. Manifest `id`, `start_url`, `scope`, icon URLs and `/sw.js` registration currently use `/`. The new deployment needs `/help-net/app/` paths. Cached asset matching, workers, fonts, dynamic loading and offline downloads must use the same base.
5. IndexedDB, Cache Storage, local preferences and installed PWA identity belong to the old origin. A new-origin installation cannot simply read them. Users would need to install the new PWA, grant permissions and join or restore their membership there. The old PWA must remain available until migration is verified.
6. Joining again as a new device can consume another participant slot. The current venue limit is 40 devices including the administrator. The existing member-recovery mechanism should be assessed before recommending rejoining to all participants. Do not automatically reset or remove old memberships.

## Repository rename and current incidents

The rename also moved the public city-data path from `/safety_confirm/data/cities/` to `/help-net/data/cities/`. Version 1.6.62 still points at the old location. The user reported that relaunching into 1.6.63 restored the visible map. This is evidence of an old-version/old-data-URL failure, not evidence that reinstalling the PWA damages group records.

Active data references, map manifests, their generator and browser fixtures are being updated. Previously downloaded city data is read by its old cache key as a compatibility fallback; the old network URL is not fetched. Historical comparison screenshots and historical documentation are retained.

## Cloudflare and Firebase checks

- Cloudflare: inspected actual deployed Worker settings and script hash. The existing `GroupJournal` Durable Object binding and rate limiter remain unchanged. PWA asset publication does not recreate or migrate the database.
- Firebase: plugin environment and registered-app list identify project `tasukeai-67320` and Android package `io.github.nevaseme.plandmk1`. The registered app is Android, with the historical console display name `PlanD`. Code uses Firebase Cloud Messaging for Android earthquake notification registration/receipt. No PWA Firebase SDK, Firebase Auth or Firebase Hosting deployment is present in this codebase. Repository and web-path changes do not change the Android package or FCM project.
- Selecting the existing Firebase project for inspection did not create a Firebase project or deploy Firebase services.

## Proposed migration, only after approval

1. Add a deployment base and explicit API base to a locally staged PWA; retain the current Worker deployment.
2. Adapt resource/worker/manifest/cache paths and test at the exact `/help-net/app/` path.
3. Allow only the intended GitHub Pages origin at the existing API, preserving bearer authentication and data stores.
4. Verify new group join, existing-member recovery, earthquake/drill selection, safety updates, map/report taps, city download and offline reload, language, themes, permissions and application updates. Test on iPhone and Android browsers before switching the participant guide link.
5. Publish and switch links only after those tests and the user's explicit approval. Do not state that migration is error-free based on source inspection alone.

## Evidence

- Local investigation: `output/helpnet-migration-audit.json` (unmodified copy failed; production migration not performed).
- GitHub documentation: [Pages is static hosting](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages), [repository rename does not redirect project-site URLs](https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository).
