# Step 05 — HTTP and streaming contract specification

Status: **complete for specification**. Future endpoints/events remain pending their numbered implementation steps.

## Outcome and files

- Added `docs/api/openapi.yaml` with current controller routes, request/response schemas, security requirements, and explicit `x-implementation` labels for current, opt-in, development-only, and pending operations.
- Added `docs/api/README.md` with compatibility, canonical onboarding/persona field choices, proposed validation and null semantics, memory version/ownership/forget rules, error codes, 429/reset metadata, usage status, OAuth ticket transition, and payment purchase flow.
- Added `docs/api/streaming.md` plus a synthetic v2 SSE fixture. The existing unnamed chunk protocol remains the default. The proposed event protocol requires `X-Chat-Stream-Version: 2` and an idempotency key and is marked pending Step 10.
- Added synthetic current JSON examples and a serialization regression test for `resolved`, `isPro`, nullable dates, success/error envelope, and UTC instant formatting. No migration, configuration, runtime route, or frontend code changed.

## Validation performed

| Check | Result |
| --- | --- |
| YAML parse with duplicate-key rejection, local `$ref` resolution, and `x-implementation` on every operation | Passed: 29 paths; JSON fixture parsed |
| Focused `ApiResponseJsonTest` | Passed: 3 tests. The first assertion revealed that memory serializes `resolved`, which the spec now records. |
| `bash ./mvnw test -q` | Passed: 45 reported, 0 failures, 0 errors, 2 skipped |

The two Testcontainers tests skipped because `/var/run/docker.sock` is unavailable; they are Step 00/04 integration gates, not evidence for an HTTP deployment. No live browser, Google OAuth, Razorpay, Bedrock, or AWS check ran. The proposed stream fixture is not a live server capture.

## Compatibility and rollout

The decision is to preserve existing `/api/v1` methods, successful response shape, `resolved`/`isPro` names, and default SSE bytes. New onboarding and memory fields/routes are additive; `aiPersona`, `topics_of_concern`, and `enjoyments` need compatibility aliases during Phase F. The new stream requires an explicit version header. Existing error formats remain inconsistent and are described as such; Step 13 must freeze a common envelope only after HTTP tests prove it.

This step is documentation and test-only; rollback is a revert of these files. No traffic switch or data migration is needed. Future features must not be advertised or called before their implementation status changes to current. The old frontend's shared Razorpay link, double-unwrapped chat response, and stream fallback URL are documented for Phase F; none were silently changed here.

## Outstanding implementation

- Step 06: canonical onboarding fields, explicit completion, validated limits, versioned memory review/edit/resolve/forget/reset, and source suppression.
- Step 08: usage status, server-derived limits, 429 metadata, and accounting failure behavior.
- Step 10: idempotent generation, v2 terminal stream events, cancellation, persisted request status, and settlement.
- Step 13 and Phase F: full HTTP contract acceptance with PostgreSQL/Redis and frontend integration, including consistent errors and Google/Razorpay round trips.
