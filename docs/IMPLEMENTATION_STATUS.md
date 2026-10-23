# Implementation status — local-only scope

## Phase 0–1: foundation
- [x] PostgreSQL Compose and Flyway baseline
- [x] Next.js config compatibility fix and framework upgrade
- [ ] Full frontend CI (lint/test/build) and clean-clone startup verified

## Phase 2 — authentication
- [x] Registration, BCrypt password hashing and OWNER workspace membership
- [x] Database-backed HTTP Basic authentication for local API
- [x] Workspace-derived authorization for video operations
- [ ] Password recovery, email verification, OIDC/MFA, rate limits and production session model

## Phase 3 — upload
- [x] Multipart upload with configured byte limit and MIME/extension allowlist
- [x] Local media storage and metadata
- [ ] Resumable/chunked uploads, checksum validation, quotas and recovery

## Phase 4 — playback
- [x] Async FFmpeg thumbnail and HLS generation
- [x] Authenticated owner-only HLS asset endpoint
- [x] hls.js-based playback component for browsers supporting Media Source Extensions
- [ ] Public/unlisted playback access policy beyond token sharing, retries, media validation and cross-browser tests

## Phase 5 — creator dashboard
- [x] Upload/list/status/visibility/delete UI and usage summary
- [ ] Search, sorting, folders, full analytics and accessibility polish

## Phase 6 — sharing/collaboration
- [x] Secure random share tokens stored as SHA-256 hashes, expiry/revocation-aware access, comments CRUD for workspace members
- [x] Schema for playback events
- [ ] Embed page, playback-event ingestion, invitations and full role management

## Phase 7 — INR billing
- [x] INR plan seed data and usage endpoint
- [x] Razorpay test-mode subscription creation when test keys and test plan IDs are configured
- [x] Webhook HMAC verification and duplicate event suppression
- [ ] Invoice/tax documents, cancellation UI, reconciliation and end-to-end Razorpay test-mode verification
- [x] Live mode explicitly blocked by API configuration

## Phase 8 — release readiness
- [x] Local-only scope documented; cloud deployment deferred
- [ ] Tenant-isolation integration tests, dependency audit, upload fuzzing, load tests and restore drills

This is a local MVP, not a production-ready hosted video service. HTTP Basic credentials are stored in sessionStorage in the development UI; do not deploy this setup publicly.
