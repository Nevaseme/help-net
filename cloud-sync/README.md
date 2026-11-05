# Tasukeai shared server

The independent `tasukeai-sync` Cloudflare Worker provides HTTPS PWA assets and a durable per-group journal. It does not modify or call the deployed `pland-eew-relay` Worker. The full client contract is in [CONTRACT.md](CONTRACT.md).

## Published instance (2026-10-30)

Public PWA and API: https://tasukeai-sync.tomodachi-takumi.workers.dev/ . Published through the connected Cloudflare plugin as a separate Worker on the existing Free account; no plan or existing EEW deployment change. Current standalone deployment: `d4e964f0e4764b4c84289c19911ff745` (168,291 bytes gzip), PWA1.5.2. Includes fixed Atami coastal3m depth maps and registration guidance. Public map/offline tests and registration-only → offline profile → resident/building controls passed. Existing two-browser sharing lifecycle also passed locally after this change. The earlier PWA1.5.0 deployment passed the same lifecycle against the public service.

The public HTTPS flow was tested with two independent browser contexts: create/invite/join, durable profiles, manual formation, SOS, offline answer plus reload, reconnect, administrator END. A separate instrumented Android client exchanged real server records with the PWA and verified both answers and END after reopening Room. iPhone Home Screen execution requires its own device confirmation and is not implied by Chromium success.

## Local verification

Use Node 24 (the persistence tests use `node:sqlite`).

```sh
npm ci
npm test
npm run check
```

`npm run build` copies only public files from `../lab/web` to ignored `dist/public`, shared validation modules to `dist/shared`, and backend modules to `dist/src`. It reads no local lab configuration, certificates or tokens. `npm run check` builds and performs a Wrangler dry run; it does not deploy. `npm run dev` starts the local Worker. Actual publishing is a separate authorized operation.

For the connected Cloudflare plugin's single multipart upload, run `node scripts/build.mjs --standalone`. It produces `dist/standalone.mjs` with the small static PWA embedded and `dist/standalone-metadata.json` for a **first** deployment. This variant needs only `GROUPS` and `SIGNUP_LIMITER`, not `ASSETS`; it applies the same no-store API and restrictive static headers/CSP. Confirm metadata against the upload API before publishing; the initial `v1` migration must not be reapplied as a new migration on later deployments. The script refuses a bundle over 3 MiB gzip. Rebuild after any PWA changes.

Tests exercise file-backed SQLite restart, cursor replay, deduplication, atomic batch rollback, durable-flush failure, actor/role/group isolation, more than three members, immutable roster snapshots, delayed FORM after membership growth, concurrent END/corrections and registration retries after a lost response. A workerd/Miniflare integration test runs real Worker RPC and SQLite, restarts the runtime and checks isolation. Its compatibility date is October 23 to match the installed runtime; deployment configuration uses October 29 UTC (October 30 in Japan).

## Deployment

`wrangler.jsonc` creates only the new Worker and SQLite DO class `GroupJournal`, binding `GROUPS`, with migration `v1`. It packages static PWA assets under the same origin and sends `/api/*` through the Worker first. Existing EEW resources are not part of this configuration. No account ID, API token, private key or application credential is committed. Authenticated API responses are never cached. Optional `ALLOWED_ORIGINS` must list exact additional PWA origins; same-origin requires no setting.

Each group is a separate durable object. SQL and transaction rollback guard records and roster updates. The server awaits `storage.sync()` before returning a success ACK; on storage failure the client retains and retries the same records. Read-only replay is cursor-based and does not need the original sender online. Registration secrets are generated and saved on the client before the request, allowing the same request to recover after response loss without issuing another credential. The server stores only their SHA-256 hashes.

Signup/join has a per-IP 30-request/minute rate limiter to reduce accidental/abusive group creation. This is a request rate limit, not a participant limit. Cloudflare Free quotas still apply, and exceeding them can stop requests; the app must preserve offline records and pending requests. Public group creation has no identity-provider or account-recovery flow in this version.

## Intentional boundaries

- Membership adds an invited MEMBER and immutable roster revision. No member removal, promotion, device reassignment or recovery UI is implemented here.
- A credential proves access to a registered device identity, not the identity of a real person. Device-reported EEW evidence is not independently checked against the official upstream by this server.
- New unsigned third-party mesh records cannot be uploaded by a different device. Already stored identical records may be retransmitted. `original_author_required` must remain a visible pending state, never delete local data or mark it remotely saved.
- A new cloud group does not migrate records from a different group ID. Existing offline groups and their records remain local. Legacy FORM without an original roster revision returns `roster_revision_required`; it cannot silently acquire the new group's quorum.
- Upload managed quake records; clients derive synthetic Incident START/END views. The server accepts own ANSWER/CARE after the formation proof is stored, without requiring an uploaded synthetic START row. It reserves the derived START author's sequence 1 to prevent a journal that receivers cannot merge. Record sequence scopes match the Android/PWA receivers. Response correction authorization resolves merged incident aliases while preserving original record IDs in storage.
- Unknown dependencies (for example FORM before referenced reports) return an error with no ACK. Retain and retry after the dependencies arrive. Never advance a pull cursor before the complete page is durably merged.
