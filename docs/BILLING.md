# Billing and INR

- Store monetary amounts as integer paise (`BIGINT`); do not use floating point.
- Seed plans are illustrative only. Product owner must approve final prices/limits.
- Create provider plan/subscription identifiers server-side.
- Verify webhook signatures using official provider instructions and raw request bytes.
- Store provider event IDs in a unique constraint to make duplicate deliveries safe.
- Model lifecycle states explicitly: INCOMPLETE, TRIALING, ACTIVE, PAST_DUE, CANCELLED, EXPIRED.
- Use a reconciliation job to compare local state with provider state.
- Keep test-mode and live-mode credentials separate.
- GST invoices, tax presentation, refund rules and recurring-payment compliance require review before launch.
