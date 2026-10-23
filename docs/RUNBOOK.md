# Operations runbook

## Before deploying
1. Run backend tests and frontend lint/build.
2. Review Flyway migration and rollback strategy.
3. Confirm staging secrets and payment test mode.
4. Confirm local environment variables and Docker resource availability.
5. Verify health endpoint and logs.

## Spend spike
1. Inspect local container usage and application logs.
2. Check database disk growth and local media/test fixtures.
3. Stop local workers or containers if runaway jobs are suspected.
4. Preserve logs needed for debugging.
5. Rotate exposed development credentials and review audit logs.

## Database incident
1. Declare incident and stop risky schema changes.
2. Confirm backup/point-in-time recovery availability.
3. Restore to isolated environment and validate data.
4. Follow approved recovery objectives before production cutover.

## Media processing failure (future phase)
- Inspect job state, provider job ID and sanitized error.
- Retry only idempotent operations.
- Cap retries and route persistent failures to an operator queue.
