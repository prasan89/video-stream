# Implementation status — local only

- [x] PostgreSQL Compose and Flyway baseline
- [x] Next.js config compatibility fix and framework upgrade
- [x] Registration, BCrypt hashing, database-backed HTTP Basic, OWNER workspace creation
- [x] Owner-scoped video CRUD, local upload, asynchronous FFmpeg thumbnail/HLS processing
- [x] Usage summary and schema foundations for shares/comments/playback events
- [ ] Verify all CI jobs, migrations and clean local startup
- [ ] Resumable uploads, checksum validation, quotas and recovery
- [ ] HLS.js playback UI, public/unlisted share-token authorization and embed pages
- [ ] Comments, invitations, roles UI, search/sorting/folders and full analytics
- [ ] Razorpay test checkout, verified/idempotent webhooks, subscription lifecycle and invoices
- [ ] Password reset, email verification, OIDC/MFA, rate limits and abuse/moderation controls
- [ ] Tenant-isolation integration tests, dependency audit, load tests, restore drills

No GCP deployment or live payments are enabled. This is a local MVP, not production-ready hosting software.
