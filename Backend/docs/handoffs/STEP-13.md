# Step 13 handoff — backend acceptance and contract freeze

Status: **in progress** on `codex/backend-baseline`. The full PostgreSQL/Redis GitHub Actions run is pending before this step can be marked complete.

## Work completed in this checkpoint

- Froze `docs/api/openapi.yaml`, `docs/api/README.md`, and `docs/api/streaming.md` against implemented controller behavior. The docs now distinguish compatible unversioned session/message calls from the disabled unversioned SSE endpoint and describe the v2 event contract. Removed stale `pending-step-*` markers for implemented routes.
- Added a common JSON error envelope for Spring controller errors, request validation, missing routes, authentication, authorization, OAuth callback identity rejection, and Redis admission failures. Validation responses include only field names and bounded constraint messages. Internal exception details and request values are not returned.
- Added a focused retrieval evaluation for changing chat topics and irrelevant high-importance facts. Repository context queries also require an explicit confirmation timestamp in addition to active status, current revision, and eligible source.
- Added PostgreSQL-backed tests for prompt exclusion of unconfirmed and source-suppressed memories, concurrent duplicate payment callbacks granting one payment/subscription period, and unversioned session/message compatibility through the accounted workflow.
- Updated the development tracker and backend reference to current Step 11–13 status.

## Verification

- Full local Maven suite: 103 tests discovered, 79 passed, 24 PostgreSQL/Redis container tests skipped because Docker is unavailable locally. No failures or errors.
- Focused `PromptBuilderAcceptanceTest` and `SecurityHttpTest`: 22 passed, no failures/errors/skips.
- OpenAPI YAML parsed successfully with PyYAML.
- CI PostgreSQL/Redis execution remains pending. The local container-backed additions have not run in this environment.
- No live Bedrock, Google, or Razorpay request was made. Those account-backed checks belong to Step 16 and are not represented as completed.

## Compatibility, rollout, and rollback

- No database migration or persisted data shape changed. JSON error responses now consistently include the existing `success`, `data`, `error`, and `timestamp` envelope; `error.details` is an optional additive validation field. Clients should continue to read `error.code` and `error.message` and ignore unknown fields.
- Unversioned session/message HTTP request and success shapes remain compatible and now run through v2 accounting. Unversioned SSE continues to return a sanitized 503 until the frontend migration in Step 17. Versioned SSE remains opt-in through server configuration and the version header.
- Rollback is a code revert of the implementation and matching contract docs; there is no schema rollback. The legacy stream should remain disabled during rollback unless its prior accounting and cancellation limitations are deliberately accepted.

## Remaining gates

- Run the full GitHub Actions suite with PostgreSQL and Redis containers, fix any failures, then update this report and tracker with the final commit and run link.
- Real AWS Bedrock model access in Mumbai, Google OAuth staging, and payment-provider live validation remain Step 16 gates. The user has an AWS account but has not configured services; before live Bedrock evaluation, ask them to enable/select the needed model IDs and approve an account budget/limit. No AWS resources are needed for the pending CI gate.
