# Step 08 — Atomic quotas and durable usage accounting

Status: **complete for the accounting foundation**. No Bedrock call, AWS resource, provider model selection, or monetary spending limit was introduced.

## Implemented

- Flyway V7 adds `ai_requests`, `ai_attempts`, and `ai_usage_buckets`. The request ledger has one `(user_id, idempotency_key)` identity and keeps purpose, trusted tier/policy/model/region, estimates, reservations, actual usage, attempts, lease state, and sanitized failure code. It never stores prompts or generated text.
- `UsageAccountingService` reserves the estimated input plus the approved maximum output against UTC daily user, account, and model-region buckets. PostgreSQL conditional upserts make concurrent reservations atomic. Repeating the same logical request returns the original reservation; a changed payload under that key is an `IDEMPOTENCY_CONFLICT`.
- Settlement replaces the reservation with provider-reported usage when present. Missing final provider usage conservatively charges the reservation. A reconciliation job releases abandoned pre-provider reservations and charges expired in-flight work conservatively. Attempt records reach the same terminal state as their request.
- `RedisAdmissionService` uses Lua scripts for minute admission and a one-user owned lease. Lease renewal and release require the same request owner. Redis failures produce `ACCOUNTING_UNAVAILABLE`; the rate-limit filter returns a retryable 503 rather than allowing billable chat submission through.
- `GET /usage/status` uses the authenticated server identity and server-side entitlement. It returns the configured policy identifier, effective tier, remaining daily requests/tokens, reserved tokens, and the next UTC reset.

## Configuration

`AI_USAGE_POLICY_ID`, `AI_USAGE_FREE_*`, `AI_USAGE_PRO_*`, `AI_USAGE_ACCOUNT_*`, `AI_USAGE_MODEL_*`, and `AI_USAGE_LEASE_SECONDS` define the policy. Defaults are conservative beta controls only. `BEDROCK_REGION` is recorded in the ledger as a region label; it does not enable AWS or choose a model.

## Tests and remaining wiring

`bash ./mvnw -q test` passes locally with 62 tests and six Docker-dependent tests skipped because this machine has no Docker daemon. GitHub Actions runs the PostgreSQL/Redis suite, including the concurrent-reservation test and the V5-to-V7 migration path.

Step 10 must place durable `reserve`, `startAttempt`, lease acquire/renew/release, `settle`, and cancellation handling around each real generation. Step 11 must use the same boundary for summaries and profile extraction. A paid-entitlement-period window and a currency spend ceiling require a durable billing-period definition and measured Bedrock model prices; daily token/request/account/model-region gates are in place now.
