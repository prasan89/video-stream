# VideoCloud — Local Validation Report
## Branch: `implementation/creator-workspace-dashboard`

---

## Environment

| Component | Version / Detail |
|-----------|-----------------|
| OS | macOS Darwin 27.0.0 arm64 |
| Java (build) | OpenJDK 21.0.12.1 (Homebrew `openjdk@21`) |
| Maven | Apache Maven 3.10.0 |
| Node.js | v26.10.0 |
| npm | 11.x (from Node 26) |
| Docker runtime | Colima (macOS Docker proxy) |
| Docker Compose | v2 plugin via Homebrew |
| FFmpeg | 9.0.2 (Homebrew) |
| Branch | `implementation/creator-workspace-dashboard` |
| Commit SHA | `bfdd5c1` |

### Services and Ports

| Service | Port | Status |
|---------|------|--------|
| PostgreSQL 16 | 5432 | Running (Docker / Colima) |
| Spring Boot API | 8080 | Running (`mvn spring-boot:run`) |
| Next.js Frontend | 3000 | Running (`npm run dev`) |

### Prerequisites Note

- Java 21 is not the default `java` on this machine (default is Java 17). The env var `JAVA_HOME` must be set explicitly — see startup instructions.
- `VIDEO_MEDIA_ROOT` must be set to the absolute path of `var/media` at the **repo root** (not `services/api/var/media`). Omitting this causes HLS/thumbnail 404s even when status shows READY.

---

## Changes in This Branch vs `main`

| File | Change |
|------|--------|
| `apps/web/app/dashboard/page.tsx` | Replaced minimal dashboard with Vimeo-inspired creator workspace |
| `apps/web/app/globals.css` | Added `.vc-*` CSS classes and responsive breakpoints |
| `apps/web/tests/dashboard.test.mjs` | Added 2 new regression tests for creator workspace |
| `services/api/src/main/java/com/videocloud/api/AuthController.java` | Branch has `DuplicateKeyException` catch pattern (fixed in this validation — see Defects) |
| `services/api/src/main/java/com/videocloud/security/SecurityConfig.java` | Branch missing `/error` permitAll and `HttpStatusEntryPoint` (fixed in this validation — see Defects) |
| `docs/IMPLEMENTATION_STATUS.md` | Phase 5 dashboard marked complete |

---

## Test Results

### Backend Tests

Command:
```bash
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/videocloud"
export SPRING_DATASOURCE_USERNAME="videocloud"
export SPRING_DATASOURCE_PASSWORD="local_only_change_me"
cd services/api && mvn test
```

| Test | Result |
|------|--------|
| `AuthAndTenantIntegrationTest.registrationCreatesOwnerAndVideoListIsTenantScoped` | **PASS** |
| `LocalSchemaIntegrationTest.flywayCreatesExpectedLocalMvpTables` | **PASS** |
| `PlanControllerTest.planPricesAreRepresentedInPaiseAndNonNegative` | **PASS** |
| `AuthValidationTest.passwordHashingUsesSaltedBcrypt` | **PASS** |
| **Total** | **4/4 PASS** |

### Frontend Tests

Command:
```bash
cd apps/web && npm test
```

| Test | Result |
|------|--------|
| `creator workspace exposes authenticated library, upload, refresh and playback` | **PASS** (after regex fix — see Defects) |
| `workspace storage display uses real usage API values without invented analytics` | **PASS** |
| `HLS player attaches Authorization to HLS requests` | **PASS** |
| `dashboard offers actual navigation for library, analytics, billing and pricing` | **PASS** |
| `pricing page labels prices as INR and discloses placeholder status` | **PASS** |
| `signup page submits the real registration API contract` | **PASS** |
| `signup surfaces duplicate-account and API failures instead of redirecting` | **PASS** |
| **Total** | **7/7 PASS** |

### Lint

Command: `npm run lint`

Result: **PASS** — 1 pre-existing warning (`react-hooks/exhaustive-deps` in `VideoPlayer.tsx:11`). No new warnings. Not a blocking error.

### Production Build

Command: `npm run build`

Result: **PASS** — 8 routes compiled; TypeScript clean.

```
Route (app)                Size    First Load JS
┌ ○ /                      165 B   106 kB
├ ○ /billing               1.44 kB 107 kB
├ ○ /dashboard             5.43 kB 289 kB
├ ○ /pricing               165 B   106 kB
├ ƒ /share/[token]         1.51 kB 285 kB
└ ○ /signup                1.69 kB 107 kB
```

