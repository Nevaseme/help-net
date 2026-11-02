# Shared HTTPS journal, version 1

All API responses are JSON and `Cache-Control: no-store`. Base path `/api/v1`.
Group IDs are opaque UUIDs from the client's random create requestId. The submitted `deviceId` is the client's **existing installation UUID**; creating/joining never replaces local record identities. Every authenticated request sends `Authorization: Bearer <token>`. Before create/join, the client securely generates and durably stores a random 32-byte `registrationToken` encoded as 64 lowercase hexadecimal characters, plus the pending request. This becomes the bearer token; only its SHA-256 hash is stored on the server. Never put credentials in URLs.

## Membership

- `GET /info` returns `{service:"tasukeai-sync",version:1}` without authentication.
- `POST /groups` `{requestId, deviceId, activityName, groupName, registrationToken}` creates a group and its ADMIN; groupId equals requestId. Retry the identical pending request after response loss.
- `POST /groups/{groupId}/invitations` authenticated ADMIN creates a reusable 24-hour invitation: `{code, expiresAt}`. Code is a random secret, shared alongside groupId. There is no A/B/C or participant-count restriction.
- `POST /groups/{groupId}/join` `{deviceId, activityName, code, registrationToken}` adds a MEMBER. Create/join retries with the same deviceId and registrationToken return the original membership, even after invitation expiry. An already registered deviceId with a different token is rejected, never reassigned or given a replacement credential. Lost credential recovery, member removal, role changes and adding a second device to one member are outside this version.
- Create/join return `{groupId, groupName, deviceId, memberId, role, token, rosterRevision, roster, rosterHistory}`.
- `GET /groups/{groupId}/roster` returns `{groupId, groupName, rosterRevision, roster, rosterHistory}`.
- `roster` retains the existing `{version:1,groupId,members:[{memberId,activityName,role,deviceIds}]}` schema. `rosterHistory` is an ascending array of immutable `{revision,roster}` snapshots, beginning at 1. Joining appends a member and a revision; existing identities and roles never change.

## Durable synchronization

- `POST /groups/{groupId}/records` body `{records:[{collection,record,rosterRevision?}]}` (1–100 records, at most 256 KiB request). Collections: residents, events, incidents, places, positions, memos, quakes, responses. Raw record IDs and values remain unchanged after schema normalization. FORM records may carry `record.rosterRevision`; this must match the envelope revision if supplied.
- Atomic batch: every row commits or the entire batch rolls back. Response `{acks:[{collection,key,cursor,status,rosterRevision}],rosterRevision}` is returned only after durable storage completes. `status` is `stored` or `duplicate`. Retries of an identical record return its original cursor. Any rejected item produces an HTTP error and no ACK; clients retain the entire batch and can retry valid records individually.
- New rows must be authored by the authenticated device: originId (resident/position), authorId (care/incident/place/memo), actorDeviceId (quake/response). Exact copies of already accepted records may be retransmitted by another group member. Unknown unsigned third-party records return 403 `original_author_required`; keep them locally until their original device uploads. This does **not** authenticate the origin of mesh-relayed records.
- `GET /groups/{groupId}/records?after=0&limit=100` returns `{records:[{cursor,collection,record,rosterRevision}],nextCursor,hasMore,rosterRevision}` in server cursor order. Cursor is a nonnegative integer. Advance local cursor only after the whole page is durably merged. Fetch roster/history first whenever the response rosterRevision is newer. Cursor 0 replays the full journal, including old revisions.
- Resident/position key = originId, memo key = answerId; these are revisioned. Event-based collections key = eventId and are immutable. Same ID/revision with different content is 409 `record_conflict`. Older unseen revisions can still be journaled without replacing a newer local view. Memo identity (author/incident/answer) cannot change between revisions.
- Response/place sequence numbers are unique per author within a group; incident sequence numbers are unique per author and original incidentId. A different event using an occupied sequence returns 409 `sequence_conflict`. Exact duplicate event retransmissions remain successful.
- FORM is evaluated against its immutable roster revision. Clients must preserve the revision and use that snapshot for historic quorum, including after new members join. Existing legacy FORM must be associated with its original imported roster, never silently rebound to a larger new roster.
- Synthetic Incident START rows from managed FORM/EEW are derived locally, not uploaded as someone else's record. Upload REPORT/FORM/EEW and own ANSWER/CARE. ANSWER/CARE before their formation proof arrives returns retryable 409 `missing_incident`; retain and resend after proof. The synthetic START author's incident sequence 1 is reserved. Quake END is ADMIN-only. Authenticated device assertions of EEW remain device assertions, not server verification of an official source.

## Errors and operation

Error body `{error:{code,message}}`; 400 invalid request, 401 missing/invalid credential, 403 unauthorized actor/role, 404 unknown group/route, 409 immutable conflict or duplicate device, 413 body too large, 429 signup rate limit, 500/503 storage unavailable. No success ACK on failures. CORS is same-origin by default; deployments can set an explicit comma-separated `ALLOWED_ORIGINS` for separately hosted PWA origins. Native Android does not require CORS.

The separate `tasukeai-sync` Worker uses SQLite Durable Objects, one per group. Existing EEW Worker is unchanged. Static assets are packaged from the shared PWA by the deployment builder. Free usage is bounded by Cloudflare quotas; this contract does not promise unlimited or uninterrupted service. Local records remain the source for offline operation.
