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
- [ ] Password recovery, email verification, OIDC/MFA, rate limits and production session model

## Phase 3 — upload
- [x] Multipart upload with configured byte limit and MIME/extension allowlist
- [x] Local media storage and video metadata
- [ ] Resumable/chunked uploads, checksum validation, quotas and recovery

## Phase 4 — playback
- [x] Async FFmpeg thumbnail and HLS generation
- [x] Authenticated owner-only HLS asset endpoint
- [x] hls.js playback component for browsers supporting Media Source Extensions
- [x] Explicit public video and HLS asset endpoints
- [ ] Upload integrity scanning, retry management and cross-browser playback tests

## Phase 5 — creator dashboard
- [x] Upload/list/status/visibility/delete UI and usage summary
- [x] Responsive creator workspace layout with sidebar, quick actions, library search/sort, and analytics placeholder that does not fabricate metrics
- [x] Compact sidebar icon/label alignment and visible sign-out action
- [x] Dashboard polls while video processing is in progress to refresh status and storage usage
- [x] Storage display connected to the existing workspace usage API (original-file bytes only)
- [ ] Video view/watch-time analytics, real plan quota enforcement, recording/import/live event features
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
