# Step 16 — Staging validation and recovery rehearsal

**Status: in progress, staging not deployed.** This page records executed evidence and the remaining gates. The selected backend is the low-cost Mumbai App Runner stack from [Step 15](STEP-15.md), with Netlify as frontend and existing Neon/Upstash services. Do not label Step 16 complete based on local probes or CI alone.

## Evidence obtained before AWS deployment

- The user confirmed the AWS account is on the **Free plan** with $100 credit available. AWS account identity, credit expiry, service eligibility, and billing controls still need verification in the account. Avoid creating or joining AWS Organizations because AWS says that expires Free Tier credits. Use temporary browser-based `aws login` credentials for this single account. [AWS plan rules](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-plans.html), [CLI sign-in](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sign-in.html).
- The existing Neon credentials passed a read-only JDBC `SELECT 1` with `sslmode=verify-full` and Java's default SSL factory on 2026-10-21. No schema or row was changed. The user says the database is empty/disposable; recheck before running Flyway.
- The existing Upstash host passed a TLS hostname/certificate check, Redis `AUTH`, and read-only `PING` on 2026-10-22. No key was written.
- Commit `9a401f4` passed the [backend CI](https://github.com/realHKV/AiTherapyLive/actions/runs/39593692975) PostgreSQL/Redis tests, infrastructure mocked plans, image build, migration/application smoke, and scans. This does not establish live Bedrock, OAuth, payment, or AWS behavior.

## Deployment gates

1. Verify account ID with `aws sts get-caller-identity`, Mumbai region, actual credit expiry, App Runner/Bedrock availability under the Free plan, and root MFA. Establish and verify the $15/month notification email before a warm service is created. No AWS Organization or long-lived access key is needed.
2. Review Mumbai cost estimate, one-instance App Runner resource size and Bedrock limits. Create protected Terraform state and AWS secret references. Keep secret values out of Git, Terraform variables/state, and chat. Check Google OAuth and Razorpay test-mode credentials before starting the service.
3. Apply `create_service=false`, mirror the reviewed Step 14 image by digest, run the one-off Flyway migrator on Neon, then apply `create_service=true` with the exact ECR digest. Record the AWS service URL, image digest, migration version, and deployment operation IDs. A failed migration stops before service creation.
4. Run [`demo-smoke.sh`](../../deploy/demo-smoke.sh) against the AWS HTTPS service URL for liveness, dependency readiness, exact Netlify CORS permission, and rejection of an unexpected origin. Confirm CloudWatch logs and the 5xx alarm without exposing secrets or therapy data.

## Staging acceptance matrix — not yet executed

| Area | Required live evidence |
| --- | --- |
| Auth and Google | Synthetic registration/login/refresh/logout; real Google redirect and callback host use HTTPS; no token in URL; cross-instance state after restart. |
| FREE/PRO and Bedrock | Approved Mumbai models reachable; provider failures sanitized; quota, daily reset, tier expiry, account spend cap, and model routing verified with measured token/cost usage. |
| Streaming and jobs | Versioned SSE delivers incremental chunks and exactly one terminal state; cancellation, timeout, task replacement, queued job retry, and restart behavior checked. |
| Payments | Razorpay **test mode** order, server-side verification, signed webhook, duplicate delivery, invalid signature, ownership, and expiry checked with synthetic users only. |
| Recovery | Create a synthetic encrypted fixture and Flyway history, rehearse Neon branch/restore with the same encryption key, then deploy and restore a compatible prior image/configuration. Image rollback does not reverse schema migrations. |
| Operations and cost | Check log retention, alarm delivery, App Runner and Bedrock spend, credit drawdown, and resulting concurrency/resource settings. |

Do not route real users or move Netlify's API base URL until the matrix passes. The frontend's known stream URL mismatch is scheduled for Steps 17–19.