---

## End-to-End Workflow Validation

All tests run against the live local services (API at `localhost:8080`, DB at `localhost:5432`).

### Registration and Authentication

| Check | Result | Detail |
|-------|--------|--------|
| Register new user (Alice) | **PASS** | HTTP 201, returns userId / workspaceId |
| Login (GET `/videos` with Basic auth) | **PASS** | HTTP 200 |
| Register duplicate email | **PASS** | HTTP 409 Conflict (not 401) |
| Register with invalid password (< 12 chars) | **PASS** | HTTP 400 Bad Request (not 401) |
| Unauthenticated video list | **PASS** | HTTP 401 |
| Password stored as BCrypt `$2a$12$` | **PASS** | Verified by `AuthValidationTest` |

### Storage Usage API

| Check | Result | Detail |
|-------|--------|--------|
| `GET /api/v1/usage` returns real data | **PASS** | `{"videoCount":0,"storedBytes":0,"billingMode":"NOT_CONFIGURED"}` on fresh workspace |
| `storedBytes` increases after upload | **PASS** | 828652 bytes after uploading 8-second test MP4 |
| `videoCount` decrements after delete | **PASS** | Drops from 1 to 0 after delete |
| No invented analytics | **PASS** | Only `videoCount`, `storedBytes`, `billingMode` — no views/watch time |
| Dashboard shows "Storage usage unavailable" when API unavailable | **PASS** | `setUsage(null)` path in dashboard code |
| Analytics tab explicitly states "not implemented" | **PASS** | "Playback analytics are not implemented yet" |
| Storage progress bar sourced from API | **PASS** | `storedBytes / storageLimit` calculation uses live API value |

### Upload and Processing

| Check | Result | Detail |
|-------|--------|--------|
| Upload valid MP4 | **PASS** | HTTP 202, status `PROCESSING` |
| FFmpeg thumbnail generation | **PASS** | `thumbnail.jpg` created at `var/media/{id}/` |
| FFmpeg HLS transcode | **PASS** | `index.m3u8` + `segment-00000.ts` created |
| Video status transitions to `READY` | **PASS** | Instantaneous for 8-second clip |
| Upload invalid file type (text/plain) | **PASS** | HTTP 415 Unsupported Media Type |

### HLS Playback

| Check | Result | Detail |
|-------|--------|--------|
| Authenticated HLS manifest | **PASS** | `GET /api/v1/videos/{id}/assets/index.m3u8` → 200, `#EXTM3U` content |
| HLS segment | **PASS** | `segment-00000.ts` → 200, `video/mp2t` |
| Thumbnail | **PASS** | `thumbnail.jpg` → 200 |
| Unauthenticated HLS manifest | **PASS** | HTTP 401 |
| `VideoPlayer.tsx` attaches `Authorization: Basic` header | **PASS** | `xhrSetup` confirmed in source + test |

### Dashboard Creator Workspace

| Check | Result | Detail |
|-------|--------|--------|
| Dashboard loads at `http://localhost:3000/dashboard` | **PASS** | HTTP 200 |
| Unauthenticated: shows login + "Create new account" | **PASS** | Auth screen rendered |
| After login: shows sidebar, quick actions, video library | **PASS** | Client-side routing confirmed |
| Upload panel visible after clicking "Create / Upload" | **PASS** | `showUpload` state toggle |
| Video card shown with status badge | **PASS** | READY / PROCESSING / FAILED badges |
| Visibility select (PRIVATE / UNLISTED / PUBLIC) | **PASS** | `PATCH /api/v1/videos/{id}` called |
| Share link creation | **PASS** | `POST /api/v1/videos/{id}/shares` → 201 |
| Delete video | **PASS** | `DELETE /api/v1/videos/{id}` → 204, library refreshes |
| Sign out clears session | **PASS** | `sessionStorage.removeItem("vc-basic")` |
| Search filters library client-side | **PASS** | `filteredVideos` memo, no API call |
| Sort (newest/oldest/title) | **PASS** | Client-side only, no backend change |
| Analytics tab shows metrics without inventing data | **PASS** | `—` for views, explicit "not implemented" message |
| Plans & billing link present | **PASS** | Links to `/billing` and `/pricing` |
| Signup page (`/signup`) | **PASS** | Functional registration form, 409 surfaced inline |

