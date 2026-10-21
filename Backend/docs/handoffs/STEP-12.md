# Step 12 handoff — Bedrock-only runtime and operations

Status: **complete**. Implementation commit: `93dd717`; cancellation-test stabilization: `5369501` on `codex/backend-baseline`.

## Shipped

- Removed the OpenAI-compatible/Groq adapter and all runtime API-key/base-URL settings. `DisabledLegacyAIGateway` is network-free and returns a sanitized 503 if an old internal service path is invoked. Unversioned session/message request shapes route through the accounted v2 workflow with a server-generated idempotency key when absent. Unversioned SSE returns 503 until the frontend migrates to v2 named events in Step 17.
- The `production` profile rejects missing app secrets, placeholder values, non-HTTPS CORS, local or non-verified-SSL PostgreSQL, Redis without TLS, disabled Bedrock/routing/v2/extraction, region mismatch, and dev mode. AWS credentials remain on the AWS SDK default provider chain; ECS should use a least-privilege task role.
- Added Actuator probes. Readiness checks PostgreSQL and Redis, liveness checks process state only, health details stay hidden, and metrics endpoints require authentication. Bedrock is not a health dependency.
- Added JSON console logs and bounded correlation IDs, propagated through the chat worker executor. Logs and metric labels do not contain prompts, message text, account identifiers, or credentials.
- Added low-cardinality stream, extraction-job, and settled-token metrics. Dollar spend is deliberately not estimated until model, region, and pricing choices are verified.
- Chat execution is bounded and drains on shutdown; active provider streams are cancelled through the regular durable settlement path. Extraction claims and usage reconciliation are safe across replicas. The midnight UTC subscription downgrade uses a PostgreSQL advisory lock.

## Verification

- GitHub Actions [run 39577386758](https://github.com/realHKV/AiTherapyLive/actions/runs/39577386758): 97 tests passed, zero failures/errors/skips. This included PostgreSQL/Redis lifecycle, actuator, and scheduled-lock tests.
- Local clean Maven suite: 97 tests discovered, 76 passed, and 21 PostgreSQL/Redis container tests skipped because Docker is unavailable locally. Mockito required the local Byte Buddy agent option.
- Focused Bedrock cancellation tests pass after replacing a race-prone mock verification with deterministic synchronization.
- No live AWS request was made, and no AWS resources or credentials are required for this step.

## AWS items before live Bedrock testing

The user selected Mumbai (`ap-south-1`) but has not configured AWS yet. Before the later AWS evaluation, confirm model availability/access and approved model IDs for greeting/chat/summary, enable the needed Bedrock permissions on a task role (no static keys), and set the selected pricing/account limits. Production deployment will also need RDS/Redis endpoints and secrets, TLS, and existing app secrets. Keep Bedrock disabled until these are verified. The frontend must migrate its streaming caller to the v2 named-event contract before streaming is enabled.
