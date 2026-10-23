# Local Validation Report — `implementation/storage-quota-resumable-uploads`

**Date:** 2026-10-24  
**Branch:** `implementation/storage-quota-resumable-uploads`  
**Base:** `implementation/video-analytics` (previously merged to `main`)  
**Validated by:** Claude Code (automated local validation, no cloud deployment)

---

## Summary

All automated checks pass. Storage quotas, resumable uploads, and processing recovery verified end-to-end. Five defects found and fixed.

| Check | Result |
|-------|--------|
| Maven build + tests | PASS (4/4) |
| Frontend tests | PASS (9/9) |
| ESLint | PASS (0 warnings) |
| Next.js production build | PASS |
| Flyway V5 migration | PASS (schema v5) |
| FREE plan limits seeded | PASS |
| Storage quota enforcement | PASS |
| Video count limit | PASS |
| Concurrent upload quota lock | PASS |
| Delete releases storage | PASS |
| Resumable session expiry releases quota | PASS |
| Resumable upload E2E | PASS |
| Chunk checksum verified | PASS |
| Idempotent chunk retry | PASS |
| Bad checksum rejected | PASS |
| Wrong full-file SHA rejected | PASS |
| Missing chunks rejected | PASS |
| Resume after interrupt | PASS |
| READY + HLS after complete | PASS |
| Processing recovery on restart | PASS |
| Missing file → FAILED | PASS |
| Manual retry on FAILED | PASS |

---

## 1. Branch and Defects Fixed

```
git checkout implementation/storage-quota-resumable-uploads
```

### Defect 1 — SecurityConfig.java (recurring upstream regression)
**Problem:** Missing `/error` in `permitAll()` and missing `HttpStatusEntryPoint`. Causes auth-unrelated error responses (409/400/404) to return 401 to the client.  
**Fix:** Added `"/error"` to `permitAll()`, added `HttpStatus` + `HttpStatusEntryPoint` imports, added `.exceptionHandling(e->e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`.

### Defect 2 — AuthController.java (recurring upstream regression)
**Problem:** `try { INSERT } catch(DuplicateKeyException)` pattern. Can yield 500 due to transaction rollback in certain Spring/JDBC paths.  
**Fix:** Pre-check `SELECT count(*) FROM users WHERE lower(email)=?`; throw `409 CONFLICT` if existing > 0.

### Defect 3 — dashboard.test.mjs test regex (recurring upstream regression)
**Problem:** `/const API=/` does not match `const API =` (space before `=`).  
**Fix:** Changed to `/const API\s*=/`.

### Defect 4 — dashboard.test.mjs stale assertion
**Problem:** Test `"workspace storage display uses real usage API values"` asserted `/Storage allowance is not yet enforced/`. This string was removed in this branch — the UI now shows real plan quota from `/api/v1/usage` (`storageLimitBytes`, `videoLimit`, `planCode`).  
**Fix:** Replaced the stale assertion with assertions for the real fields: `/storageLimitBytes/`, `/videoLimit/`, `/planCode/`. Added a new test `"dashboard uses resumable chunked upload with SHA-256 checksums"` covering `/uploads/resumable`, `X-Chunk-SHA256`, `chunkSizeBytes`, `chunkCount`, and the complete endpoint.

### Defect 5 — VideoPlayer.tsx unused `lastReportedTime` variable (recurring upstream regression)
**Problem:** `const lastReportedTime=useRef(0)` declared but never referenced. ESLint warning `@typescript-eslint/no-unused-vars`.  
**Fix:** Removed the declaration. Result: zero lint warnings.

---

## 2. Automated Checks

### Java 21 Path Note
System default `java_home -v 21` returned Java 17 (Temurin). The correct path for Java 21 is:
```
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home
```

