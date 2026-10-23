# VideoCloud — Phase 0 + Phase 1 starter

An India-first, Vimeo-style video SaaS foundation. Working name only; check trademark/domain availability before launch.

## Included
- Next.js + TypeScript frontend starter
- Spring Boot + Java 21 API starter
- PostgreSQL + Flyway initial schema
- Docker Compose for local PostgreSQL
- Architecture, security, API, billing and runbook docs
- Initial unit/integration test examples
- GCP/Terraform deployment notes (not a production-ready deployment)

## Prerequisites
- Node.js 22 LTS (or a compatible supported release)
- Java 21
- Maven 3.9+
- Docker Desktop / Docker Engine
- Git

## Local setup

### 1. Start PostgreSQL
```bash
docker compose up -d postgres
```
Default local-only credentials are in `compose.yaml`. Never reuse them in production.

### 2. Start Spring Boot API
```bash
cd services/api
cp .env.example .env
mvn spring-boot:run
```
The API listens on `http://localhost:8080`; health check: `GET /actuator/health`.

### 3. Start Next.js
In another terminal:
```bash
cd apps/web
cp .env.example .env.local
npm install
npm run dev
```
The UI listens on `http://localhost:3000`.

### 4. Run tests
```bash
cd services/api && mvn test
cd ../../apps/web && npm install && npm test
```

## Phase 0 acceptance criteria
- Both applications start locally.
- PostgreSQL starts from Compose.
- Flyway migrations apply cleanly.
- Health endpoint responds.
- CI runs backend and frontend checks.
- No secrets are committed.

## Phase 1 implementation order
1. Add a managed identity provider or implement authentication with Spring Security.
2. Add registration/login/refresh/logout and email verification.
3. Add workspace creation and membership authorization.
4. Add plans and entitlement evaluation.
5. Integrate Razorpay checkout and signed, idempotent webhook handling.
6. Add billing state transition tests and usage endpoint.
7. Deploy to a private GCP staging environment before accepting real payments.

## Important limitations
This is a starter scaffold, not a finished billing or video-hosting product. Payment, authentication, signed upload, transcoding, playback authorization and production infrastructure must not be considered complete until implemented and security-tested. The current plan endpoint is a small demonstrator; no real payment is taken.

## Budget guardrails
- Set a GCP budget alert before provisioning cloud resources.
- Keep Cloud SQL small for beta; review HA and backup needs before production.
- Do not offer unlimited storage or bandwidth.
- Do not provision Media CDN until a measured delivery model justifies it.
- Keep video upload and playback traffic out of the API container.
