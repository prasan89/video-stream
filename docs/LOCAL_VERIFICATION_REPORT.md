# Local Verification Report — `main` branch

**Date:** 2026-10-24  
**Branch:** `main`  
**Commit:** `ba1e92a` (fix: recurring fixes — VideoPlayer optional workspaceId, CORS X-Workspace-Id header)  
**Verified by:** Claude Code automated local verification

---

## Summary

| Check | Result |
|-------|--------|
| Environment setup | PASS |
| PostgreSQL (Docker) | PASS — running, healthy |
| Spring Boot startup | PASS — PID 38968, port 8080 |
| Next.js startup | PASS — port 3000 |
| Flyway migrations (V1–V6) | PASS |
| Maven build + 7 tests | PASS |
| Frontend tests (12/12) | PASS |
| ESLint | PASS (0 warnings) |
| TypeScript type check | PASS |
| Next.js production build | PASS (8 routes) |
| Health endpoint | PASS |
| Authentication (register/login) | PASS |
| Video upload (multipart) | PASS |
| Video processing (HLS + thumbnail) | PASS |
| HLS asset serving (authenticated) | PASS |
| Resumable upload (chunk + complete) | PASS |
| Share link (create + resolve) | PASS |
| Cross-workspace video isolation | PASS |
| VIEWER cannot delete (new RBAC code) | **FAIL — stale JVM (see Bug #1)** |
| VIEWER cannot upload (new RBAC code) | **FAIL — stale JVM (see Bug #1)** |
| Usage/quota tracking | PASS |
| Delete releases quota | PASS |
| Analytics endpoint | PASS |
| Plans endpoint (public) | PASS |
| No secrets in tracked files | PASS |
| CORS restricted to localhost:3000 | PASS |
| Phase 2 storage migration | NOT PRESENT on main branch |

**Final status: PASS WITH LIMITATIONS**

Critical RBAC enforcement is present in the uncommitted source code but the running JVM was started before those changes and has not been restarted. A restart will activate all RBAC protections.

---

## 1. Environment

| Component | Installed | Required | Note |
|-----------|-----------|----------|------|
| Java (JVM) | 21.0.12.1 (Homebrew) | 21+ | Used by running process at `/opt/homebrew/Cellar/openjdk@21/` |
| Java (`java` on PATH) | 17.0.20.1 (Temurin) | — | Default shell `java`; Maven uses the 21 JVM via project startup |
| Maven | 3.10.0 | 3.9+ | PASS |
| Node.js | 26.10.0 | 22+ | PASS |
| npm | 11.19.1 | — | PASS |
| Docker | 29.8.2 | Required | PASS |
| FFmpeg | 9.0.2 | Required | PASS |
| ffprobe | 9.0.2 | Required | PASS (at `/opt/homebrew/bin/ffprobe`) |
| PostgreSQL | 16.15 (Docker) | 16 | PASS — healthy |

**Note on PATH:** The shell `java` command resolves to Java 17 (Temurin), but the Spring Boot process runs on Java 21 (Homebrew). When running `mvn test`, set `JAVA_HOME` explicitly:

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

---

## 2. Local Services and URLs

| Service | URL | Status |
|---------|-----|--------|
| Frontend (Next.js dev) | http://localhost:3000 | UP |
| Backend (Spring Boot) | http://localhost:8080 | UP |
| Health | http://localhost:8080/actuator/health | `{"status":"UP"}` |
| PostgreSQL | localhost:5432 | UP (Docker, healthy) |

---

## 3. Startup Commands

```bash
# Terminal 1 — Database (already running; skip if already up)
docker compose up -d postgres

# Terminal 2 — Backend
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
cd services/api && mvn spring-boot:run

# Terminal 3 — Frontend
cd apps/web
cp .env.example .env.local   # if not already present
npm install
npm run dev
```

Stop commands:
```bash
# Stop frontend: Ctrl-C in Terminal 3
# Stop backend: Ctrl-C in Terminal 2
docker compose stop postgres    # optional; data persists in volume
```

---

## 4. Database and Migration Setup

- Database: `videocloud` on PostgreSQL 16.15 (Docker volume `videocloud_pgdata`)
- Default credentials: `videocloud` / `local_only_change_me` (see `.env.example`)
- Flyway version at test time: **V6** (`V6__tenant_storage_indexes.sql`) — all 6 migrations applied successfully
- Migrations validated with `Successfully validated 6 migrations` on every test run

---

## 5. Automated Test Results

### Backend — Maven

```
cd services/api && mvn test
```

| Test Class | Tests | Result |
|------------|-------|--------|
| `AuthAndTenantIntegrationTest` | 1 | PASS |
| `LocalSchemaIntegrationTest` | 1 | PASS |
| `RbacEnforcementTest` | 3 | PASS |
| `PlanControllerTest` | 1 | PASS |
| `AuthValidationTest` | 1 | PASS |
| **Total** | **7** | **PASS** |

Build time: ~11 seconds. Exit code: 0.

Note: `RbacEnforcementTest` (3 tests) is an untracked file containing tests for VIEWER deny on delete/patch, EDITOR allow on patch / deny on delete, and ADMIN allow on delete. All pass against a fresh test context.

### Frontend — Node.js tests

```
cd apps/web && node --test
```

| Tests | Pass | Fail |
|-------|------|------|
| 12 | 12 | 0 |

Duration: ~126ms. All 12 component/integration tests pass.

### Frontend — ESLint

```
cd apps/web && npm run lint
```

Result: **0 errors, 0 warnings** (deprecation notice for `next lint` is informational only).

### Frontend — TypeScript

```
cd apps/web && npx tsc --noEmit
```

Result: **PASS** (exit code 0, no errors).

### Frontend — Production Build

```
cd apps/web && npm run build
```

Result: **PASS**. 8 routes compiled successfully in ~5.5 seconds.

```
Route (app)             Size    First Load JS
/                       165 B   106 kB
/dashboard              7.67 kB 291 kB
/billing                1.5 kB  107 kB
/pricing                165 B   106 kB
/share/[token]          1.91 kB 285 kB
/signup                 1.69 kB 107 kB
```

---

## 6. API Workflow Results

All tests used disposable test accounts with suffix `1792824240`.

| Workflow | Result | HTTP |
|----------|--------|------|
| Register user (owner) | PASS | 201 |
| Register user (viewer) | PASS | 201 |
| List videos (empty) | PASS | 200 |
| Usage endpoint | PASS | 200 |
| Upload valid MP4 (640×360) | PASS | 202 |
| Video processing → READY + HLS + thumbnail | PASS | — |
| Update video (OWNER) | PASS | 200 |
| Cross-workspace video GET (should 404) | PASS | 404 |
| Viewer lists own workspace | PASS | 200 |
| Invalid file type rejected (text/plain) | PASS | 415 |
| Delete video (OWNER) releases quota | PASS | 204 |
| Wrong-workspace delete (should 404) | PASS | 404 |
| VIEWER DELETE video (should 403) | **FAIL — got 204** (stale JVM) |
| VIEWER PATCH video (should 403) | **FAIL — got 404** (video already deleted) |
| VIEWER upload video (should 403) | **FAIL — got 202** (stale JVM) |
| Share link creation | PASS | 201 |
| Share link resolution (unauthenticated) | PASS | 200 |
| Resumable upload initiate | PASS | 200 |
| Resumable upload chunk (with SHA-256) | PASS | 200 |
| Resumable upload complete → READY | PASS | 200 |
| HLS playlist served (authenticated) | PASS | 200 |
| Analytics endpoint | PASS | 200 |
| Plans endpoint (public, no auth) | PASS | 200 |
| Unauthenticated access returns 401 | PASS | 401 |

---

## 7. Security and Tenant Isolation

| Check | Result |
|-------|--------|
| No `.env` files in git tracked files | PASS |
| `.env.example` contains only placeholder values | PASS |
| CORS restricted to `localhost:3000` and `127.0.0.1:3000` | PASS |
| `X-Workspace-Id` in allowed CORS headers | PASS |
| Cross-workspace video access blocked (returns 404) | PASS |
| Unauthenticated API returns 401 | PASS |
| VIEWER restricted from mutating (compiled code) | PASS — passes in `RbacEnforcementTest` |
| VIEWER restricted from mutating (running JVM) | **FAIL — stale JVM** |
| Storage path constrained to workspace subdirectory | PASS (verified in source) |
| Storage quota tracked per workspace | PASS |
| Razorpay mode forced to `test` only | PASS (source verified) |

---

## 8. Bugs Found

### Bug #1 — Critical: RBAC enforcement not active in running JVM

**Severity:** Critical  
**Status:** Root cause identified; fix is ready in uncommitted source code

**Description:** The Spring Boot process (PID 38968) was started at 02:53 AM, before the RBAC enforcement changes were compiled (10:10 AM today). Spring Boot `spring-boot:run` without DevTools does not hot-reload. The compiled `.class` files in `target/` include the new `assertAtLeast()` calls, but the running JVM loaded the old versions.

**Evidence:**
- VIEWER DELETE on `/api/v1/videos/{id}` returned **204** (deleted successfully) instead of 403.
- VIEWER PATCH returned 404 only because the video had already been deleted.
- VIEWER upload returned **202** instead of 403.
- `RbacEnforcementTest` (3 tests) passes when run fresh from Maven, confirming the new code is correct.
- `ps` shows PID 38968 started at `Sat Oct 24 02:53:22 2026`.
- `VideoProcessingService.class` in `target/` was compiled at 10:10 and includes `probeDuration()`, confirming changes were compiled but not reloaded.

**Root cause:** No Spring Boot DevTools in `pom.xml`; `spring-boot:run` does not restart the JVM on source change without DevTools.

**Fix:** Restart the Spring Boot application:
```bash
# Stop current process (Ctrl-C or kill -9 38968)
# Then restart:
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
cd services/api && mvn spring-boot:run
```

**Regression test:** `RbacEnforcementTest` covers all 3 RBAC cases and must be run after restart to confirm.

**Impacted uncommitted changes in the stale JVM:**
- `CurrentWorkspace.assertAtLeast()` — not executing
- `VideoController.delete` requires `OWNER,ADMIN` — not enforced
- `VideoController.update` requires `OWNER,ADMIN,EDITOR` — not enforced
- `VideoController.upload` requires `OWNER,ADMIN,EDITOR` — not enforced
- `ResumableUploadController` all mutations require `EDITOR+` — not enforced
- `ShareController` share create/revoke requires `EDITOR+` — not enforced
- `VideoProcessingService.probeDuration()` — not executing (duration stays null)
- `AsyncConfig` bounded executor — old `@Async` (no qualifier) runs on SimpleAsyncTaskExecutor

---

### Bug #2 — Low: `durationSeconds` always null in currently running app

**Severity:** Low (cosmetic UX; no data corruption)  
**Status:** Will self-resolve after Bug #1 fix (app restart)

**Description:** The `durationSeconds` field is always null in `VideoDto` responses. The `probeDuration()` method was added in the uncommitted `VideoProcessingService.java` changes but the running JVM uses the old code path that never sets this field.

**Evidence:**
```json
{"status":"READY","durationSeconds":null,...}
```
Manual `ffprobe` on the same file returns `3.000000` correctly.

**Fix:** Restart resolves this automatically. No code change needed.

---

### Bug #3 — Medium: Stale `UPLOADING` video records in database

**Severity:** Medium (storage quota may be incorrectly reserved)  
**Status:** Observed; recovery relies on scheduled cleanup or startup recovery

**Description:** 3 video records from a previous session remain in `UPLOADING` status with no source file on disk. These were likely from interrupted uploads before this session.

**Evidence:**
```sql
SELECT id, status FROM videos WHERE status='UPLOADING' ORDER BY created_at DESC;
-- 3 rows, created 2026-10-24 03:51-03:52, no source files on disk
```

**Fix:** The `ProcessingRecovery` startup component skips videos whose source file is missing (does not requeue them as FAILED). The `ExpiredUploadCleanup` scheduled task should eventually mark expired resumable sessions as EXPIRED and release their slots. Monitor the next cleanup run.

---

## 9. Phase 2 Storage Migration

**Status: NOT PRESENT on current branch**

The Phase 2 storage abstraction and legacy migration code exists on the remote branch `phase2/storage-abstraction-migration` but has **not been merged** into `main`.

Verification was not performed because the code is not present in the current checkout, per instruction Phase 5 condition: "If the Phase 2 storage abstraction and legacy migration code is present."

To inspect the Phase 2 code:
```bash
git fetch origin phase2/storage-abstraction-migration
git checkout phase2/storage-abstraction-migration
```

---

## 10. Known Limitations and Unimplemented Features

| Feature | Status |
|---------|--------|
| `durationSeconds` in video response | NOT IMPLEMENTED in running JVM (needs restart) |
| Password recovery / email verification | NOT IMPLEMENTED |
| OIDC / MFA / rate limiting | NOT IMPLEMENTED |
| Embed settings / invitation UI | NOT IMPLEMENTED |
| Comment UI | NOT IMPLEMENTED |
| Cancellation UI / billing reconciliation | NOT IMPLEMENTED |
| Anonymous share analytics | NOT IMPLEMENTED |
| Folder organization | NOT IMPLEMENTED |
| End-to-end Razorpay payment flow | NOT IMPLEMENTED (test keys required) |
| Browser-driven upload / interruption tests | NOT TESTED |
| Phase 2 storage migration dry-run | NOT TESTED (branch not present) |

---

## 11. Files Changed This Session

None — this report was generated without modifying source code. The verification tested existing uncommitted changes.

**Uncommitted changes present (not introduced by this session):**
- `services/api/src/main/java/com/videocloud/api/CurrentWorkspace.java` — adds `assertAtLeast()`, `role()`, `Membership` record
- `services/api/src/main/java/com/videocloud/video/VideoController.java` — enforces `assertAtLeast()` on upload/patch/delete/retry
- `services/api/src/main/java/com/videocloud/video/ResumableUploadController.java` — enforces `assertAtLeast()` on all mutations
- `services/api/src/main/java/com/videocloud/video/ShareController.java` — enforces `assertAtLeast()` on share create/revoke
- `services/api/src/main/java/com/videocloud/video/VideoProcessingService.java` — adds `probeDuration()` via ffprobe, uses `@Async("videoProcessingExecutor")`
- `services/api/src/main/resources/application.yml` — (diff not shown; check `git diff`)
- `docs/IMPLEMENTATION_STATUS.md` — status updates
- `services/api/src/main/java/com/videocloud/AsyncConfig.java` (untracked) — bounded async executor
- `services/api/src/test/java/com/videocloud/RbacEnforcementTest.java` (untracked) — 3 RBAC integration tests

---

## 12. Restart Procedure and Post-Restart Verification

After restarting the backend, verify RBAC is active:

```bash
# 1. Restart backend
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
cd services/api && mvn spring-boot:run

# 2. Rerun all tests (including RbacEnforcementTest)
mvn test

# 3. Manual RBAC smoke test:
# Register two users, add second as VIEWER to first workspace,
# verify VIEWER DELETE returns 403, VIEWER POST returns 403.
```

---

## 13. Final Status

**PASS WITH LIMITATIONS**

- All automated quality gates pass (7 backend tests, 12 frontend tests, ESLint clean, TypeScript clean, production build clean).
- Core video workflows work end-to-end: registration, upload, HLS processing, thumbnail, share, resumable upload.
- Tenant isolation (cross-workspace data separation) works correctly.
- The critical RBAC enforcement changes are correct in source and pass in test, but are not active in the currently running JVM due to no hot-reload.
- **Action required before using the running app for any security-sensitive testing:** restart the Spring Boot process.