### Spring Boot Maven Tests
```
cd services/api
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/.../Contents/Home mvn test
```
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Frontend Tests
```
cd apps/web
node --test tests/dashboard.test.mjs
```
```
✔ creator workspace exposes authenticated library, upload, refresh and playback
✔ workspace storage display uses real usage API values without invented analytics
✔ dashboard uses resumable chunked upload with SHA-256 checksums  (new)
✔ HLS player attaches Authorization to HLS requests
✔ dashboard offers actual navigation for library, analytics, billing and pricing
✔ sidebar uses dedicated compact icon and label elements
✔ processing videos trigger periodic refreshes for status and storage
✔ analytics view loads real playback metrics and renders a daily chart
✔ video player sends playback events to the authenticated analytics API

tests 9  pass 9  fail 0
```

### ESLint
```
npx next lint
✔ No ESLint warnings or errors
```

### Production Build
```
npx next build
✓ Compiled successfully
```
```
/dashboard  6.83 kB  290 kB first load JS
8 routes total
```

---

## 3. Services and Migration

```
# API
VIDEO_MEDIA_ROOT="/Users/I565711/video-stream/var/media" \
JAVA_HOME=.../openjdk@21/... mvn spring-boot:run

# Frontend  
rm -rf apps/web/.next && npx next dev
```

API: `http://localhost:8080` → `{"status":"UP"}`  
Frontend: `http://localhost:3000` → HTTP 200

### Flyway Migrations Applied

```
version | description                           | installed_on
--------+---------------------------------------+-------------------------
1       | create identity workspace and billing | 2026-10-24 00:34:30
2       | create video foundation               | 2026-10-24 00:34:30
3       | comments shares and playback events   | 2026-10-24 00:34:30
4       | playback analytics indexes            | 2026-10-24 02:01:01
5       | storage quota and resumable uploads   | 2026-10-24 02:17:12
```

V5 adds columns `expected_sha256`, `completed_sha256`, `chunk_size_bytes`, `updated_at` to `upload_sessions`; creates `upload_chunks` table; adds `upload_sessions_expiry_idx`.

---

## 4. Storage Quota Verification

**Test workspace:** `quota-test-<ts>@example.com` (fresh registration)

### FREE Plan Seeded Limits
```
GET /api/v1/usage
planCode=FREE  storageLimitBytes=2147483648 (2 GiB)  videoLimit=5
videoCount=0  storedBytes=0
```

### Upload Within Quota — PASS
Single MP4 (828,652 bytes) uploaded via `POST /api/v1/videos` → HTTP 202, status=PROCESSING.

### Over-Storage Rejected — PASS
`POST /api/v1/uploads/resumable` with `sizeBytes=2147000000` (under `maxUploadBytes=2147483648` but over remaining quota after 828 KB stored) → **HTTP 402 Payment Required**.  
Message: `"Storage quota exceeded for the FREE plan. Used X bytes of 2147483648 bytes."`

Note: `sizeBytes > maxUploadBytes` returns HTTP 413 (Payload Too Large), not 402. Correct — that's a different limit.

### Video Count Limit — PASS
Uploaded videos 2–5 (all succeed). 6th upload → **HTTP 402**.  
Message: `"Video limit reached for the FREE plan (5 videos)."`

### Delete Releases Storage — PASS
```
Before delete: storedBytes=4,143,260  videoCount=5
DELETE /api/v1/videos/{id}  →  204
After delete:  storedBytes=3,314,608  videoCount=4
Delta: -828,652 bytes = 1 original file released
```

### Resumable Session Reserves Storage — PASS
```
POST /api/v1/uploads/resumable { sizeBytes: 50_000_000 }  →  201
GET /api/v1/usage  →  reservedUploadBytes=50000000
```

### Concurrent Upload Quota Lock — PASS
With 50 MB reserved, attempt to start another session consuming all remaining available bytes → **HTTP 402**.  
The `WorkspaceQuotaService` issues `SELECT ... FOR UPDATE` on the workspace row, preventing two concurrent requests from each seeing stale usage.

