# Step 11 handoff — durable extraction

Status: **complete**. Commit: `2402a2d` on `codex/backend-baseline`.

## Shipped

- Flyway V9 adds durable extraction jobs and encrypted profile candidates with ownership, source, policy, revision, state, attempt, replay, and idempotency metadata.
- Session completion inserts the deduplicated extraction job in the same transaction. PostgreSQL claims use `FOR UPDATE SKIP LOCKED`, leases recover abandoned work, and retry/backoff/exhaustion plus explicit replay are supported.
- The extraction worker uses the shared generation router and usage accounting boundary. It bounds the transcript to 200 recent messages and 24,000 characters, validates structured output, and requires evidence tied to a user message.
- Summaries and pending profile/memory candidates persist atomically with successful job state after account, conversation, eligibility, and context-revision checks. Confirmed profile data is not overwritten.
- The worker defaults off until AI routing and provider configuration are intentionally enabled. No AWS service setup or live model request was needed for this step.

## Verification

- GitHub Actions [run 39574509660](https://github.com/realHKV/AiTherapyLive/actions/runs/39574509660): 85 tests passed, zero failures/errors/skips, with PostgreSQL and Redis containers.
- Local clean Maven suite: zero failures/errors; 17 container-backed tests skipped because Docker was unavailable. Mockito on the local JDK required `-DargLine=-javaagent:/home/hkv/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.11/byte-buddy-agent-1.18.11.jar`.

## Follow-up

- Step 12 should add the runtime/operations controls and decide how operators invoke replay in deployment.
- Step 13 should exercise the migration and extraction job lifecycle through the final HTTP/runtime contract.
- User-owned local edits to `.github/workflows/keep-alive.yml`, `Backend/.gitkeep`, and `Frontend/.gitkeep` were left untouched and uncommitted.
