# Chat streaming contract

The base URL includes `/api/v1`. The v1 stream is `POST /chat/sessions/{sessionId}/stream` with JSON `{"content":"..."}` and a bearer token. Native `EventSource` cannot issue this POST; clients use `fetch` and parse SSE incrementally. UTF-8 characters and SSE lines may be split across network chunks.

## Legacy v1 stream

The old unnamed raw-text stream is disabled. Without `X-Chat-Stream-Version: 2`, the endpoint returns HTTP 503 with the standard JSON error envelope and does not persist a message or invoke a provider. This protects usage accounting, cancellation, and durable terminal state while the frontend migrates. The current frontend's fallback URL omits `/api/v1`; Phase F will switch callers only after its acceptance tests pass.

## Opt-in v2 lifecycle

V2 requires `AI_CHAT_V2_ENABLED=true` and `AI_ROUTING_ENABLED=true`. It uses the provider-neutral router and durable usage ledger. Bedrock remains an independently configured choice; v2 does not enable AWS or select a model on its own.

### Start or resume a session

`POST /chat/sessions` accepts `X-Chat-Request-Version: 2` and a UUID `Idempotency-Key`. A repeated key returns the same session. A reload with a new key also resumes an already active session, so it cannot bill for another greeting. Complete an active session before starting a new one. Concurrent starts are serialized per account and PostgreSQL enforces at most one active session.

### Submit a non-streamed turn

`POST /chat/sessions/{sessionId}/messages` accepts `X-Chat-Request-Version: 2`, a UUID `Idempotency-Key`, and the existing JSON body. The assistant response uses the existing `ChatMessageResponse` envelope. Reusing a key with the same session and body returns the stored assistant message; a different body returns `409`. An in-progress duplicate returns `409` and does not start another provider call.

### Stream a turn

`POST /chat/sessions/{sessionId}/stream` accepts `X-Chat-Stream-Version: 2`, `Idempotency-Key: <UUID>`, and the same JSON body. The response sets `Content-Type: text/event-stream`, `Cache-Control: no-store`, and `X-Request-Id`. Events are named SSE events with one JSON object in `data:`; all carry the same request ID:

1. `start`: `{ "requestId": "UUID", "sessionId": "UUID", "userMessageId": "UUID" }`, after the owned active session, user turn, durable usage reservation, and idempotency record are committed.
2. Zero or more `delta`: `{ "requestId": "UUID", "text": "..." }`. Append the parsed text in order; deltas may split Unicode characters at the transport byte level.
3. Exactly one terminal event while the connection remains writable:
   - `complete`: `{ "requestId": "UUID", "assistantMessageId": "UUID", "status": "completed", "usage": { "inputTokens": 12, "outputTokens": 34 } }`, sent only after assistant persistence and usage settlement commit.
   - `error`: `{ "requestId": "UUID", "code": "PROVIDER_UNAVAILABLE", "message": "The AI service is temporarily unavailable.", "retryable": true, "partialPersisted": false, "usage": { "inputTokens": null, "outputTokens": null } }`, sent after failure state and usage settlement commit. Provider diagnostics, prompt text, and generated partial text are never placed in the error event.

If settlement storage is unavailable, the connection closes without a terminal event. Treat EOF without a terminal event as unknown and reconcile with `GET /chat/requests/{requestId}`. That endpoint is owner-scoped and returns the durable state, failure code, assistant message when completed, and observed or unknown usage. A repeated completed stream key sends `start` and `complete`; use the status endpoint to retrieve the stored assistant text. A repeated failed key sends the existing sanitized error. A repeated in-progress key returns `409` before opening a second stream.

The request key is retained with the durable usage and turn audit records until account deletion; only a SHA-256 body fingerprint is stored. V2 has no `id:` field and does not promise byte replay or `Last-Event-ID`. A `404` status response conceals requests owned by another user. `429` before streaming includes `Retry-After`; after `start`, provider and generation failures use the terminal `error` event.

## Persistence and cancellation

- Request validation, ownership checks, active-session checks, context assembly, admission, durable reservation, and user-message persistence happen in a short database transaction. Model calls run only after it commits.
- Messages are capped at 8,000 characters. Routed context includes at most 20 recent messages and a bounded 50-memory candidate set. Input usage is conservatively estimated at four characters per token for reservation; returned provider usage replaces that estimate when available.
- Memory retrieval considers only active, confirmed, unexpired, current-revision memories with eligible source conversations. It ranks topic overlap, due follow-ups, recency, and importance in process memory. The latest valid session summary is included within the system prompt budget. Stored profile, summary, and memory content is explicitly treated as untrusted context.
- V2 uses a 15-second first-token deadline, 60-second total generation deadline, and 90-second emitter deadline by default. All are configurable. The async worker and queue are bounded. Redis admission fails closed; the PostgreSQL reservation is authoritative and an expired lease reconciler updates both usage and chat-turn state.
- Client disconnect, generation deadline, or session end cancels upstream work where the gateway supports it. Failed or canceled turns retain the user message and durable request status; incomplete assistant text is not persisted. A late callback cannot complete a terminal request. Repeating session end does not increment the session count again.
- The default application keeps v2 disabled unless routing and v2 are explicitly enabled. Legacy HTTP session/message requests use the accounted workflow, but unversioned stream calls return 503 until the frontend adopts v2.

The [synthetic v2 SSE fixture](fixtures/stream-v2.sse) illustrates event framing. It is an example, not a captured live response. The v2 endpoint is implemented and enabled only when server-side v2 and routing configuration are enabled; frontend adoption remains gated on Phase F acceptance.
