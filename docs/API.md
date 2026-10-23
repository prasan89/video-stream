# API contracts (target)

Base path: `/api/v1`. JSON requests/responses unless noted. All workspace APIs require authentication and server-side membership checks.\n\n## Tenant selection (implemented)\n- `GET /workspaces` lists only workspaces the authenticated user belongs to.\n- Send `X-Workspace-Id: <workspace UUID>` on workspace-scoped API requests when the user belongs to more than one workspace. The API verifies membership for every requested workspace; an arbitrary ID is never accepted as authorization.\n- If the user has exactly one workspace, the API selects it automatically when the header is omitted. If multiple workspaces exist and no header is provided, the API returns `400` and requires explicit selection.\n- Usage and quota enforcement are calculated per workspace. New original video files and resumable chunks use workspace-specific local directories under `var/media/workspaces/<workspaceId>/`. Legacy object keys remain supported until a verified file migration is performed.

## Authentication
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /me`

## Workspaces
- `POST /workspaces`
- `GET /workspaces/current`
- `GET /workspaces/current/members`
- `POST /workspaces/current/invitations`

## Plans and billing
- `GET /plans`
- `POST /billing/checkout` — idempotency key required
- `GET /billing/subscription`
- `POST /billing/cancel`
- `GET /usage`
- `POST /webhooks/razorpay` — raw body + signature header; no user session required, signature mandatory

## Videos (later phase)
- `POST /videos`
- `GET /videos`
- `GET /videos/{id}`
- `PATCH /videos/{id}`
- `DELETE /videos/{id}`
- `POST /videos/{id}/upload-session`
- `POST /videos/{id}/complete-upload`
- `GET /videos/{id}/processing`
- `POST /videos/{id}/playback-token`

## Error shape
```json
{
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": "The workspace plan limit has been reached.",
    "requestId": "..."
  }
}
```

## Status conventions
- 400 validation error
- 401 unauthenticated
- 403 unauthorized
- 404 missing/inaccessible resource
- 409 state conflict/idempotency conflict
- 413 payload too large
- 429 rate limit
- 5xx server or dependency failure