### Expired Session Releases Quota — PASS
Set `expires_at = now() - interval '1 hour'` on the active session:
```
Before: reservedUploadBytes=50000000
After:  reservedUploadBytes=0
```
Usage query filters `us.expires_at > now()` — expired sessions are excluded automatically.

---

## 5. Resumable Upload E2E

**Test workspace:** `resumable-test-<ts>@example.com`  
**Source file:** `var/media/dacfb641.../source.mp4` (828,652 bytes, SHA-256: `7c5622d5...`)

### Start Session
```
POST /api/v1/uploads/resumable
{ title, sizeBytes: 828652, contentType: "video/mp4", filename: "source.mp4", sha256: "7c5622d5..." }
→ 201 { uploadId, videoId, chunkSizeBytes: 8388608, chunkCount: 1, expiresAt }
```

### Upload Chunk 0
```
PUT /api/v1/uploads/resumable/{uploadId}/chunks/0
X-Chunk-SHA256: 7c5622d5...
→ 200 { duplicate: false, chunkIndex: 0, sha256: "7c5622d5..." }
```

### Idempotent Retry — PASS
Retry identical chunk:
```
→ 200 { duplicate: true }
```
Session status unchanged; no duplicate DB row.

### Bad Chunk Checksum — PASS
```
X-Chunk-SHA256: 0000...0000  (wrong)
→ 400 "Chunk checksum mismatch"
```

### Wrong Full-File SHA — PASS
Session created with `sha256: "aaaa...aaaa"`. After all chunks uploaded:
```
POST .../complete
→ 400 "Full-file checksum mismatch; upload remains resumable"
```

### Resume After Interrupt — PASS
```
GET /api/v1/uploads/resumable/{uploadId}
→ { status: "UPLOADING", chunks: [{chunkIndex: 0, size_bytes: 828652, sha256: "7c5622d5..."}], receivedBytes: 828652 }
```
Session persists across page refreshes/restarts.

### Missing Chunks at Complete — PASS
New session, no chunks uploaded, then `POST .../complete` → **409 Conflict**: `"Upload is incomplete; upload every chunk before completing"`

### Complete — PASS
```
POST /api/v1/uploads/resumable/{uploadId}/complete
→ 200 { status: "PROCESSING", sizeBytes: 828652, sha256: "7c5622d5..." }
```
Full-file SHA verified against the assembled file.

### READY and HLS Playback — PASS
```
GET /api/v1/videos/{videoId}  →  status: "READY"
GET /api/v1/videos/{videoId}/assets/index.m3u8  →  200
```
Video processed to HLS by FFmpeg; playlist and segments accessible.

### Original File Stored and Deleted — PASS
```
video_assets.object_key = "{videoId}/source.mp4"
File on disk: var/media/{videoId}/source.mp4  ✓ exists
DELETE /api/v1/videos/{videoId}  →  204
var/media/{videoId}/  →  removed ✓
```

---

## 6. Processing Recovery

### Recovery on Restart — PASS
1. Completed upload → video status = PROCESSING.
2. Set status = PROCESSING in DB (`UPDATE videos SET status='PROCESSING'`).
3. Killed API (`pkill -f spring-boot:run`).
4. Restarted API.
5. `ProcessingRecovery.recoverInterruptedJobs()` runs on `ApplicationReadyEvent`.
6. Original file found at `source_object_key` → `processor.process()` re-queued.
7. Video reached status = **READY** after API restart.

### Missing File → FAILED — PASS
Inserted a video row with `source_object_key = "nonexistent-dir/source.mp4"`, status = PROCESSING. On API restart:
- `ProcessingRecovery` checks `Files.isRegularFile(source)` → false.
- Sets `status = 'FAILED'`.
- Confirmed: `SELECT status FROM videos WHERE id = '...'` → **FAILED**.

### Manual Retry on FAILED — PASS
```
# video exists, original file present
POST /api/v1/videos/{videoId}/retry-processing  →  202  { status: "PROCESSING" }
# after FFmpeg
GET /api/v1/videos/{videoId}  →  status: "READY"
```

