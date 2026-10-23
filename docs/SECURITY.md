# Security baseline

## Identity
- Prefer a reputable OIDC identity provider unless there is a concrete reason to manage passwords.
- If passwords are stored, use Argon2id (or a reviewed modern password hashing configuration), email verification, reset tokens with expiry, and rate limits.
- Use short-lived access credentials and secure refresh/session handling.
- Apply CSRF protection when cookies authenticate state-changing requests.

## Tenant isolation
- Derive active workspace from authenticated server-side context.
- Check workspace membership and role on every tenant-owned resource access.
- Do not treat UUIDs as authorization.
- Add tests proving cross-tenant reads and writes fail.

## Billing
- Verify Razorpay webhook signatures using official provider instructions and raw request bytes.
- Deduplicate provider event IDs and make side effects transactional/idempotent.
- Do not trust browser redirects as proof of payment.
- Reconcile subscription status periodically.
- Never log secrets, full payment payloads or sensitive personal data.

## Media (future phase)
- Keep original files, manifests and segments private.
- Use short-lived scoped upload/playback authorization.
- Validate actual object size and metadata server-side.
- Protect every HLS segment and applicable encryption key; manifest-only authorization is insufficient.
- Do not claim domain/referrer checks prevent all copying.
- Apply upload caps, duration limits, MIME/container validation, job concurrency limits and rate limits.

## GCP
- Least-privilege service accounts per workload.
- Secret Manager for credentials.
- TLS and secure headers.
- Cloud audit logs and alerts for unusual spend.
- Database backups and tested restore procedures.
