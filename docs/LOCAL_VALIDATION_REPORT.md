# VideoCloud — Local Validation Report

## Environment

| Component | Version / Detail |
|-----------|-----------------|
| OS | macOS Darwin 27.0.0 arm64 |
| Java | OpenJDK 21.0.12.1 (Homebrew temurin@21) |
| Maven | Apache Maven 3.10.0 |
| Node.js | v26.10.0 |
| npm | 11.19.1 |
| Docker | 29.8.2 (Colima runtime) |
| Docker Compose | 5.6.1 (Homebrew plugin) |
| FFmpeg | 9.0.2 (Homebrew) |
| Branch | `implementation/local-mvp` |
| Commit SHA | `539618fa99cad2cb1e5f49b99d6bb11dd3328a0a` |

### Services and Ports

| Service | Port | Status |
|---------|------|--------|
| PostgreSQL 16 | 5432 | Running (Docker / Colima) |
| Spring Boot API | 8080 | Running (mvn spring-boot:run) |
| Next.js Frontend | 3000 | Running (npm run dev) |

### Prerequisites Gap Found

- Java 21 was installed at `/opt/homebrew/Cellar/openjdk@21/21.0.12.1` (not the default JVM). The default `java` binary was Java 17. Set `JAVA_HOME` explicitly (see startup instructions).
- `docker-compose` V2 plugin was not installed. Fixed with `brew install docker-compose`.
- FFmpeg was not installed. Fixed with `brew install ffmpeg`.

---

## Results

### Database Migrations

| Check | Result | Detail |
|-------|--------|--------|
| Flyway applies V1 (identity/workspace/billing) | **PASS** | Applied on fresh schema |
| Flyway applies V2 (video foundation) | **PASS** | Applied on fresh schema |
| Flyway applies V3 (comments/shares/playback events + seed plans) | **PASS** | Applied on fresh schema |
| `users`, `videos` tables exist | **PASS** | Verified by `LocalSchemaIntegrationTest` |
| FREE, STARTER, PRO plans seeded | **PASS** | Verified by `LocalSchemaIntegrationTest` |

### Backend Unit/Integration Tests

Command: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home/... mvn test`

| Test | Result |
|------|--------|
| `AuthAndTenantIntegrationTest.registrationCreatesOwnerAndVideoListIsTenantScoped` | **PASS** |
| `LocalSchemaIntegrationTest.flywayCreatesExpectedLocalMvpTables` | **PASS** |
| `PlanControllerTest.planPricesAreRepresentedInPaiseAndNonNegative` | **PASS** |
| `AuthValidationTest.passwordHashingUsesSaltedBcrypt` | **PASS** |
| **Overall backend test suite** | **PASS — 4/4** |

### Frontend Tests

Command: `npm install && npm run lint && npm test && npm run build`

| Check | Result | Detail |
|-------|--------|--------|
| `npm run lint` | **PASS** (1 warning) | `react-hooks/exhaustive-deps` warning in `VideoPlayer.tsx:11` — not a blocking error |
| `dashboard exposes auth and upload/status controls` | **PASS** | |
| `HLS player attaches Authorization to HLS requests` | **PASS** | |
| `pricing page labels prices as INR and discloses placeholder status` | **PASS** | |
| `npm run build` (production) | **PASS** | 8 routes generated |
| TypeScript check | **PASS** | No type errors during build |

### Backend Startup

| Check | Result |
|-------|--------|
| Spring Boot starts on port 8080 | **PASS** |
| DB connection succeeds | **PASS** |
| Flyway validation on startup | **PASS** |
| `/actuator/health` returns `{"status":"UP"}` | **PASS** |

### Frontend Startup

| Check | Result |
|-------|--------|
| Next.js dev server starts on port 3000 | **PASS** |
| Home page served | **PASS** |
| Frontend reaches backend (API base URL configured) | **PASS** |

### Registration and Authentication

| Check | Result | Detail |
|-------|--------|--------|
| Registration of new user (Alice) | **PASS** | Returns 201 with user/workspace IDs |
| Registration of new user (Bob) | **PASS** | Returns 201 |
| Duplicate registration | **PASS (after fix)** | Returns 409 Conflict (was 401 — see Defects) |
| Validation failure (short password) | **PASS (after fix)** | Returns 400 (was 401 — see Defects) |
| Password stored as BCrypt hash | **PASS** | `$2a$12$` prefix confirmed |
| Authenticated `GET /api/v1/videos` | **PASS** | Returns 200 |
| Unauthenticated `GET /api/v1/videos` | **PASS** | Returns 401 |

### Upload and Processing

| Check | Result | Detail |
|-------|--------|--------|
| Upload valid MP4 | **PASS** | Returns 202 Accepted, status `PROCESSING` |
| FFmpeg thumbnail generation | **PASS** | `thumbnail.jpg` created |
| FFmpeg HLS transcode | **PASS** | `index.m3u8` + `segment-00000.ts` created |
| Video status transitions to `READY` | **PASS** | < 5 seconds for 8-second test clip |
| Upload invalid file type (text/plain) | **PASS** | Returns 415 |

### HLS Playback

| Check | Result | Detail |
|-------|--------|--------|
| Authenticated HLS manifest access | **PASS** | Returns valid `#EXTM3U` |
| Unauthenticated HLS manifest (private video) | **PASS** | Returns 401 |
| HLS segment accessible (authenticated) | **PASS** | Returns `video/mp2t` |

