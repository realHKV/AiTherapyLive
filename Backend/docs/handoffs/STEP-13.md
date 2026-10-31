# Step 13 handoff — backend acceptance and contract freeze

Status: **complete** on `codex/backend-baseline` at `db1c9c7`. GitHub Actions [run 39581071817](https://github.com/realHKV/AiTherapyLive/actions/runs/39581071817) passed the full PostgreSQL/Redis-backed suite.

## Work completed in this checkpoint

- Froze `docs/api/openapi.yaml`, `docs/api/README.md`, and `docs/api/streaming.md` against implemented controller behavior. The docs now distinguish compatible unversioned session/message calls from the disabled unversioned SSE endpoint and describe the v2 event contract. Removed stale `pending-step-*` markers for implemented routes.
- Added a common JSON error envelope for Spring controller errors, request validation, missing routes, authentication, authorization, OAuth callback identity rejection, and Redis admission failures. Validation responses include only field names and bounded constraint messages. Internal exception details and request values are not returned.
- Added a focused retrieval evaluation for changing chat topics and irrelevant high-importance facts. Repository context queries also require an explicit confirmation timestamp in addition to active status, current revision, and eligible source.
- Added PostgreSQL-backed tests for prompt exclusion of unconfirmed and source-suppressed memories, concurrent duplicate payment callbacks granting one payment/subscription period, and unversioned session/message compatibility through the accounted workflow.
- The concurrent payment test exposed that loading a managed purchase before waiting on the account lock could leave an earlier pending snapshot in the persistence context. Ownership now loads as a scalar ID before the locked purchase read, and PostgreSQL tests cover duplicate delivery and one payment ID replayed against a second purchase.
- Updated the development tracker and backend reference to current Step 11–13 status.

## Verification

- Full local Maven suite: 104 tests discovered, 79 passed, 25 PostgreSQL/Redis container tests skipped because Docker is unavailable locally. No failures or errors.
- Focused `PromptBuilderAcceptanceTest` and `SecurityHttpTest`: 22 passed, no failures/errors/skips.
- OpenAPI YAML parsed successfully with PyYAML.
- GitHub Actions [run 39581071817](https://github.com/realHKV/AiTherapyLive/actions/runs/39581071817): 104 passed, zero failures/errors/skips. Container-backed additions ran against PostgreSQL and Redis.
- No live Bedrock, Google, or Razorpay request was made. Those account-backed checks belong to Step 16 and are not represented as completed.

## Compatibility, rollout, and rollback

- No database migration or persisted data shape changed. JSON error responses now consistently include the existing `success`, `data`, `error`, and `timestamp` envelope; `error.details` is an optional additive validation field. Clients should continue to read `error.code` and `error.message` and ignore unknown fields.
- Synthetic PostgreSQL encrypted-field fixtures remained readable through a separately initialized encryption service using the same test key. This validates app-level same-key recovery behavior; it is not a substitute for the real backup restore rehearsal in Step 16.
- Unversioned session/message HTTP request and success shapes remain compatible and now run through v2 accounting. Unversioned SSE continues to return a sanitized 503 until the frontend migration in Step 17. Versioned SSE remains opt-in through server configuration and the version header.
- Rollback is a code revert of the implementation and matching contract docs; there is no schema rollback. The legacy stream should remain disabled during rollback unless its prior accounting and cancellation limitations are deliberately accepted.

## Remaining gates

- Real AWS Bedrock model access in Mumbai, Google OAuth staging, and Razorpay test-mode validation remain Step 16 gates. The user has an AWS account but has not configured services; before live Bedrock evaluation, ask them to enable/select the needed model IDs and approve an account budget/limit. No AWS resources are needed for these code and CI checks.
