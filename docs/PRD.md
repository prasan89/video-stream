# Product Requirements — VideoCloud

## Goal
Build an India-first professional video platform with ad-free embedding, privacy controls, reliable playback, analytics, team collaboration and later live streaming.

## Initial personas
- Course creator: uploads lessons and restricts embeds to course domains.
- Small business: hosts demos and training videos.
- Agency: manages multiple client workspaces.
- Developer: uses a stable API and webhooks.

## Phase 0
Repository, local development, database migrations, CI, environment conventions, architecture/security documentation and GCP cost guardrails.

## Phase 1
- Account registration/login, email verification and session lifecycle.
- Workspaces and member roles: OWNER, ADMIN, EDITOR, VIEWER.
- Public plan catalog, INR prices in paise, monthly/yearly billing interval.
- Subscription lifecycle and entitlement checks.
- Razorpay checkout and verified webhook processing.
- Usage summary and billing pages.

## Phase 1 acceptance criteria
- User can create a workspace and is its OWNER.
- Every workspace operation checks membership and role.
- Plan prices are stored in paise (integer).
- Entitlements are computed server-side; clients cannot set their own limits.
- Webhook signatures are verified, events are idempotent and duplicate deliveries do not duplicate side effects.
- Subscription state is auditable and reconciled against the payment provider.
- No real checkout is enabled until provider credentials and webhook verification are configured.

## Later phases
- P2: direct-to-GCS upload, validation, transcoding and secure HLS playback.
- P3: analytics and usage metering.
- P4: browser editing, AI captions and summaries.
- P5: live streaming, team/enterprise features.

## Non-functional targets
- API p95 <300 ms for ordinary metadata endpoints in the initial target environment, excluding external provider calls.
- Initial availability target: 99.5%.
- Structured logs, health checks, request IDs and actionable alerts.
- Private media by default.
- Restore-tested database backups before production launch.
