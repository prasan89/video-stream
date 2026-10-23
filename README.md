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
Local MVP uses HTTP Basic and BCrypt-hashed passwords, with CORS restricted to localhost. Use a unique development password of at least 12 characters. The development UI stores the Basic credential in sessionStorage; this is not production-grade. Production requires HTTPS, a reviewed OIDC/session architecture, recovery flow, rate limits and MFA options.

## Optional Razorpay test mode
Create test-mode plans in your Razorpay test account, then set `RAZORPAY_MODE=test`, test key ID/secret, webhook secret, and the corresponding test plan IDs in your local environment. Configure the webhook URL to `/api/v1/webhooks/razorpay` for your local test tunnel only if you understand the exposure. The API refuses any mode other than `test`. Never use live credentials. This flow has not yet been verified end-to-end against Razorpay.

## Checks
```bash
cd services/api && mvn test
cd ../../apps/web && npm test && npm run lint && npm run build
```

## Remaining gaps
Resumable uploads, share/embed UI, comments UI, invitations, full analytics, invoice/tax documents, cancellation UI, reconciliation, account recovery, production hardening, backup/restore automation and load tests remain incomplete. No GCP deployment or live payments are enabled. See `docs/IMPLEMENTATION_STATUS.md`.
