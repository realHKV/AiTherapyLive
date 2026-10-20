# API contract decisions and implementation status

This is the Step 05 contract draft. [openapi.yaml](openapi.yaml) records every current controller route and labels future operations with `x-implementation: pending-step-NN`. [streaming.md](streaming.md) separates the current SSE bytes from the proposed opt-in protocol. Fixtures contain synthetic content only.

## Compatibility rule

Keep existing `/api/v1` methods, success envelopes, field names, and default SSE behavior until the frontend is migrated in Phase F. Add new fields and routes without requiring them from old clients. New stream semantics require `X-Chat-Stream-Version: 2`; absent header retains current behavior. Do not repurpose a current path or silently replace its response shape. The current frontend unwraps successful `ApiResponse.data`; its `sendMessage` method unwraps twice, and its stream fallback omits `/api/v1`. The fixed Razorpay link cannot satisfy the server-owned purchase flow. These are Phase F client changes, not reasons to weaken backend authorization.

## Implemented HTTP behavior

- Authenticated routes require `Authorization: Bearer <access-token>`. Auth register/login/refresh/logout and the opt-in OAuth ticket exchange are public matchers; the payment webhook is public only after raw-body HMAC verification. Account deletion requires a bearer token. `/dev/*` routes exist only with `DEV_MODE=true` and still require authentication.
- JSON successes use `{success:true,data:...,error:null,timestamp:"UTC ISO-8601"}`. `data` can be null for end/refresh-profile. Memory deletion returns 204 with no body. The webhook returns plain text. Current errors vary by controller, filter, validation, and uncaught exception; a universal error envelope is **not implemented**.
- Current profile fields are `preferredName`, `ageRange`, `communicationStyle`, `aiPersona`, and `topicsOfConcern`. PUT ignores nulls and cannot clear a field. Memory JSON uses `resolved`, not `isResolved`; subscription JSON uses `isPro`. `occurredAt` and `followUpAt` are `YYYY-MM-DD` or null; timestamps such as `startedAt` and `proExpiresAt` are ISO-8601 instants or null.
- `GET /profile/me/memories` defaults to `offset=0,limit=20`, but current code rounds arbitrary offsets to pages and lacks safe bounds. The spec records current defaults without claiming validated bounds.
- `POST /payment/purchases` returns a server-owned purchase and Razorpay order. `POST /payment/verify` accepts only a payment ID bound to that purchase. The old shared payment link is incompatible and must be retired in Phase F.

## Proposed profile and onboarding shape (Step 06)

Canonical new fields are `personaId`, `onboardingComplete`, `gender`, `country`, `interests`, and `additionalNote`, alongside current fields. `topicsOfConcern` and `interests` are arrays of strings, never JSON-encoded strings. `personaId` is a server-approved catalog ID; `communicationStyle` remains a separate preference. The frontend currently sends `aiPersona` for a therapist ID, `topics_of_concern` and `enjoyments` as JSON strings. Step 06 must accept those legacy aliases during transition, map them to one stored meaning, and return the current `aiPersona` projection until Phase F. The canonical response may add `personaId` and explicit `onboardingComplete`; completion is computed from validated onboarding state, never inferred from a nonempty default persona.

For the future validated contract, choose limits before implementation and test them end to end: preferred name at most 100 Unicode code points, age range at most 50, communication style at most 50, each topic/interest at most 100 with at most 20 entries, and additional note at most 500 (matching the present UI counter). Trim surrounding whitespace; reject blank required choices and oversized payloads with field-level `VALIDATION_FAILED`. Empty array means clear that list; omitted field means unchanged; explicit null clears only nullable fields. A profile reset is a distinct operation, not an accidental null update. These are **proposed rules**, not current validation guarantees.

## Proposed memory lifecycle (Step 06)

The existing manual memory POST remains usable. New extracted candidates start `pending` and must not enter prompts. A user can list, confirm/correct, or dismiss owned candidates. Confirmed memories can be edited, resolved, or forgotten. The new response adds `status`, `sourceKind`, `confirmedAt`, `updatedAt`, `version`, and relevant source evidence; `resolved` remains a compatibility projection. Dates remain nullable when unknown. Do not accept a client-supplied owner, evidence ID, or effective status that bypasses confirmation.

Mutations to versioned candidates/memories require `If-Match: "<decimal version>"`. A missing precondition returns 428; a stale one returns 412 and changes nothing; an absent or other user's ID returns 404. A successful edit returns the updated memory and its new quoted ETag. `forget` removes the memory from future AI context, suppresses known supporting sources, invalidates affected summaries, and advances the user's context revision. `context-reset` advances a source eligibility boundary so old transcripts cannot repopulate reset context. Already-sent model requests cannot be retracted. The current DELETE memory route only removes a row; it is **not** the proposed forget guarantee.

## Proposed error and usage contract (Steps 08, 10, 13)

Normalize failures to the existing top-level shape, keeping `success:false,data:null,error:{code,message},timestamp`. Add optional `requestId` and field `details` without removing existing fields. Never put secrets, internal exception text, or user content in an error. Stable proposed codes include `VALIDATION_FAILED` (400), `AUTH_REQUIRED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404, also for foreign-owned objects), `IDEMPOTENCY_CONFLICT` (409), `VERSION_MISMATCH` (412), `PRECONDITION_REQUIRED` (428), `RATE_LIMITED`/`USAGE_EXHAUSTED` (429), `PROVIDER_UNAVAILABLE` (503), and `ACCOUNTING_UNAVAILABLE` (503). Current controller-specific codes remain documented by the code until a common handler is implemented and tested.

For 429, return `Retry-After` as nonnegative seconds and `X-Usage-Reset-At` as an ISO-8601 UTC instant. `GET /usage/status` will expose server-derived tier, remaining request/token allowance, reserved usage, reset instant, and configured policy ID; the client cannot select effective tier or model. A provider failure before streaming starts is 503 with `PROVIDER_UNAVAILABLE`; after `start`, it is a terminal SSE `error`. Accounting unavailable fails closed for new billable work. Money and token caps remain configuration inputs pending Bedrock evaluation.

## Auth and payment transition

The existing OAuth redirect sends a token query parameter by default for the current frontend. With `OAUTH_TICKET_HANDOFF=true`, it sends a short-lived one-use `ticket`; `POST /auth/oauth/exchange` returns AuthResponse and `Cache-Control: no-store`. Phase F will adopt the ticket flow, then remove legacy redirect tokens after compatibility is verified. The `/auth/oauth/exchange` route exists now, but a Google fixture and staging round trip are still pending.

The purchase response contains `purchaseId`, `orderId`, `keyId`, `amountPaise`, and `currency`. Clients start Razorpay Checkout with the returned order ID, then submit the payment ID; the backend independently verifies captured amount/currency/order/ownership. Webhook retries are allowed and duplicate captures must not grant multiple entitlements. The current backend implements the purchase binding, but database concurrency validation remains a Step 03 gate.

## What later steps must prove

Step 06 implements and tests onboarding, versioned memory, forget/reset, limits, and aliases. Step 08 implements usage and 429 metadata. Step 10 implements idempotent chat and v2 streaming. Step 13 freezes the error envelope and runs HTTP client tests against PostgreSQL/Redis. The spec's pending labels stay until each relevant gate passes. No frontend code changes are part of Step 05.
