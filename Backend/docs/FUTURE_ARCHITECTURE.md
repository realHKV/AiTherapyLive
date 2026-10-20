# Future backend architecture and implementation roadmap

Status: planning only; researched 2026-10-19. Nothing here changes the running application. The [backend reference](BACKEND_REFERENCE.md) describes implemented behavior; this document describes the requested direction and recommendations. Recheck provider catalogs, pricing, quotas, regions, SDK support, and Spring support status at implementation time.

## 1. Decision summary

| Area | Direction | Status / reasoning |
| --- | --- | --- |
| Delivery | GitHub Actions → Docker Hub → ECS Fargate by digest | User-requested direction; detailed in [AWS migration plan](AWS_MIGRATION.md) |
| Production registry | Optional ECR mirror of the tested Docker Hub artifact | Recommendation to reduce runtime dependency on external registry pulls; not mandatory |
| Compute | ECS on Fargate; one backend service initially | Corrected user preference: AWS manages servers; task sizing, budget, and region still needed |
| FREE conversations | Bedrock managed inference; evaluate Nova Lite | User-approved Bedrock-only direction; application subsidizes a capped allowance |
| PRO conversations | Bedrock managed inference; evaluate Nova 2 Lite against Claude Haiku 5.5 | Model selection follows conversation evaluation and cost measurement |
| Active memory | Encrypted PostgreSQL tables; four context layers and user-controlled lifecycle | Confirmed storage choice; [MEMORY_DESIGN](MEMORY_DESIGN.md) defines behavior |
| Extraction jobs | PostgreSQL-backed durable jobs in the first release | Queue/worker infrastructure deferred until justified |
| Voice | PRO-only later milestone, separate bidirectional adapter | Text/SSE architecture should not be mistaken for a voice transport |
| Identity | Harden existing auth for first migration; evaluate Cognito User Pool + Google federation next | Recommendation; do not build a second custom identity service merely to host login |
| Authorization | Backend owns UUID mapping, ownership, subscription entitlement, and usage policy | Authentication provider does not decide who owns a conversation or whether a payment grants PRO |
| Framework | Upgrade 3.4.4 through a tested 3.5 checkpoint toward a maintained 4.x release | Separate changes from cloud/provider migration; retain Java 21 initially |

The user has requested planning for these changes, not infrastructure creation, image publication, dependency edits, or production deployment. Recommendations and unresolved selections are not approved production configuration.

## 2. Bedrock-only model selection

Both FREE and PRO use Amazon Bedrock. FREE means free to the customer, not free inference: the application pays for every billable invocation, including greetings, summaries, retries, and profile refresh. External free-model providers and cross-provider fallback are outside the target plan.

| Purpose | Initial evaluation candidate | Selection criteria |
| --- | --- | --- |
| FREE conversations | Amazon Nova Lite | Conversation quality, latency, and cost within the subsidized allowance |
| PRO conversations | Amazon Nova 2 Lite, compared with Claude Haiku 5.5 | Empathy, consistency, supported languages, latency, and measured cost |
| Summaries and memory extraction for both tiers | Amazon Nova Micro | Faithful extraction, validated JSON, and no invented personal details |
| PRO voice later | Amazon Nova 2 Sonic | Speech quality, language support, interruption handling, and audio cost |

These are candidates, not tested winners or committed deployment IDs. A single evaluated conversation model may serve both tiers initially, with differences in allowances and later voice access. Keep model choice separate from product entitlement.

