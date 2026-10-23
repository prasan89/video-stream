# Terraform deployment outline

This directory intentionally contains deployment guidance only. Create separate Terraform modules for:
- APIs and service accounts
- Cloud Run services
- Cloud SQL PostgreSQL
- private Cloud Storage buckets
- Secret Manager bindings
- Cloud Monitoring alerts and budget notifications
- Pub/Sub / Cloud Tasks if required

Before applying:
1. Select the intended GCP project and region.
2. Configure billing budgets and quotas.
3. Use remote Terraform state with restricted access.
4. Do not commit state files, secrets or service account keys.
5. Review egress, Cloud SQL HA, backups, CDN and transcoding cost assumptions.
6. Keep staging and production isolated.

Do not run `terraform apply` until costs, IAM permissions and environment settings have been reviewed.
