# Release readiness — 2026-10-26

This is the current gate list for the Bedrock text release. It supplements [DEVELOPMENT_PLAN](DEVELOPMENT_PLAN.md) and the [Step 16 handoff](handoffs/STEP-16.md). Local changes are reviewable in the working tree; they are not a deployed release.

## Verified locally

- Frontend: full ESLint, production Vite build, and four API test files pass. The client now uses the v2 stream contract, canonical onboarding fields, versioned memory actions, server-owned Razorpay orders, and server-provided usage status. Account deletion and logout preserve the local session when the server operation fails. The product copy no longer asserts end-to-end encryption, certifications, unlimited use, or recurring billing. Route loading is split and an unused Spline package import was removed; the built landing chunk is about 26 kB instead of about 2 MB, while the visual iframe remains.
- Backend: `bash ./mvnw test -q` completes after configuring Mockito as a test JVM agent. Its 104 tests report 79 passed, zero failures/errors, and 25 skipped because this host has no Docker socket. Earlier GitHub CI evidence for PostgreSQL/Redis appears in the step handoffs; it does not validate this working-tree patch.
- No live AWS resource, provider call, payment, Google callback, database migration, or production traffic switch was performed in this local pass.
- On 2026-10-26, temporary AWS CLI login succeeded for the AiTherapy project. `sts:GetCallerIdentity` returned account `492613852824` through `AccountFullAccessRole`. The user reports Bedrock inference is working and directed us to skip quota checks; that report has not been independently tested here.

## Remaining gates, in order

1. **Container-backed regression:** run the current revision in a Docker-capable CI runner and require all PostgreSQL/Redis tests to execute with no skips. Include concurrent refresh, payment capture, memory revision, and chat/usage coverage. Fix any regression before packaging an image.
2. **AWS development access — complete:** temporary CLI credentials are connected to account `492613852824` in `ap-southeast-2`, and identity was verified. The user reports that Bedrock inference is working and has asked to skip further quota checks. Exercise the configured model with synthetic content during staging integration. Obtain a deliberate data-location decision before real therapy content uses global routing.
3. **Sydney staging design — revised for cost:** [STAGING_SYDNEY_EC2_OPTION](STAGING_SYDNEY_EC2_OPTION.md) uses one Free-plan-eligible EC2 instance without a load balancer, about $18.84/month gross with the requested 00:00–07:00 India-time stop window, before credits and usage. A [daily schedule template](../infra/ec2-demo-schedule.json) is prepared but cannot be deployed until the EC2 instance ID exists. The frontend has a site-wide shutdown/restart countdown; set `VITE_DEMO_SCHEDULE_ENABLED=true` in Netlify only when the EC2 schedule is running, then verify the banner at 23:00 and 00:00 IST. Trusted IP HTTPS is feasible, but Google OAuth cannot redirect to a raw IP; the narrow Netlify callback proxy must be proven before deployment is complete. The [ECS Express alternative](STAGING_SYDNEY_DESIGN.md) is about $60.68/month gross. The Mumbai App Runner Terraform is not applicable. Obtain cost approval before starting billed resources.
4. **Staging deployment:** mirror the reviewed image by digest, run the one-off Flyway migrator against the confirmed disposable or backed-up database, start the backend, and record the API hostname, image/config/schema revisions, and rollback path. No production API base URL change yet.
5. **Live integration:** test Google OAuth ticket handoff, access/refresh/logout/deletion, FREE/PRO chat and SSE cancellation/reconciliation, quota reset, memory review/conflicts/forget/reset, Razorpay test-mode checkout and signed duplicate webhooks, encrypted-data restore, alarms, and cost. Remove the legacy token-in-URL callback only after the ticket flow works in staging.
6. **Release:** publish the Step 17 backend handoff with actual staging coordinates and image digest, finish Steps 18–19 browser acceptance, then record a compatible frontend/backend/schema manifest and execute Step 20 cutover and rollback rehearsal. Keep live traffic unchanged until those gates pass.

## Inputs required for external gates

- Refresh the temporary AWS CLI session when it expires; use the user's Google sign-in through AWS's normal project flow.
- Confirmation that global cross-region AI processing is acceptable for real user content, if that model is selected. Synthetic prompts can be used for initial checks.
- Approval of a concrete Sydney staging design and gross cost after it is priced. The existing $15 monthly budget is an alert, not a spending cap.
- Access to Google OAuth and Razorpay **test-mode** settings once a staging HTTPS callback/webhook URL exists. Never paste secret values into reports or chat.
