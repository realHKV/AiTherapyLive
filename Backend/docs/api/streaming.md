# Chat streaming contract

Base path: `/api/v1/chat/sessions/{sessionId}/stream`. The client uses `POST` with JSON `{"content":"..."}` and a bearer token. Native `EventSource` cannot issue this POST; use `fetch` and parse the SSE byte stream incrementally. UTF-8 characters and SSE lines can be split across network chunks.

## Current behavior (implemented)

- `200 text/event-stream` emits **unnamed** SSE `data:` events containing raw assistant text chunks. There is no JSON envelope, event ID, start event, or terminal completion event. HTTP error responses before streaming are not consistently formatted.
- The backend saves the user message before the provider call. On provider completion it saves the concatenated assistant text and completes the emitter. The emitter has a 60-second timeout.
- Some provider errors can appear as text, including `[ERROR: RATE_LIMIT_EXCEEDED]`. The current code may persist such text as assistant content. `completeWithError` can also close the stream without a terminal event. A disconnect does not reliably cancel upstream work. These are known defects for Step 10, not client guarantees.
- The current frontend's fallback stream URL omits `/api/v1`; this helper is not a validated stream integration. Phase F will switch callers after the new protocol is accepted.

## Proposed opt-in protocol (pending Step 10)

The existing mode remains the default for old clients. A new client sends `X-Chat-Stream-Version: 2` and `Idempotency-Key: <UUID>`; the server must reject an unsupported version before saving a message. One idempotency key identifies one logical turn for the authenticated user. Repeating it with a different body returns `409 IDEMPOTENCY_CONFLICT`; repeating it with the same body returns the existing state without generating or charging twice. A key is retained for at least 24 hours; Step 10 will specify the exact retention and cleanup policy.

The server returns `Content-Type: text/event-stream; charset=UTF-8`, `Cache-Control: no-store`, and `X-Request-Id`. All events are UTF-8 JSON in one `data:` field, with an `event:` name. The server sends these in order:

1. `start`: `{ "requestId": "UUID", "sessionId": "UUID", "userMessageId": "UUID" }` after an owned active session and a durable request record are established.
2. Zero or more `delta`: `{ "requestId": "UUID", "text": "..." }`. Preserve order. Text may be any Unicode substring; consumers append parsed `text`, not transport bytes.
3. Exactly one terminal event when the connection remains writable:
   - `complete`: `{ "requestId": "UUID", "assistantMessageId": "UUID", "status": "completed", "usage": { "inputTokens": 12, "outputTokens": 34 } }`, sent only after successful assistant persistence and usage settlement.
   - `error`: `{ "requestId": "UUID", "code": "PROVIDER_UNAVAILABLE", "message": "Response is temporarily unavailable.", "retryable": true, "partialPersisted": false, "usage": { "inputTokens": 12, "outputTokens": 0 } }`, sent after a failed/canceled generation is durably recorded and usage is settled or marked for reconciliation. Never send a provider diagnostic or user content in the message.

`usage` numbers are server-observed values; when the provider does not return usage, the server records an explicit unknown/estimated state rather than inventing exact counts. The final JSON shape for that state is pending Step 08. `start`, `delta`, and terminal events must carry the same request ID. The client treats EOF without a terminal event as **unknown**, then calls the proposed `GET /api/v1/chat/requests/{requestId}` to reconcile. It must not assume success or retry with a new key.

SSE `id:` is deliberately omitted in v2. This protocol does not promise resumable byte replay or `Last-Event-ID` support. Reconnection uses the request status endpoint, and a new generation requires a new idempotency key. A `409` means the same key was used with different input; a `404` on the status endpoint conceals another user's request.

## Persistence and cancellation rules for Step 10

- Validate bearer token, user ownership, active session, body length, idempotency key, and budget **before** starting a billable provider call. Define a bounded first-token deadline and total deadline in configuration.
- Never hold a database transaction open while waiting for the provider. Persist the request and user turn once, then persist a completed or explicitly incomplete assistant turn and settle usage.
- On provider error, timeout, client disconnect, or session end during generation, cancel upstream work when possible. Record `failed` or `canceled` state; do not store an error marker as an assistant answer. Charge only actual provider attempts under Step 08's accounting rules.
- A client disconnect can make the terminal event impossible to deliver. The persisted request state is authoritative. A late provider callback must not turn a canceled request into a completed answer.
- `429` before stream start is an HTTP error with `Retry-After` seconds and a UTC reset timestamp. After `start`, quota/provider limits use a terminal `error` event with a stable code. Provider unavailable maps to `503` before start or `PROVIDER_UNAVAILABLE` after start. Exact headers and error envelope are pending implementation and tests.

The [synthetic v2 event fixture](fixtures/stream-v2.sse) illustrates framing. It is a proposed example, **not** current endpoint output. Do not enable v2 in the frontend until Step 10 and Step 13 acceptance tests pass.
