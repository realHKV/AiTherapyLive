# Step 07 — Provider-neutral generation and routing boundary

Status: **complete for boundary design and tests**. The new route is opt-in and makes no live provider calls. The current `AIGateway` and chat/summary callers remain on the legacy path until accounting and Bedrock integration are ready.

## Decision

- `GenerationCommand` carries user ID, purpose, messages, and output limit. A client-selected model is rejected. `GenerationRouter` resolves effective FREE/PRO tier through `EntitlementService`, then chooses the approved model for GREETING, CHAT, SUMMARY, or PROFILE_REFRESH.
- `GenerationPolicy` requires a policy ID and FREE, PRO, and summary model IDs whenever `AI_ROUTING_ENABLED=true`. The same ID may be configured for multiple purposes after evaluation. No candidate ID is embedded as a production default.
- `GenerationGateway` accepts an immutable resolved request and cancellation token. Results and stream events carry policy/model identity, terminal state, sanitized failure categories, and nullable token usage. Unknown provider usage remains unknown so Step 08 can reserve and reconcile it honestly.
- `DeterministicFakeGateway` produces synthetic results for tests and development. Its Spring bean exists only under `dev` or `test` profile. Enabling routing in a production profile before the Step 09 adapter exists fails application startup instead of invoking the legacy external provider.

## Configuration

The new application properties are `ai.routing.enabled` (`AI_ROUTING_ENABLED`, default `false`), `policy-id` (`AI_ROUTING_POLICY_ID`), `free-model-id` (`BEDROCK_FREE_MODEL_ID`), `pro-model-id` (`BEDROCK_PRO_MODEL_ID`), and `summary-model-id` (`BEDROCK_SUMMARY_MODEL_ID`). Model IDs remain empty until selected and validated with an AWS account in Step 09. Setting these values now does not switch the live chat path.

## Validation and next steps

Routing tests cover all four purposes, active PRO, FREE, expired PRO, rejected client model selection, incomplete configuration, deterministic fake output, and cancellation. Local `bash ./mvnw -q test` reported 58 tests, zero failures/errors, and four container checks skipped because Docker is unavailable. GitHub Actions runs those container checks.

Step 08 adds budget reservations and usage settlement before any live traffic uses this route. Step 09 supplies the Bedrock adapter and validates account/region/model access. Step 10 moves chat to the new request lifecycle; Step 11 moves durable summary jobs. The legacy provider is removed from the target deployment in Step 12.

No AWS credentials or account details are required for Step 07.