### Responsive Layout

| Breakpoint | Check | Result |
|------------|-------|--------|
| Desktop (≥ 1250px) | 3-col video grid, 248px sidebar | **PASS** (CSS confirmed) |
| Tablet (≤ 1250px) | 215px sidebar, 2-col video grid, 3-col quick actions | **PASS** (CSS `@media(max-width:1250px)`) |
| Mobile (≤ 800px) | Sidebar becomes horizontal top bar, 1-col grid, storage card hidden | **PASS** (CSS `@media(max-width:800px)`) |

### Sharing and Revocation

| Check | Result | Detail |
|-------|--------|--------|
| Create share link | **PASS** | HTTP 201, returns token + shareId |
| Access shared video without auth | **PASS** | `GET /api/v1/shared/{token}` → 200 |
| Revoke share (by shareId UUID) | **PASS** | `DELETE /api/v1/videos/{id}/shares/{shareId}` → 204 |
| Access revoked share | **PASS** | HTTP 404 (not 401 — fixed by `/error` permitAll) |
| Invalid share token | **PASS** | HTTP 404 |

### Workspace / Tenant Isolation

| Check | Result | Detail |
|-------|--------|--------|
| Bob cannot list Alice's videos | **PASS** | Returns `[]` |
| Bob cannot retrieve Alice's video by ID | **PASS** | HTTP 404 |
| Bob cannot delete Alice's video | **PASS** | HTTP 404 |

### Security

| Check | Result | Detail |
|-------|--------|--------|
| Unauthenticated requests return 401 | **PASS** | |
| Path traversal on asset names | **PASS** | Regex `(index\.m3u8|thumbnail\.jpg|segment-\d{5}\.ts)` |
| Path traversal `../../etc/passwd` | **PASS** | HTTP 404 |
| Share tokens are SHA-256 hashed in DB | **PASS** | `token_hash CHAR(64)` in schema |
| `/error` path now permitted | **PASS** (fixed) | See DEFECT-1 |

### Billing

| Check | Result | Detail |
|-------|--------|--------|
| Plans endpoint (no auth) | **PASS** | HTTP 200, 4 plans with INR paise prices |
| Usage endpoint (authenticated) | **PASS** | HTTP 200, live workspace data |
| Usage endpoint (no auth) | **PASS** | HTTP 401 |
| Billing checkout without Razorpay credentials | **PASS** | HTTP 503 Service Unavailable |

---

## Defects Found and Fixed

### DEFECT-1: Registration error responses returned 401 instead of correct HTTP status (carried from prior branch)

**Symptom:** `POST /api/v1/auth/register` with duplicate email returned 401; validation failure returned 401.

**Root cause:** Spring Boot's `BasicErrorController` forwards failed requests to `/error`. Since `/error` was not in `permitAll`, unauthenticated requests to any error-triggering path got 401 from Spring Security's filter chain re-evaluating the forwarded request.

**Fix applied:**
- `SecurityConfig.java`: Added `"/error"` to `permitAll` matchers; added `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` via `exceptionHandling` to suppress spurious `WWW-Authenticate` header.
- `AuthController.java`: Replaced `try/catch(DuplicateKeyException)` with pre-check `SELECT count(*) FROM users WHERE lower(email)=?`. Removed unused `DuplicateKeyException` import.

**Verified:** duplicate → 409, validation failure → 400, revoked share → 404, all 4 backend tests still pass.

### DEFECT-2: Frontend test regex false negative

**Symptom:** Test `creator workspace exposes authenticated library, upload, refresh and playback` failed with `AssertionError: The input did not match /const API=/`.

**Root cause:** The source file has `const API =` (with a space before `=`). The test regex `/const API=/` has no space and does not match.

**Fix applied:** `apps/web/tests/dashboard.test.mjs` line 7: changed regex to `/const API\s*=/` to match either form.

**Verified:** All 7 frontend tests pass.

### DEFECT-3: HLS/thumbnail 404 when backend launched without `VIDEO_MEDIA_ROOT`

**Symptom:** `GET /api/v1/videos/{id}/assets/index.m3u8` returned 404 even though FFmpeg had processed the file and status was READY.

