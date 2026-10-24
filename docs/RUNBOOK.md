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


## Legacy media migration (local-only; explicit opt-in)

The migration copies legacy media into `workspaces/{workspaceId}/videos/{videoId}/`, verifies each copy with SHA-256, updates database object keys, and retains source files. It does not delete legacy sources. The runner is disabled by default.

1. Stop uploads and processing while preparing the migration window.
2. Back up the PostgreSQL database and media directory.
3. Start the API with `VIDEO_CLOUD_LEGACY_MEDIA_MIGRATION_ENABLED=true` and `VIDEO_CLOUD_LEGACY_MEDIA_MIGRATION_DRY_RUN=true` only if these properties are mapped in your local configuration. Alternatively use Spring properties `videocloud.legacy-media-migration.enabled=true` and `videocloud.legacy-media-migration.dry-run=true`.
4. Review the complete migration report. Resolve every missing file, unsafe key, and active-upload skip before proceeding.
5. Run with `videocloud.legacy-media-migration.enabled=true` and `videocloud.legacy-media-migration.dry-run=false` only after explicit approval and a fresh backup.
6. Verify video playback, generated assets, and workspace storage usage. Keep legacy source files until verification is complete; delete them only as a separate, reviewed operation.

Do not expose this migration runner through a public API. Run it only in a controlled local maintenance window.
