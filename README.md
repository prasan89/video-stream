# VideoCloud — local Vimeo-style platform

Local deployment only. No GCP resources, Terraform applies, or production payments are part of this milestone.

## Prerequisites
- Java 21, Maven 3.9+, Node.js 22+, Docker
- FFmpeg installed and available on PATH for video processing

## Start locally
From repository root, terminal 1:
```bash
docker compose up -d postgres
```
Terminal 2:
```bash
cd services/api && mvn spring-boot:run
```
Terminal 3:
```bash
cd apps/web && cp .env.example .env.local && npm install && npm run dev
```
Open http://localhost:3000 and visit Dashboard. API health: http://localhost:8080/actuator/health.

## Video processing
Install FFmpeg locally. Uploads and HLS outputs are stored under `./var/media`. A background task generates a thumbnail and HLS playlist. This single-node local storage implementation is not designed for public hosting.

## Authentication
Local MVP uses HTTP Basic and BCrypt-hashed passwords, with CORS restricted to localhost. Use a unique development password of at least 12 characters. Basic authentication is local-only; production requires HTTPS and a reviewed OIDC/session architecture, recovery flow, rate limits and MFA options.

## Checks
```bash
cd services/api && mvn test
cd ../../apps/web && npm test && npm run lint && npm run build
```

## Remaining gaps
Resumable uploads, share-token endpoints, embeds, comments/invitations, full analytics, Razorpay checkout/webhooks/invoices, account recovery, production hardening, backup/restore automation and load tests remain incomplete. No payments are collected. See `docs/IMPLEMENTATION_STATUS.md`.