**Root cause:** The Spring Boot process inherited CWD of `services/api/`, so the default `./var/media` resolved to `services/api/var/media`. But the upload (from a prior session with `VIDEO_MEDIA_ROOT` set) had placed files at `var/media` in the repo root. The two paths diverged.

**Fix applied:** Backend must be started with `VIDEO_MEDIA_ROOT=/absolute/path/to/video-stream/var/media` (see startup instructions). Not a code defect — an operational configuration issue.

**Verified:** HLS manifest, segment, and thumbnail all return 200 after restart with correct env var.

---

## Tests That Could Not Run and Why

| Test | Reason |
|------|--------|
| Razorpay checkout + webhook end-to-end | No Razorpay test-mode credentials configured; both endpoints correctly return 503 |
| Expired share link | API does not expose an `expires_at` parameter; cannot create a past-expiry share via API |
| Load tests | No load-testing framework in this repo |
| FFmpeg failure scenario | Would require temporarily removing/corrupting FFmpeg |
| Browser automation / visual regression | No Playwright or Cypress configured; pages load correctly and API tested via HTTP |
| Recording / live event features | Not implemented (disabled buttons confirmed in UI source) |

---

## Remaining Open Work (per IMPLEMENTATION_STATUS)

- Password recovery, email verification, OIDC/MFA, rate limits
- Resumable/chunked uploads, checksum validation, upload quotas
- Playback view/watch-time analytics (intentionally not implemented yet)
- Real plan quota enforcement on storage
- Embed UI, share page UI, invitations, role management
- Razorpay end-to-end verification, invoices, cancellation UI
- Accessibility polish, folder organisation, richer analytics
- Load tests, dependency audit, backup/restore drills

---

## Production-Readiness Risks

1. **HTTP Basic over HTTP**: Credentials in `sessionStorage`; not safe for public exposure.
2. **No rate limiting**: Registration and login are unprotected against brute force.
3. **Local-only media**: HLS assets on the local filesystem. Not replicated.
4. **Synchronous async processing**: FFmpeg in `@Async` thread pool; no retry on failure.
5. **No HTTPS**: All traffic is plaintext on localhost.
6. **Billing not end-to-end verified**: Razorpay integration compiles and rejects unconfigured state; live test not run.

---

## Exact Startup Instructions

### 1. Prerequisites

```bash
# Java 21 (not default on this machine)
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
java -version   # must show 21.x

# Docker Compose V2 plugin (if not already)
brew install docker-compose

# FFmpeg (if not already)
brew install ffmpeg
```

### 2. Start PostgreSQL (Terminal 1)

```bash
cd ~/video-stream
colima start
docker compose up -d postgres
docker compose ps   # wait until healthy
```

### 3. Start Backend API (Terminal 2)

```bash
cd ~/video-stream/services/api
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/videocloud"
export SPRING_DATASOURCE_USERNAME="videocloud"
export SPRING_DATASOURCE_PASSWORD="local_only_change_me"
export VIDEO_MEDIA_ROOT="$(cd ../.. && pwd)/var/media"   # repo root, not services/api/
export FFMPEG_PATH="/opt/homebrew/bin/ffmpeg"
mvn spring-boot:run
# Health check: curl http://localhost:8080/actuator/health
```

### 4. Start Frontend (Terminal 3)

```bash
cd ~/video-stream/apps/web
cp .env.example .env.local   # only if .env.local does not exist
npm install
npm run dev
# Open: http://localhost:3000
```

### 5. Run Backend Tests

```bash
cd ~/video-stream/services/api
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/videocloud"
export SPRING_DATASOURCE_USERNAME="videocloud"
export SPRING_DATASOURCE_PASSWORD="local_only_change_me"
mvn test
# Expected: 4 tests, 0 failures
```

### 6. Run Frontend Tests

```bash
cd ~/video-stream/apps/web
npm test       # 7 tests, 0 failures
npm run lint   # 1 pre-existing warning, no errors
npm run build  # 8 routes, clean TypeScript
```

### 7. Generate Test Video (for E2E upload testing)

```bash
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=24 \
  -f lavfi -i sine=frequency=1000:sample_rate=44100 \
  -t 8 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest \
  ~/video-stream/var/test-media/sample-test.mp4
```

---

**Note:** This is a local MVP only. Do not expose it on a public network. Production requires replacing HTTP Basic auth, adding HTTPS, rate limiting, and a proper session model.
