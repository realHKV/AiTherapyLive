# Step 10 — Transaction-safe chat and streaming lifecycle

Status: **complete; committed and pushed; PostgreSQL/Redis CI passed**.

## Implemented

- Added Flyway migration V8. It converges any historical duplicate active sessions to `abandoned`, adds a partial unique index for one active session per user, and adds idempotent session-start and chat-turn records linked to the durable usage request.
- Added opt-in v2 session start, synchronous turn, stream, and owner-scoped request-status paths. V2 requires both `AI_CHAT_V2_ENABLED=true` and `AI_ROUTING_ENABLED=true`; legacy routes remain the default for the current frontend.
- User idempotency keys are UUIDs and are compared with a SHA-256 fingerprint and conversation ID. A duplicate successful turn returns the saved answer without another provider attempt; changed payloads conflict; failed and canceled states remain durable. Repeated session end no longer increments session count or republishes completion.
- Usage admission/reservation and user-message persistence happen in a short transaction. Provider calls use a bounded worker pool after commit. Successful assistant persistence and usage settlement share a transaction. Failures never become assistant error-marker text. Unknown provider usage stays unknown in the response and settles conservatively against the reservation.
- V2 streaming sends named JSON `start`, `delta`, `complete`, and sanitized `error` events. It returns `X-Request-Id`, disables caching, closes without a success terminal when settlement cannot be recorded, and exposes status lookup for reconnects. Upstream cancellation is wired to client disconnect, first-token/total deadlines, and an after-commit session-end event.
- Added bounded conversation context and memory selection. Only active, confirmed, unexpired, current-revision memories from eligible sources are considered. Ranking uses topic overlap, due follow-up, recency, and importance; the latest context-valid summary is included. Prompt and message assembly have token/character bounds.
- Updated CORS to allow the v2 request/idempotency headers and expose request IDs and Retry-After. Updated streaming and backend reference documentation.

## Validation

- `bash ./mvnw -q -DskipTests test-compile` passed.
- Focused routing, Bedrock, chat, Redis-admission, and HTTP tests passed.
- `bash ./mvnw -q test` passed locally: 81 discovered, 69 passed, 12 skipped. The skipped Testcontainers checks include the new Postgres/Redis chat workflow tests and migration tests because Docker is unavailable here.
- GitHub Actions run [39571804383](https://github.com/realHKV/AiTherapyLive/actions/runs/39571804383) passed all 81 tests with PostgreSQL and Redis, including six chat workflow tests and a real HTTP SSE test that verifies event framing, Unicode deltas, request/cache headers, and completion.

## Remaining gates

- Before Step 13 and any frontend switch, retain a live-client cancellation check in the broader regression/contract-freeze pass; HTTP event framing and Unicode transport are already covered in CI.
- Keep v2 disabled by default until the planned frontend/traffic gate; no traffic switch has been made.
- AWS setup is not required for Step 10. The user chose Mumbai (`ap-south-1`) for Step 09's later Bedrock access/model evaluation. The AWS account still needs model access and an SSO/task-role setup before any smoke call; no credentials or AWS changes were made here.
