# Launch checklist

This checklist tracks the text-chat launch. Evidence in RELEASE_READINESS.md and the step handoffs is historical; rerun the gates for the exact release and current infrastructure. Voice is a later release.

- [ ] Agree on launch domain, hosting, data location, availability hours, expected users, owner and spending ceiling; approve any cross-region processing.
- [ ] Review existing working-tree changes and prepare a versioned release candidate. Run frontend lint/build/tests, backend tests and PostgreSQL/Redis integration tests in CI without skips; scan dependencies and the container image.
- [ ] Verify Google sign-in, ticket handoff, refresh, logout and deletion on the staging domain. Check OAuth callbacks, secrets, TLS and CORS. Retire token-in-URL only after ticket handoff is verified.
- [ ] Test free and paid chat, streaming interruption, retries, idempotency, quota exhaustion/reset and session resume on desktop and mobile. Confirm model access, feature flags and safety behavior with synthetic content.
- [ ] Test onboarding, guide selection, profile, memory review, confirmation, edits, forget/reset, account isolation and extraction job recovery.
- [ ] Test ₹100 (10,000 paise) new orders with Razorpay test mode, signed webhooks, duplicates, wrong amounts, failed checkout and 30-day entitlement. Check that existing ₹50 pending orders settle against their stored amount within the same Razorpay mode. Review pricing, terms, privacy and support.
- [ ] **Activate Razorpay live mode:** confirm merchant activation/KYC, settlement bank details, enabled payment methods and capture settings in the Razorpay dashboard. Generate a separate live API key pair and store it securely in production runtime secrets as `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`; do not paste secrets into chat or commit them. Keep staging on test keys. Configure a live-mode `payment.captured` webhook at the production `/api/v1/payment/webhook` URL and put its matching secret in `RAZORPAY_WEBHOOK_SECRET`; verify the real external routing before activation.
- [ ] Before opening paid traffic, verify the production deployment uses the intended live key ID, ₹100 price and live webhook configuration without exposing secrets. Plan how to resolve outstanding test-mode or old live-mode orders; test and live provider objects are separate. After an authorized live purchase, reconcile capture, webhook delivery, the correct user's 30-day entitlement and settlement; record the result and monitor failures. A real payment moves money and needs a separately agreed smoke procedure.
- [ ] Verify backups and encrypted-data restore with the correct keys; rehearse deployment rollback and any database migration with a write-pause/reconciliation plan. Check migrations V1–V10 against the target database.
- [ ] Verify application alerts, uptime schedule, logs, usage accounting, concurrency limits, spending controls and budget alerts. Measure actual AI, hosting and payment costs against ₹100 pricing.
- [ ] Complete browser acceptance for current UI changes, accessibility and reduced motion; check public links, SEO and legal copy.
- [ ] Record exact frontend commit, backend image digest, schema version, configuration, domains and rollback steps. Sign off staging, then publish compatible versions and verify production using an agreed smoke procedure.

Current AWS/hosting state, provider access and quotas, live OAuth/webhook settings, payment account, budget and data residency were not checked in this local review. Old staging results are not evidence for the current release.
