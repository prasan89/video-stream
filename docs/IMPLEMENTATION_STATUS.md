# Implementation status — local-only scope

## Phase 0–1: foundation
- [x] PostgreSQL Compose and Flyway migrations, validated in GitHub Actions against PostgreSQL 16
- [x] Next.js configuration corrected and framework upgraded
- [x] Frontend lint, Node tests and production build pass on the last completed frontend run
- [ ] Verify latest combined CI after final changes and test on a clean developer machine

## Phase 2 — authentication
- [x] Registration with BCrypt password hashing and OWNER workspace membership
- [x] Signup UI wired to the registration API with validation, duplicate-account feedback and dashboard redirect
- [x] Signup verifies newly created credentials against the authenticated video API before storing local Basic credentials
- [x] Database-backed HTTP Basic authentication for the local API
- [x] Workspace-derived authorization for video operations
- [x] Integration test for cross-workspace video isolation
- [x] RBAC role enforcement: VIEWER is read-only; EDITOR can upload/edit but not delete; ADMIN and OWNER can delete; all roles checked on every mutating endpoint via CurrentWorkspace.assertAtLeast()
- [x] RbacEnforcementTest covers VIEWER deny (delete/patch), EDITOR allow (patch) / deny (delete), and ADMIN allow (delete)
- [ ] Password recovery, email verification, OIDC/MFA, rate limits and production session model

## Phase 3 — upload
- [x] Multipart upload with configured byte limit and MIME/extension allowlist
- [x] Local media storage and video metadata
- [x] Workspace plan-based storage and video-count quota enforcement on standard and resumable uploads (FREE fallback; active/trial subscription plan when present)
- [x] New original videos and resumable chunks stored under tenant-specific `workspaces/{workspaceId}/videos` and `workspaces/{workspaceId}/uploads` directories
- [x] Usage and quota calculations are scoped to the selected workspace; reserved upload bytes are included
- [x] Workspace selector resolves `X-Workspace-Id` only after verifying authenticated membership; ambiguous multi-workspace requests require explicit selection
- [x] Resumable uploads with 8 MiB chunks, per-chunk SHA-256, optional full-file SHA-256, idempotent chunk retries, upload status and completion validation
- [x] Client retries transient chunk failures up to three attempts and displays upload progress
- [x] Usage API reports plan, storage limit, available bytes, in-progress reserved bytes and video limit
- [x] Startup recovery requeues videos left in PROCESSING when the original file is present; failed videos can be manually retried
- [x] Scheduled cleanup marks expired upload sessions EXPIRED, releases placeholder video slots, and removes local temporary chunk directories
- [x] Dashboard persists pending upload metadata and resumes missing chunks after refresh when the same file is reselected
- [x] Bounded async executor (ThreadPoolTaskExecutor, default 4 threads, queue 20) for VideoProcessingService; configurable via PROCESSING_THREADS and PROCESSING_QUEUE env vars
- [ ] End-to-end backend tests for concurrent quota reservations and browser-driven large-file interruption tests
- [ ] Migrate existing legacy media files into tenant-specific directories with a one-time verified local migration (existing legacy object keys remain readable)

## Phase 4 — playback and processing reliability (in progress)
- [x] Run FFmpeg with bounded execution time and discard subprocess output streams to avoid blocked pipes.
- [x] Terminate timed-out media processes and report non-zero exits as processing failures.
- [x] Validate non-empty thumbnail, VOD HLS manifest, and at least one non-empty segment before marking a video READY.
- [x] Remove stale/partial generated outputs before retries and clear generated-asset registrations on processing failure.
- [x] Add process-runner tests for exit codes and timeout behavior.
- [ ] Run end-to-end FFmpeg tests with real synthetic media and verify HLS playback in Chromium, Firefox, Safari/iOS, and native HLS clients.
- [ ] Add a durable processing queue and bounded worker pool; current @Async execution is not a persistent job queue.
- [ ] Add playback range-request/seek behavior and verify HLS manifest segment references and MIME/cache behavior across browsers.

## Phase 4 — playback
- [x] Async FFmpeg thumbnail and HLS generation
- [x] Authenticated owner-only HLS asset endpoint
- [x] hls.js playback component for browsers supporting Media Source Extensions
- [x] Explicit public video and HLS asset endpoints
- [x] duration_seconds populated via ffprobe after HLS transcode succeeds; VideoDto exposes it
- [ ] Upload integrity scanning, retry management and cross-browser playback tests