Model references: [Nova Lite](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-lite.html), [Nova Micro](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-micro.html), [Nova 2 Lite](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-2-lite.html), [Claude catalog](https://docs.aws.amazon.com/bedrock/latest/userguide/model-cards-anthropic.html), [Bedrock pricing](https://aws.amazon.com/bedrock/pricing/). Recheck availability, model lifecycle, API support, regional pricing, and account quotas before implementation.

Enforce separate FREE subsidy and PRO usage budgets plus a shared account-wide budget. Bedrock quotas may be shared across tiers for the same model/region; reserve capacity for summaries and prevent FREE traffic from exhausting paid capacity. When a budget or capacity limit is reached, return a clear retry/reset response rather than switching to another provider or exceeding the budget.

## 3. Bedrock integration for both tiers

Use Bedrock-managed inference. There is no need to operate model-serving servers to call a supported Bedrock model. Custom/imported models and provisioned throughput are different projects and are outside the initial plan.

For PRO chat, start evaluation with Amazon Nova 2 Lite (`amazon.nova-2-lite-v1:0`), a documented cost-oriented Bedrock model. Compare another suitable Bedrock text model only if measured conversation quality requires it. Record exact model/inference-profile identifier, region, API support, model lifecycle, price, and account quota in the decision record before selection. Regional or cross-region inference profile requirements vary; do not copy an ID into production without a successful invocation. [Nova 2 Lite model card](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-2-lite.html).

Prefer AWS SDK for Java v2 `Converse`/`ConverseStream` for text with a compatible model, using the ECS task role and scoped Bedrock permissions. A separate `BedrockGateway` should handle AWS credentials, messages/system prompts, stream events, usage, throttling, and cancellation. It is not implemented by changing `AI_PRO_BASE_URL` to a Bedrock endpoint. Match the selected API to model capability. [Bedrock API compatibility](https://docs.aws.amazon.com/bedrock/latest/userguide/models-api-compatibility.html).

Proposed internal boundaries (names are suggestions, not existing classes):

```text
HTTP auth → internal user UUID → EntitlementService
         → UsagePolicyService.reserve(operation, user, tier)
         → AiRouter.select(tier, purpose, policyVersion)
             → BedrockGateway (FREE and PRO; model selected by tier/purpose)
         → normalized output/events + usage settlement + persistence
```

- Replace the growing `boolean isPro` gateway API with explicit request context: internal user/conversation IDs, purpose (`GREETING`, `CHAT`, `SUMMARY`, `PROFILE_REFRESH`), output budget, and provider policy version. Resolve entitlement on the server; ignore client-supplied tier/model names.
- Normalize provider usage, finish reason, provider request ID, text deltas, errors, and terminal state. Preserve the current frontend contract initially, or version stream events and update the frontend together. Never store a provider error string as a successful assistant reply.
- Route greetings, chat, summaries, and profile extraction for both tiers through Bedrock. Persist the model/routing policy and budget reservation with jobs; on downgrade, decide whether to honor already reserved work or stop it. A cheaper approved Bedrock extraction model may serve both tiers without changing conversation entitlement.
- Implement and validate Bedrock routing before retiring the current external-provider adapter, API-key secrets, and configuration. Keep the internal gateway abstraction. The current-state reference remains accurate until implementation lands; the future deployment must not require an external AI key or silently fall back to the old endpoint.
- Keep prompt/context construction provider-neutral and token-bounded. Update extraction validation and fixtures for each model; never trust model-generated JSON to satisfy database constraints without validation.
- Retry only transient failures within a bounded budget; do not replay after visible output without defined duplicate/partial semantics. Reserve capacity for retries and summaries.
- Restrict allowed model/profile ARNs and region scope. Review cross-region routing explicitly. Keep invocation-content logging disabled unless deliberately enabled with retention/access controls. Verify selected-model data retention rather than claiming blanket zero retention for all Bedrock models. [Bedrock data retention](https://docs.aws.amazon.com/bedrock/latest/userguide/data-retention.html).

Model promotion requires a small versioned evaluation set: empathetic response quality, transparency as AI, handling crisis language, avoiding invented personal memories, multilingual behavior for supported languages, prompt-injection resistance in memories, valid extraction JSON, output length, latency, and cost. Use synthetic examples and human review. Generic model benchmarks alone do not establish fitness for this product.

### Memory and retrieval direction

Follow [MEMORY_DESIGN](MEMORY_DESIGN.md): confirmed profile, recent conversation, relevant long-term memories, and valid session summaries are distinct context layers. User-confirmed facts are authoritative; AI extraction creates reviewable candidates with source evidence. Retrieve by relevance and lifecycle within a token budget rather than always taking the five highest-importance rows. Forget/reset must suppress source reuse and invalidate derived context. Keep active content in encrypted PostgreSQL fields; S3 archives/exports and embeddings are later options, not prerequisites.

## 4. Proposed rate limits and cost controls

These are conservative beta starting values, **not promised quotas or validated profitability**. The effective allowance is the minimum of user, provider, concurrency, token, and spend budgets. Tune after measuring usage; all limits must be configuration-backed and enforced across replicas.

| Control | FREE beta proposal | PRO text beta proposal |
| --- | --- | --- |
| User chat submissions | 5/minute | 10/minute |
| Daily user chat allowance | 20/day | 60/day |
| Period allowance | 300/rolling 30 days | 1,000/current paid entitlement period |
| Active generations | 1/user, 1/conversation | 1/user, 1/conversation |
| User-message size | 4,000 characters, plus token budget validation | 8,000 characters, plus token budget validation |
| Maximum assembled input | Initial target 2,000 tokens | Initial target 4,000 tokens |
| Maximum generated tokens | 400 | 800 |
| AI-generated greeting | At most 3/day; static greeting fallback | At most 10/day; static greeting fallback |
| Mid-session profile refresh | At most 1/15 minutes and 3/day | At most 1/5 minutes and 10/day |
| Automatic summaries | At most one successful final summary per completed session | Same; durable retries and reserved capacity |
| Voice | Disabled | Disabled until separate voice launch |

Daily windows use UTC; return reset timestamps so the UI can localize them. Define the rolling-window implementation precisely. A renewal must not erase usage already billed to the same period by accident. An active conversation should not trigger paid greetings on every page refresh; reuse session state or return a static greeting when the greeting budget is exhausted.

Implementation rules:

1. Check ownership and effective subscription before selecting a model. Reserve a user quota slot, provider tokens, and an in-flight lease atomically where required. Rate limits must cover session start and extraction, not only the messages endpoint.
2. Use atomic Redis operations/scripts for short-window counters and expiring concurrency leases. Renew a lease while a stream is active and release on completion/cancellation; avoid expired leases permitting overlapping generations. If a clustered Redis topology is chosen, design compatible key slots or a reservation workflow rather than assuming a multi-key Lua script works across slots.
3. Keep durable usage/reservations in PostgreSQL with unique request/idempotency keys so task restarts, retries, and Redis eviction do not erase paid consumption. Store metadata/token counts/cost estimates, not prompt text, in the usage ledger. Reconcile abandoned reservations.
4. Settle reservations using actual provider usage where reported. Charge only one user message for an idempotent retry. Pre-provider rejection does not consume user allowance; provider attempts can still incur cost on cancellation/error, so retain measured or conservatively estimated provider cost and document the user-refund policy.
5. Enforce the applicable Bedrock model/region/account request and token quotas plus hard application budgets independently of per-user limits. Use service quotas, throttling signals, and measured usage, leaving headroom for background work. For both FREE and PRO traffic, reserve estimated input plus worst-case output cost before invocation and settle afterward. Do not rely on a delayed billing alarm as the spending gate.
6. On user limit exhaustion return HTTP 429 with machine-readable code, `Retry-After`/reset time where applicable, and remaining allowance. Use a distinct capacity-unavailable response for provider/global outages. After SSE headers are sent, emit a defined terminal error event and close; HTTP status can no longer be changed.
7. Keep login/register/refresh abuse limits separate from chat quotas (IP plus identity-aware throttling with trusted proxy handling). If Redis/usage accounting is unavailable, reject new billable generation with a clear retryable response rather than bypassing the budget.

The current ₹50/30-day PRO payment amount is hardcoded. Do not assume it funds 1,000 Bedrock requests plus Fargate, DB, Redis, payment fees, and voice. Calculate before launch:

```text
period AI cost = Σ(input_tokens × input_rate + output_tokens × output_rate)
                + summaries + retries + future voice cost
contribution = net subscription receipts − AI cost − allocated platform cost
```

Use consistent currencies and per-token units; include cache/reasoning/audio charges where applicable. Set a per-user AI spend ceiling and a global daily/monthly ceiling from the selected model's current pricing. Reduce quotas, adjust subscription pricing, or defer voice if the budget fails. No concrete monetary ceiling is approved until those inputs are available.

## 5. Login service: recommendation and migration boundary

Authentication answers who the user is; application authorization controls access to conversations, memories, and paid features. Existing Google OAuth login is an authentication integration, not a complete managed account system.

Keep and harden existing login during the ECS Fargate migration. If managed password reset, verification, MFA, and social identity management are desired, prefer **Cognito User Pools with Google federation** over a new custom Spring authorization-server microservice. Cognito can bridge Google login into one token issuer. A Cognito Identity Pool is not needed just to authenticate calls to this backend. [Cognito federation](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-identity-federation.html).

Alternatives: retain current Spring auth if simplicity and control outweigh account-management work; consider Keycloak if operating an identity platform and provider portability are explicit requirements. Do not add a custom auth service just to create a separate deployment. Microservice separation does not automatically make login safer.

Proposed Cognito migration contract:

- Browser uses authorization code + PKCE against the selected Cognito app client; Google is federated through it. Keep AWS credentials in the backend. Select browser token storage/BFF-cookie design deliberately; cookies require CSRF handling and should not inherit today's blanket CSRF-disabled policy blindly.
- Backend becomes an OAuth2 resource server. Validate signature, allowed issuer, expiry, token type, intended client/audience semantics, and scopes. Cognito access and ID tokens have different claim rules; accept access tokens for APIs and validate `token_use=access` and allowed `client_id` (plus audience when configured). Do not merely verify a signature.
- Add `external_identities(issuer, subject, user_id)` with unique `(issuer,subject)` through Flyway. Keep existing internal UUIDs and all related data. A Cognito subject is not the existing application UUID.
- Never link accounts solely by an untrusted matching email. Define proof-of-ownership and verified-provider linking, especially when a local and Google account share an address.
- Choose a rehearsed password migration: sign-in migration trigger, compatible hash import if the current feature/format is supported, or forced reset. Verify the existing BCrypt hash parameters against the actual selected mechanism; do not assume a DB import automatically preserves passwords. [Cognito migration trigger](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-import-using-lambda.html), [current import capabilities](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-using-import-tool.html).
- If temporarily accepting legacy and Cognito tokens, use an explicit issuer/validator allowlist, translate both to one internal principal, and set a sunset date. Never fall back from failed Cognito validation to permissive legacy decoding. The legacy tokens currently have no issuer claim; isolate their verification path and key explicitly.
- Decide refresh/logout/revocation and account deletion end-to-end. Offline JWT validation alone does not guarantee immediate invalidation after logout or user deletion. Keep entitlement in the database, not long-lived token claims. Disable access locally first when deleting an account; coordinate external identity deletion with retries.

Exit tests: existing user retains the same UUID/profile/subscription after migration; local and Google login work; expired/wrong-client/wrong-issuer/ID tokens fail; account linking cannot take over another user; password migration/reset, logout, deletion, and rollback are rehearsed. This is a separate release after infrastructure stabilization.

## 6. Spring Boot and dependency modernization

Current source uses Boot **3.4.4**, Java **21**, and explicitly pinned JJWT **0.12.5**. The reviewed Spring documentation lists maintained 4.0/4.1 lines and displays 4.1.1 documentation. Treat exact patch numbers as a research snapshot; choose a currently maintained compatible patch when implementing. [Spring release/support index](https://github.com/spring-projects/spring-boot/wiki), [Boot upgrading documentation](https://docs.spring.io/spring-boot/upgrading.html).

Upgrade sequence:

1. Establish passing unit, real PostgreSQL/Redis, security, encryption, and JSON/SSE contract tests on the existing code. Record JDK, wrapper, dependency tree, and container versions.
2. Upgrade to the latest available 3.5.x as a migration checkpoint and remove deprecated API usage. Do not treat this checkpoint as a long-term supported destination without checking its support status.
3. Upgrade through 4.0 using the official guide, then the selected supported 4.x minor/patch with its release notes. Keep checkpoints separately reviewable. Boot 4 changes include modularized starters/test support and a Jackson 3 default, so review imports and third-party integrations rather than only editing the parent version. [Official Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
4. Audit JJWT and its Jackson adapter separately: it is explicitly versioned and can have different Jackson expectations from Boot. Verify current supported releases and changelogs; do not blindly align every `jackson-*` artifact to one major. Audit AWS SDK v2 modules/BOM when adding Bedrock and bidirectional voice support.
5. Let the Boot BOM manage Spring/Hibernate/Jackson/Flyway/driver versions unless a documented compatibility/security reason requires an override. Retain Java 21 initially; a separate JDK upgrade is optional. Update Maven wrapper and container base images in bounded changes, with image scanning and reproducible digest references.
6. Verify encrypted data round trips (including existing ciphertext fixtures), Flyway checksums/DDL validation, OAuth/JWT filters, DTO boolean/date JSON names, SSE async dispatch, scheduler behavior, and provider JSON serialization. Refresh the source reference only once changes land.

Do not fix the locally edited `JacksonConfig.java` by replacing it blindly. Inspect its actual state and preserve unrelated work. Enable automated dependency PRs after establishing a green baseline; group compatible patches and review major upgrades separately. Avoid adopting Spring AI simultaneously with the Boot/Bedrock changes unless it provides a concrete benefit over the existing gateway and its compatibility has been checked.

## 7. Voice later: design now, implement separately

Keep text chat as the first paid release. For voice, evaluate Amazon Nova 2 Sonic (`amazon.nova-2-sonic-v1:0`) and its region/language/SDK availability. Its invocation uses a bidirectional stream rather than text `ConverseStream`. [Bedrock API compatibility](https://docs.aws.amazon.com/bedrock/latest/userguide/models-api-compatibility.html).

Proposed transport: browser microphone → authenticated WebSocket to backend voice-session handler → Bedrock bidirectional SDK stream → audio/transcript events back to the browser. Implement a separate `VoiceGateway` and session lifecycle; keep text SSE intact. Browser WebSocket authentication needs a deliberate mechanism (for example a short-lived one-time session ticket issued over authenticated HTTPS), origin validation, and no long-lived JWT in a logged URL.

Plan audio formats/sample rates, turn detection, interruption/barge-in, backpressure, idle/disconnect cancellation, provider session-duration rollover, deployment draining, and transcript persistence. Store final transcript turns through the normal conversation model with deduplication; do not retain raw audio by default. Make microphone use and retention behavior clear to users. Recheck entitlement and session budget during the connection, not only at socket open.

A voice beta could start with one connection/user, ten-minute maximum sessions, and thirty minutes per paid period, but these are evaluation targets only. Set monetary/token limits after measuring the selected model's actual audio and text charging. Do not include unlimited voice in the existing ₹50 plan. A speech-to-text → text model → text-to-speech pipeline is an alternative with separate latency and billing tradeoffs, not an automatic equivalent to a conversational speech model.

## 8. Builder milestones and definition of done

The table below groups architecture outcomes. [DEVELOPMENT_PLAN](DEVELOPMENT_PLAN.md) defines the actual numbered implementation order and completion tracker: backend first, then frontend integration, then coordinated production release. Optional Cognito and voice do not block that first release.

| Milestone | Bounded deliverable | Required evidence |
| --- | --- | --- |
| M0 | Existing production gaps + baseline integration tests | Dev routes disabled, auth matcher fixed, payment binding/currency/replay fixed, no token logs, encrypted fixture restore |
| M1 | Framework/dependency upgrade in checkpoints | Maintained version selected, dependency inventory, contracts/security/SSE/schema tests green |
| M2 | Entitlement and usage policy + provider-neutral gateway | Atomic concurrent quotas, idempotency/reservation settlement, server-side routing, bounded prompts/errors |
| M3 | Bedrock model evaluation and adapter for both tiers | Recorded model/region/policy decisions, synthetic evaluations, subsidy/spend and cancellation tests; every AI operation uses Bedrock with no external-provider fallback |
| M4 | PostgreSQL extraction jobs, memory lifecycle, and operations | Commit/restart/retry, confirmation/retrieval/forget-reset tests, probes/metrics, graceful shutdown, bounded workers |
| M5 | Docker Hub CI and ECS Fargate staging | Tested digest deployment, least-privilege OIDC/task roles, secret injection, one-off migration task, fresh-task pull and SSE drain tests |
| M6 | Frontend integration and production migration | Backend handoff, browser acceptance, restore/cutover/rollback rehearsal, and AWS_MIGRATION acceptance matrix |
| M7 | Optional Cognito migration | Identity mapping, existing-user preservation, token validation/linking/logout/deletion tests |
| M8 | Paid voice beta | Supported region/languages, audio evaluation, connection/cost caps, cancellation/draining and transcript integrity |

M2–M4 should land in separate changes even if developed before ECS deployment. No need to wait for voice or Cognito to release paid text. Docker build/test work can start early, but keep infrastructure deployment and application behavior changes independently reversible.

Each task must name: current source files, desired behavior, new config names/defaults, schema/contract impact, acceptance tests, rollback, and unresolved deployment inputs. Proposed new names such as `BEDROCK_FREE_MODEL_ID`, `BEDROCK_PRO_MODEL_ID`, `BEDROCK_SUMMARY_MODEL_ID`, `BEDROCK_REGION`, and `USAGE_*` are design placeholders until implementation wires and documents them; setting them today does nothing.

Before production choices, record: AWS region/budget, Fargate task sizing/scaling, Docker Hub visibility/ECR mirror choice, anticipated active users, expected turns per user, retained ₹50 pricing or new pricing, supported conversation languages, and provider data policy. These questions do not block local code/document preparation, but they do block claiming a validated production capacity or cost plan.
