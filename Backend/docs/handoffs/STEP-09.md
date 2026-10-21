# Step 09 — Bedrock gateway and model evaluation

Status: **gateway implementation complete; live AWS candidate evaluation pending account inputs**.

## Implemented

- Added AWS SDK for Java v2 `bedrockruntime` and async Netty client dependencies, managed by the AWS SDK BOM (2.55.13).
- Added opt-in `BedrockClientConfig` and `BedrockGateway` for sync Converse and ConverseStream. It accepts only the approved model ID on `ResolvedGeneration`; the caller cannot supply provider SDK types or choose an effective model through the public command.
- The adapter maps system/user/assistant text messages, token limits, output text, finish reason, usage, and streaming deltas. It uses AWS's default credential chain, bounded timeouts, explicit cancellation, no hidden SDK retries, and sanitized failures for throttling, timeouts, access denial, missing model, invalid input, and transport/service outages.
- Bedrock remains disabled by default. The current legacy chat calls do not use this gateway; Step 10 must wire budget reservation and generation lifecycle before production traffic.
- Added synthetic conversation, summary, and memory-injection evaluation prompts plus a report template in `docs/BEDROCK_EVALUATION.md`.

## Configuration

`AI_BEDROCK_ENABLED` defaults to `false`. Enabling the adapter requires `BEDROCK_REGION`; timeouts use `BEDROCK_CONNECT_TIMEOUT`, `BEDROCK_API_CALL_TIMEOUT`, and `BEDROCK_API_CALL_ATTEMPT_TIMEOUT`. Routing remains separately controlled by `AI_ROUTING_ENABLED` and its policy/model IDs. No model ID or AWS credential is committed.

## Validation and outstanding AWS work

The AWS SDK fixture tests cover synchronous result/usage identity, sync and streaming provider errors, token deltas, partial output, terminal events, deadlines, and cancellation. `bash ./mvnw -q test` passes: 72 tests discovered, 66 passed and 6 PostgreSQL/Redis integration tests skipped because Docker is unavailable in this environment. No live AWS call has been made. Real access, selected source region, model/profile IDs, cross-region permission, pricing, and quality/latency measurements must be supplied and recorded before Step 09's external validation gate can close.

For a real run, use local AWS IAM Identity Center SSO or an ECS task role. Do not send or commit access keys. The small required permissions and remaining choices are listed in [BEDROCK_EVALUATION](../BEDROCK_EVALUATION.md).
