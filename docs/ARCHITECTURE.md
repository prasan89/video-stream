# Architecture

## Current runtime (local only)
- `apps/web`: Next.js App Router and TypeScript, run with `npm run dev`.
- `services/api`: Spring Boot modular monolith, run with `mvn spring-boot:run`.
- PostgreSQL: local Docker Compose container with Flyway migrations.

## Deferred cloud target (documentation only; do not provision)
Future versions may evaluate Cloud Run, Cloud SQL, Cloud Storage, Transcoder API, Cloud Tasks/Pub/Sub, Secret Manager, monitoring and CDN after the local MVP works and costs are validated.

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