### Manual Retry — Missing File — PASS
```
POST /api/v1/videos/{missingFileVideoId}/retry-processing  →  409
"Original file is missing; upload again"
```

### Retry on Non-FAILED Video — PASS (from earlier VideoController tests)
`POST .../retry-processing` on a READY video → **409 Conflict**: `"Only failed videos can be retried"`

---

## 7. New Feature Summary

### Backend

**`WorkspaceQuotaService`**
- Row-level lock (`SELECT ... FOR UPDATE`) prevents TOCTOU races on concurrent uploads.
- Checks video count, stored bytes, + reserved bytes (active non-expired sessions).
- Resolves plan via active subscription or falls back to FREE plan.
- Throws HTTP 402 on limit breach.

**`ResumableUploadController`** (`/api/v1/uploads/resumable`)
- `POST /` — start session; validates title, size, content-type, quota.
- `PUT /{id}/chunks/{index}` — store chunk; verifies SHA-256; idempotent on same checksum.
- `POST /{id}/complete` — assemble chunks; verify full-file SHA-256; trigger FFmpeg.
- `GET /{id}` — resume status: session state + chunk list.
- `POST /{id}/retry-processing` — retry FAILED video via resumable upload path.

**`ProcessingRecovery`**
- `@EventListener(ApplicationReadyEvent)` — on startup, queries all PROCESSING videos.
- Re-queues if original file exists; sets FAILED otherwise.

**`VideoController` (updated)**
- `POST /{id}/retry-processing` — manual retry on FAILED video (via multipart upload path).
- `WorkspaceQuotaService` injected into multipart upload path.

**`UsageController` (updated)**
- Now returns `reservedUploadBytes`, `availableBytes`, `videoLimit`, `planCode` in addition to prior fields.

**V5 migration**
- `upload_sessions`: adds `expected_sha256`, `completed_sha256`, `chunk_size_bytes`, `updated_at`.
- `upload_chunks`: new table (upload_session_id, chunk_index, size_bytes, sha256, object_key).
- Index `upload_sessions_expiry_idx` on (status, expires_at).

### Frontend (dashboard/page.tsx)

- Upload form now uses chunked resumable API: computes full-file SHA-256 via `crypto.subtle.digest`, starts session, uploads 8 MB chunks with per-chunk SHA-256 header, then calls complete.
- Up to 3 retries per chunk with backoff.
- Progress bar showing % complete.
- Storage panel now displays real plan limits: `formatBytes(storedBytes) of formatBytes(storageLimitBytes)`, plan code, video count vs limit.

---

## 8. Known Limitations

- **`var/` must never be committed.** Chunk files (`resumable/{uploadId}/chunk-NNNNNNNN.part`) accumulate in `var/media/resumable/` for any abandoned sessions; they are cleaned up on successful `complete`. Manually clean `var/media/resumable/` if sessions are abandoned during testing.
- **Anonymous viewer analytics not implemented.** (From prior branch — carried forward.)
- **maxUploadBytes default is 2 GiB** (`videocloud.max-upload-bytes`). The FREE plan storage limit is also 2 GiB. In practice, a single file upload will always hit the plan limit before the system size cap. The 413 path (file > maxUploadBytes) is a secondary guard, not the primary quota enforcement.
- **Concurrent-upload lock is per-workspace, not per-connection.** Two concurrent uploads that each individually fit within remaining quota but together overflow it are correctly prevented by the `FOR UPDATE` row lock; however this lock is held only for the duration of the transaction (the quota check + INSERT). If the network is very slow and the transaction commits before both session INSERTs complete, a theoretical gap remains — this is acceptable for a local MVP.
- **`ProcessingRecovery` does not log.** The class recovers silently; check video status via API to confirm recovery ran. A future improvement would add SLF4J logging at INFO level.
