# Architecture

## Runtime
- `apps/web`: Next.js App Router, TypeScript, responsive SaaS UI.
- `services/api`: Spring Boot modular monolith, REST `/api/v1`, Bean Validation, Spring Security.
- PostgreSQL: transactional metadata and billing state.
- GCP Cloud Run: stateless web/API containers.
- Cloud SQL: managed PostgreSQL.
- Cloud Storage: private original and output media buckets.
- Transcoder API: asynchronous HLS renditions and thumbnails.
- Cloud Tasks/Pub/Sub: retryable async workflows when needed.
- Secret Manager: provider keys and database secrets.
- Cloud Monitoring/Logging: metrics, logs and alerts.

## Data flow: future video upload
1. Client asks API to create a video and upload session.
2. API verifies workspace role, plan quota, file size and content-type allowlist.
3. API issues short-lived upload authorization for a private object.
4. Browser uploads directly to Cloud Storage.
5. API verifies object metadata and actual size; client claims are not trusted.
6. API creates a transcoding job and queues work.
7. Transcoder writes outputs to a private bucket.
8. A verified callback or poller updates job/video state.
9. Playback service issues scoped, short-lived authorization.
10. Delivery layer enforces access to manifest and every segment, not just the manifest.

## Architecture decisions
- Start with a modular monolith; split services only after measured scaling or ownership needs.
- Store media bytes in object storage, not PostgreSQL.
- Store object keys rather than public URLs.
- Use asynchronous jobs for transcoding, captions and analytics.
- Keep billing provider integration behind an interface.
- Never use client-side subscription state as an entitlement source.

## Deployment environments
- Local: Docker Compose PostgreSQL.
- Staging: isolated GCP project or isolated resources; test payment mode only.
- Production: separate secrets, service accounts, buckets, database and billing credentials.