## Phase 5 — creator dashboard
- [x] Upload/list/status/visibility/delete UI and usage summary
- [x] Responsive creator workspace layout with sidebar, quick actions, library search/sort, and analytics placeholder that does not fabricate metrics
- [x] Compact sidebar icon/label alignment and visible sign-out action
- [x] Dashboard polls while video processing is in progress to refresh status and storage usage
- [x] Resumable upload recovery UI can restore a saved session and skip chunks already confirmed by the API
- [x] Storage display connected to the existing workspace usage API (original-file bytes only)
- [x] Authenticated playback event ingestion for PLAY, PAUSE, COMPLETE and ERROR
- [x] Workspace analytics API for 30-day playback starts, unique browser sessions, completed plays, watch-time estimates, daily trend and per-video totals
- [x] Dashboard analytics view connected to recorded events
- [ ] Anonymous shared/public audience analytics, real plan quota enforcement, recording/import/live event features
- [x] Search by title and sort by newest, oldest or title
- [ ] Folders, richer analytics and accessibility polish

## Phase 6 — sharing/collaboration
- [x] Random share tokens stored as SHA-256 hashes with expiry/revocation-aware access
- [x] Share-link creation and shared-video page
- [x] Comments CRUD API for workspace members and playback-event schema
- [ ] Embed settings/UI, invitations, role management and analytics event ingestion

## Phase 7 — INR billing
- [x] INR plan seed data and usage endpoint
- [x] Razorpay test-mode subscription creation when test keys and test plan IDs are configured
- [x] Webhook HMAC verification, duplicate event suppression and transactional processing
- [ ] End-to-end provider verification, invoices/tax, cancellation UI, reconciliation and refund handling
- [x] API rejects modes other than test; live payments are not enabled

## Phase 8 — release readiness
- [x] PostgreSQL migration smoke test and workspace isolation integration test added to CI
- [x] Local-only deployment scope documented
- [ ] Dependency audit, upload fuzzing, load tests, backup/restore drills, rate limiting and security review

This is a local MVP, not a production-ready hosted video service. The dashboard stores Basic credentials in sessionStorage. Do not expose it publicly; production must replace this with a reviewed OIDC/session design.


## Phase 3 — upload integrity and reliability (in progress)
- [x] Validate extension/MIME agreement and container signatures before queuing standard uploads for processing.
- [x] Validate the assembled resumable file after chunk/full-file checksum verification and before finalizing it.
- [x] Unit tests cover valid MP4/WebM signatures, MIME mismatch, spoofed signatures and undersized files.
- [ ] Add PostgreSQL-backed concurrent quota reservation tests and browser-driven interruption/resume tests.
- [ ] Add malware scanning or sandboxed media inspection before public sharing; the current signature check is not a malware scanner.
- [ ] Verify the full workflow locally with real MP4, MOV, WebM and MKV fixtures and FFmpeg.

## Storage abstraction and legacy-media migration milestone
- [x] Introduced a `MediaStorage` contract and safe local-disk implementation with root-confined object keys.
- [x] Verified, retry-safe copy primitive uses SHA-256 and retains source files.
- [x] Video processing registers generated assets through the storage abstraction.
- [x] Added an opt-in legacy-media migration service and startup runner; dry-run is the default and migration is disabled unless explicitly enabled.
- [x] Added unit tests for path traversal rejection and verified copy retry behavior.
- [ ] Migrate all remaining controllers and cleanup jobs to the storage abstraction.
- [ ] Run the legacy migration in dry-run mode on a representative local database and review every reported object.
- [ ] Run migration mode only after a backup and dry-run sign-off; verify playback and storage usage before manually cleaning legacy files.
\n\n## Phase 5 — creator dashboard library organization\n- [x] Add workspace-scoped folder labels to videos with an additive database migration; existing videos remain uncategorized.\n- [x] Add folder assignment and removal through the existing workspace-authorized video update endpoint.\n- [x] Add folder filtering, uncategorized view, and accessible per-video folder controls in the creator library.\n- [x] Preserve existing search, sorting, sharing, visibility, upload and analytics behavior.\n- [ ] Verify migration and API/frontend tests locally and run browser accessibility checks.\n