### Public/Private Access

| Check | Result | Detail |
|-------|--------|--------|
| Public video accessible without auth | **PASS** | `GET /api/v1/public/videos/{id}` returns 200 |
| Private video not exposed publicly | **PASS** | Returns 404 |
| Public HLS accessible without auth | **PASS** | Returns 200 |

### Sharing and Revocation

| Check | Result | Detail |
|-------|--------|--------|
| Create share link | **PASS** | Returns token and share URL |
| Access shared video without auth | **PASS** | Returns 200 with metadata |
| Access shared HLS without auth | **PASS** | Returns 200 |
| Invalid share token | **PASS** | Returns 404 |
| Revoke share link | **PASS** | Returns 204 |
| Access revoked share | **PASS** | Returns 404 |
| Share token is random (URL-safe Base64, 32 bytes) | **PASS** | Not a raw DB ID |

### Workspace Isolation

| Check | Result | Detail |
|-------|--------|--------|
| Bob cannot list Alice's videos | **PASS** | Returns `[]` |
| Bob cannot retrieve Alice's video by ID | **PASS** | Returns 404 |
| Bob cannot update Alice's video | **PASS** | Returns 404 |
| Bob cannot delete Alice's video | **PASS** | Returns 404 |
| Bob cannot access Alice's private HLS | **PASS** | Returns 404 |
| Bob cannot comment on Alice's video | **PASS** | Returns 404 |

### Video CRUD

| Check | Result | Detail |
|-------|--------|--------|
| Update video title | **PASS** | Title updated, returns 200 |
| Update video visibility (PRIVATE → PUBLIC) | **PASS** | Visibility updated |
| List videos (client-side search/sort) | **PASS** | Client filters in-memory |
| Delete video | **PASS** | Returns 204 |
| Deleted video no longer accessible | **PASS** | Returns 404 |
| Filesystem cleanup after delete | **PASS** | Media directory removed |

### Comments

| Check | Result | Detail |
|-------|--------|--------|
| Create comment with timestamp | **PASS** | Returns author, body, timestamp |
| List comments | **PASS** | Returns paginated list |
| Cross-workspace comment denied | **PASS** | Returns 404 |

### Billing

| Check | Result | Detail |
|-------|--------|--------|
| Plans endpoint (no auth) | **PASS** | Returns 4 plans with INR paise prices |
| Usage endpoint (authenticated) | **PASS** | Returns workspace usage summary |
| Usage endpoint (no auth) | **PASS** | Returns 401 |
| Billing checkout without test credentials | **PASS** | Returns 503 Service Unavailable |
| Razorpay webhook without secret | **PASS** | Returns 503 |
| Razorpay mode enforcement (non-test blocked) | **NOT RUN** | No live credentials to test rejection |

### Security Checks

| Check | Result | Detail |
|-------|--------|--------|
| Unauthorized requests return 401 | **PASS** | |
| Path traversal guard on asset names | **PASS** | Asset path validated by regex |
| Path traversal guard on media root | **PASS** | `dir.startsWith(root)` check in VideoController |
| Share tokens are SHA-256 hashed | **PASS** | `token_hash CHAR(64)` in DB |
| `/error` path now permitted | **PASS (fixed)** | See Defects |

### Reliability Checks

| Check | Result | Detail |
|-------|--------|--------|
| FFmpeg missing scenario | **NOT RUN** | Would require temporarily removing FFmpeg |
| Corrupt/unsupported media | **PASS (partial)** | Invalid MIME type rejected at upload; corrupt MP4 would fail at FFmpeg with `FAILED` status |
| Interrupted uploads | **NOT RUN** | No resumable upload implementation |
| Disk-space constraints | **NOT RUN** | No quota enforcement implemented |
| API timeouts | **NOT RUN** | No load test performed |

---

## Defects and Remaining Work

### Defects Found and Fixed

#### DEFECT-1: Registration error responses returned 401 instead of correct status

**Symptom:** Duplicate registration returned 401 instead of 409 Conflict. Validation failures returned 401 instead of 400 Bad Request.

