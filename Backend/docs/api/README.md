# API contract decisions and implementation status

This is the frozen Step 13 backend contract. [openapi.yaml](openapi.yaml) records implemented routes and error behavior. [streaming.md](streaming.md) specifies the v2 SSE protocol and the 503 response for the disabled legacy stream. Fixtures contain synthetic content only.

## Compatibility rule

Keep existing `/api/v1` methods, session/message success envelopes, and field names compatible until the frontend is migrated in Phase F. The legacy SSE wire format is disabled and returns 503; v2 stream semantics require `X-Chat-Stream-Version: 2`. Do not silently change request or success shapes. The current frontend unwraps successful `ApiResponse.data`; its `sendMessage` method unwraps twice, and its stream fallback omits `/api/v1`. The fixed Razorpay link cannot satisfy the server-owned purchase flow. These are Phase F client changes, not reasons to weaken backend authorization.

## Implemented HTTP behavior

- Authenticated routes require `Authorization: Bearer <access-token>`. Auth register/login/refresh/logout and the opt-in OAuth ticket exchange are public matchers; the payment webhook is public only after raw-body HMAC verification. Account deletion requires a bearer token. `/dev/*` routes exist only with `DEV_MODE=true` and still require authentication.
- JSON successes use `{success:true,data:...,error:null,timestamp:"UTC ISO-8601"}`. `data` can be null for end/refresh-profile. Memory deletion returns 204 with no body. The webhook returns plain text. JSON API failures use `{success:false,data:null,error:{code,message,details?},timestamp:"UTC ISO-8601"}`. Validation details map field names to safe constraint messages; internal exception text and submitted values are not included. Authentication, authorization, rate-limit, service, and controller errors use the same envelope. SSE terminal errors follow the separate streaming contract.
- Profile PUT keeps omitted and null fields unchanged; `clearFields` explicitly clears supported nullable fields. Memory JSON uses `resolved`, not `isResolved`; subscription JSON uses `isPro`. `occurredAt` and `followUpAt` are `YYYY-MM-DD` or null; timestamps are ISO-8601 instants or null.
- `GET /profile/me/memories` defaults to `offset=0,limit=20`; offset must be nonnegative and divisible by limit, and limit must be 1–100.
- `POST /payment/purchases` returns a server-owned purchase and Razorpay order. `POST /payment/verify` accepts only a payment ID bound to that purchase. The old shared payment link is incompatible and must be retired in Phase F.

## Profile and onboarding implementation (Step 06)

Canonical new fields are `personaId`, `onboardingComplete`, `gender`, `country`, `interests`, and `additionalNote`, alongside current fields. `topicsOfConcern` and `interests` are arrays of strings, never JSON-encoded strings. `personaId` is a server-approved catalog ID; `communicationStyle` remains a separate preference. The frontend currently sends `aiPersona` for a therapist ID, `topics_of_concern` and `enjoyments` as JSON strings. Step 06 must accept those legacy aliases during transition, map them to one stored meaning, and return the current `aiPersona` projection until Phase F. The canonical response may add `personaId` and explicit `onboardingComplete`; completion is computed from validated onboarding state, never inferred from a nonempty default persona.

The service validates preferred name at most 100 Unicode code points, age range and communication style at most 50, each topic/interest at most 100 with at most 20 entries, and additional note at most 500. It trims surrounding whitespace. Empty arrays clear a list; omitted and null fields leave it unchanged; `clearFields` explicitly clears supported fields. The dedicated context reset also deletes profile, traits, and memories. Validation uses `VALIDATION_FAILED` with safe field details.

## Memory lifecycle implementation (Step 06)

The existing manual memory POST remains usable. New extracted candidates start `pending` and must not enter prompts. A user can list, confirm/correct, or dismiss owned candidates. Confirmed memories can be edited, resolved, or forgotten. The new response adds `status`, `sourceKind`, `confirmedAt`, `updatedAt`, `version`, and relevant source evidence; `resolved` remains a compatibility projection. Dates remain nullable when unknown. Do not accept a client-supplied owner, evidence ID, or effective status that bypasses confirmation.

Mutations to versioned candidates/memories require `If-Match: "<decimal version>"`. A missing precondition returns 428; a stale one returns 412 and changes nothing; an absent or other user's ID returns 404. A successful edit returns the updated memory and its new quoted ETag. `forget` removes the memory from future AI context, suppresses its known source conversation, invalidates summaries, and advances the user's context revision. If no source is known, it conservatively excludes all earlier conversations from future context. `context-reset` also deletes profile, traits, and memories. The legacy DELETE memory route now performs the same conservative forget operation without requiring a version, preserving its request shape. Already-sent model requests cannot be retracted.

## Error and usage contract (frozen in Step 13)

Failures use `success:false,data:null,error:{code,message,details?},timestamp`. `X-Correlation-Id` is returned on HTTP responses for request tracing. Never put secrets, internal exception text, or submitted user content in an error. Stable codes are `VALIDATION_FAILED` (400), `AUTH_REQUIRED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404, including foreign-owned objects), `CONFLICT` (409), `VERSION_MISMATCH` (412), `PRECONDITION_REQUIRED` (428), `RATE_LIMITED` (429), `PROVIDER_UNAVAILABLE` (503), `ACCOUNTING_UNAVAILABLE` (503), `AUTH_UNAVAILABLE` (503 for login dependencies), and `INTERNAL_ERROR` (500). `details` is present only for validation failures and contains field names plus safe constraint messages. Existing domain-specific auth/webhook codes remain stable.

For 429, the atomic short-window filter returns `Retry-After` as nonnegative seconds and `X-Usage-Reset-At` as an ISO-8601 UTC instant. `GET /usage/status` exposes server-derived tier, remaining daily request/token allowance, reserved usage, reset instant, and configured policy ID; the client cannot select effective tier or model. The durable reservation ledger is connected to session and turn generation. A provider failure before streaming starts is 503 with `PROVIDER_UNAVAILABLE`; after `start`, it is a terminal SSE `error`. Accounting unavailable fails closed for new billable work. Money and token caps remain configuration inputs pending Bedrock evaluation.

## Auth and payment transition

The existing OAuth redirect sends a token query parameter by default for the current frontend. With `OAUTH_TICKET_HANDOFF=true`, it sends a short-lived one-use `ticket`; `POST /auth/oauth/exchange` returns AuthResponse and `Cache-Control: no-store`. Phase F will adopt the ticket flow, then remove legacy redirect tokens after compatibility is verified. The `/auth/oauth/exchange` route is implemented; Google identity-provider and staging round-trip validation remain Step 16 cloud gates.

The purchase response contains `purchaseId`, `orderId`, `keyId`, `amountPaise`, and `currency`. Clients start Razorpay Checkout with the returned order ID, then submit the payment ID; the backend independently verifies captured amount/currency/order/ownership. Webhook retries are allowed and duplicate captures must not grant multiple entitlements. The current backend implements the purchase binding, but database concurrency validation remains a Step 03 gate.

## What later steps must prove

CI acceptance runs use isolated PostgreSQL and Redis containers and exercise migrations, encrypted persistence, usage reservations, chat lifecycle, streaming events, extraction failures/retries, account deletion races, replica locks, API validation, and HTTP security behavior. A production AWS/Google/real-payment run remains an explicit Step 16 gate; provider/payment code paths are covered here with synthetic fixtures only.