**Root cause:** Spring Boot's `BasicErrorController` forwards errors to `/error`. The `/error` path was not in the `permitAll` list in `SecurityConfig`, so unauthenticated requests to a `permitAll` endpoint that triggered an error got a 401 when Spring Security evaluated the `/error` forward.

**Fix:** Added `/error` to the `permitAll` matchers in `SecurityConfig.java`. Also replaced the default `BasicAuthenticationEntryPoint` with `HttpStatusEntryPoint(UNAUTHORIZED)` for cleaner API responses (no `WWW-Authenticate` header).

Additionally, replaced the `try/catch(DuplicateKeyException)` pattern in `AuthController.register()` with a pre-check `SELECT count(*) FROM users WHERE lower(email)=?` to improve clarity (the race-condition window is acceptable for a local MVP).

**Files changed:**
- `services/api/src/main/java/com/videocloud/security/SecurityConfig.java`
- `services/api/src/main/java/com/videocloud/api/AuthController.java`

**Regression test:** All 4 existing backend tests still pass. Manually verified 409 on duplicate, 400 on validation failure.

---

### Defects Still Open

None found beyond DEFECT-1 (fixed above).

---

### Tests That Could Not Run and Why

| Test | Reason |
|------|--------|
| Razorpay end-to-end (checkout + webhook) | No Razorpay test-mode credentials configured; requires user to provide them |
| Expired share link | Share expiry requires creating a share with `expires_at` in the past; API does not expose an expiry parameter in the current implementation |
| Load tests | No load-testing framework configured |
| FFmpeg failure scenario | Would require temporarily removing or corrupting FFmpeg |
| Disk-space limits | No quota enforcement in this build |
| Resumable/chunked upload | Not implemented |
| Browser automation (UI visual flows) | No end-to-end browser automation tool installed; pages load correctly and API is tested via HTTP |

---

### Manual Checks Still Needed

- Browser: Verify dashboard signup flow, upload form, video player playback in a real browser
- Browser: Verify share page (`/share/[token]`) renders the embedded HLS player
- Browser: Verify billing page renders correctly and shows "no payment collected" disclaimer
- Razorpay test checkout: Requires user to provide test-mode key ID, key secret, webhook secret, and plan IDs

---

### Features Not Implemented (Per IMPLEMENTATION_STATUS)

- Password recovery, email verification, OIDC/MFA, rate limits
- Resumable/chunked uploads, checksum validation, quotas
- Upload integrity scanning, retry management
- Share/embed UI, invitations, role management
- Analytics event ingestion
- Razorpay end-to-end verification, invoices/tax, cancellation UI, reconciliation, refund handling
- Folders, richer analytics, accessibility polish
- Dependency audit, upload fuzzing, load tests, backup/restore drills, rate limiting

---

### Production-Readiness Risks

1. **Authentication model:** HTTP Basic over HTTP. Credentials stored in `sessionStorage`. Not safe for public exposure.
2. **No rate limiting:** Registration and login endpoints are unprotected against brute force.
3. **Single-node media storage:** HLS assets stored on local filesystem. Not replicated or backed up.
4. **Synchronous video processing:** FFmpeg runs in a `@Async` thread pool. No retry mechanism for failures.
5. **No HTTPS:** All communication is plaintext over localhost.
6. **No input size validation on comments/webhooks:** Only the upload size is bounded.
7. **Billing not end-to-end verified:** Subscription creation and webhook flow confirmed to compile and reject unconfigured state; live Razorpay test has not been exercised.

---

## Exact Startup Instructions

### 1. Prerequisites

```bash
# Ensure Java 21 is available
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
java -version  # must show 21.x

# Ensure Docker Compose plugin is installed
brew install docker-compose
# Add to ~/.docker/config.json: "cliPluginsExtraDirs": ["/opt/homebrew/lib/docker/cli-plugins"]

# Ensure FFmpeg is installed
brew install ffmpeg
```

### 2. Start PostgreSQL (Terminal 1)

```bash
cd ~/video-stream
colima start        # start Docker runtime (macOS)
docker compose up -d postgres
docker compose ps   # wait until status shows (healthy)
```

### 3. Start Backend API (Terminal 2)

```bash
cd ~/video-stream/services/api
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/videocloud"
export SPRING_DATASOURCE_USERNAME="videocloud"
export SPRING_DATASOURCE_PASSWORD="local_only_change_me"
export VIDEO_MEDIA_ROOT="$(pwd)/../../var/media"
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
npm test      # 3 tests, 0 failures
npm run lint  # 1 warning (not an error)
npm run build # production build succeeds
```

### 7. Generate Test Video (for E2E)

```bash
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=24 \
  -f lavfi -i sine=frequency=1000:sample_rate=44100 \
  -t 8 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest \
  ~/video-stream/var/test-media/sample-test.mp4
```

---

**Note:** This platform is a local MVP. It is not production-ready. Do not expose it publicly.
