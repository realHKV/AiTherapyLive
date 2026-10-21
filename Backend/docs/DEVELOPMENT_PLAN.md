# Backend development plan for AI agents, followed by frontend integration

Status: **implementation begun; see tracker below**. Created 2026-10-19. This is the execution order for the first Bedrock text release. It does not authorize publishing images, provisioning resources, moving production data, or switching live traffic by itself. Follow the authorization already provided in the implementation session.

## 1. Release scope and fixed decisions

- Deploy the application using **Docker Hub → ECS on Fargate**. AWS manages the underlying servers. An ECR mirror is optional.
- Use **Bedrock for both FREE and PRO**, including greetings, chat, summaries, and profile extraction. FREE inference is subsidized and budget-limited. No external AI fallback.
- Keep Java 21 initially and upgrade Spring Boot/dependencies in tested checkpoints.
- Retain and harden current local/Google login for this release. Cognito is a separate later decision.
- Deliver text chat first. Voice remains disabled and is a separate later release.
- Keep PostgreSQL, Redis, existing user UUIDs, encryption compatibility, and Razorpay. New schemas use additive Flyway migrations.
- Store active memories and summaries in encrypted PostgreSQL fields. Follow MEMORY_DESIGN for confirmation, relevance, provenance, and forgetting; no active-memory S3 files or vector search in the first release. Use a PostgreSQL-backed extraction job table initially.
- Build and validate the backend first. **Implement frontend changes only in Phase F**, after the backend handoff is complete. Read frontend callers earlier to understand compatibility; test backend flows through HTTP clients and automated fixtures until then.

Read [BACKEND_REFERENCE](BACKEND_REFERENCE.md) for current implementation, [FUTURE_ARCHITECTURE](FUTURE_ARCHITECTURE.md) for design/quotas/model candidates, [AWS_MIGRATION](AWS_MIGRATION.md) for infrastructure/cutover details, and [AI_CHANGE_GUIDE](AI_CHANGE_GUIDE.md) for working conventions. This plan owns execution order; earlier milestone lists are architectural groupings, not a reason to deploy before frontend acceptance.

Read [MEMORY_DESIGN](MEMORY_DESIGN.md) for the canonical target memory behavior and [SCHEMA_REVIEW](SCHEMA_REVIEW.md) before schema or contract work. Its findings map to the numbered steps in section 5 of that review. In particular, resolve onboarding field names/types and persona/completion semantics in Steps 05–06, purchase ownership in Step 03, and shared request/usage/job identities in Steps 08–11. Do not invent parallel tables for the same logical operation. The schema review is not an applied migration or a completed tracker step.

## 2. How agents execute this plan

Execute one numbered step at a time, in order. A step may need several small changes; do not combine framework upgrades, payment fixes, and deployment work in one patch. “Agent” means the assistant assigned that step; it does not require concurrent agents or multiple services.

Before each step, inspect the current working tree and its predecessor's evidence. Read the relevant source instead of assuming the docs are still current. Preserve unrelated local changes. All Java paths below are relative to `Backend/src/main/java/com/hkv/AiTherapy/`; frontend paths are relative to the repository root.

For every completed step, record in its tracker row: status, commit/revision or patch identifier, and a link to a handoff report. Create `docs/handoffs/STEP-NN.md` when that step is actually implemented. A report must contain:

1. Outcome and files changed, including migrations and configuration names/defaults.
2. Tests/commands actually run and results; separately list checks not run and why.
3. API/data compatibility impact, rollout needs, and rollback limits.
4. Outstanding decisions or failures and the next step that can proceed.

Use `pending`, `in progress`, `blocked`, or `complete`. A step with an unmet exit gate stays incomplete. A mock passing test does not count as a real AWS invocation, payment verification, restore, or browser test. Do not include credentials or user content in reports.

An unavailable AWS account must not block local adapter tests or infrastructure preparation. Complete independent work, report the specific external validation still needed, and leave the external gate pending. Never fabricate account quotas, prices, model access, or a successful deployment.

## 3. Execution tracker

| Step | Deliverable | Status | Evidence |
| --- | --- | --- | --- |
| 00 | Baseline and reproducible local environment | In progress | Rewritten commit `7c0cc58`; [Step 00 handoff](handoffs/STEP-00.md); container gate pending |
| 01 | Production authorization and secret/log fixes | Complete | Rewritten commit `3ed4ff5`; [Step 01 handoff](handoffs/STEP-01.md) |
| 02 | Authentication lifecycle and account deletion | In progress | [Step 02 handoff](handoffs/STEP-02.md); Docker and IdP fixture gates pending |
| 03 | Payment verification and entitlement correctness | In progress | PostgreSQL migration, duplicate-callback, and cross-purchase replay gates passed in Step 13 CI; [Step 03 handoff](handoffs/STEP-03.md); Razorpay test-mode validation remains a Step 16 gate |
| 04 | Spring Boot and dependency upgrade checkpoints | In progress | 3.5.16 `2218e61`, 4.0.8 `6716cea`, 4.1.1 `1091d87`; [Step 04 handoff](handoffs/STEP-04.md); database/schema and scan gates pending |
| 05 | HTTP and streaming contract specification | Complete | `ea822ab`; [Step 05 handoff](handoffs/STEP-05.md); frozen current contract in `docs/api/` |
| 06 | Schema, profiles, and memory consistency | Complete | `55ce2db`; [Step 06 handoff](handoffs/STEP-06.md); PostgreSQL/Redis CI passed 53 tests |
| 07 | Bedrock gateway contract and model routing | Complete | `e5333c1`; [Step 07 handoff](handoffs/STEP-07.md); PostgreSQL/Redis CI passed 58 tests |
| 08 | Durable usage accounting and budget enforcement | Complete | `b9e41d5`; [Step 08 handoff](handoffs/STEP-08.md); durable reservations and settlement implemented |
| 09 | Bedrock adapter and model evaluation | In progress | [Step 09 handoff](handoffs/STEP-09.md); adapter, SDK fixtures, and synthetic evaluation are implemented; live Mumbai model evaluation awaits AWS account/model access |
| 10 | Transaction-safe chat and streaming lifecycle | Complete | `3a1da29`; [Step 10 handoff](handoffs/STEP-10.md); PostgreSQL/Redis CI passed 81 tests with no skips |
| 11 | Durable summaries and extraction jobs | Complete | [Step 11 handoff](handoffs/STEP-11.md); PostgreSQL/Redis integration and retry/replay coverage passed in CI |
| 12 | Bedrock-only configuration and operational readiness | Complete | `93dd717`–`6900723`; [Step 12 handoff](handoffs/STEP-12.md); final PostgreSQL/Redis CI passed 98 tests |
| 13 | Backend regression and contract freeze | Complete | `28ee148`–`db1c9c7`; [Step 13 handoff](handoffs/STEP-13.md); PostgreSQL/Redis CI run [39581071817](https://github.com/realHKV/AiTherapyLive/actions/runs/39581071817) passed all 104 tests |
| 14 | Docker image and CI publishing workflow | Pending | — |
| 15 | ECS Fargate infrastructure and release workflow | Pending | — |
| 16 | Staging deployment and restore/rollback rehearsal | Pending | — |
| 17 | Backend handoff package for frontend | Pending | — |
| 18 | Frontend API client and authentication integration | Pending | — |
| 19 | Frontend chat, profile, usage, and billing integration | Pending | — |
| 20 | Browser acceptance and coordinated release | Pending | — |

## Phase A — establish and secure the current backend

### Step 00 — Establish a reproducible baseline

**Read:** `pom.xml`, wrapper, Dockerfile, `application.yaml`, migrations, existing tests, and the current reference's findings.

**Implement:** document existing build/test failures before repairing only baseline blockers. Add a local PostgreSQL/Redis environment (for example Compose), a safe configuration template, and synthetic fixtures. Ensure local configuration is actually loaded by the chosen commands; do not assume Spring reads `.env`. Add database/Redis integration-test support, using isolated containers when available. Establish characterization tests for core DTOs, Flyway startup, and encryption using non-production keys/ciphertext.

**Exit gate:** a fresh checkout can start its local dependencies and run the documented tests. Real database migrations and encrypted field reads/writes work. Baseline failures and local user edits, especially `JacksonConfig.java`, are accounted for. No live AI call or live payment is required.

### Step 01 — Close immediate production exposure

**Read:** `DevController`, `SecurityConfig`, `OAuth2SuccessHandler`, JWT filters/provider, `JacksonConfig`, and logging configuration.

**Implement:** disable development subscription routes in normal mode; require authentication for account deletion; remove token logging and token-bearing examples without reproducing them in reports; set safe production logging and error details. Preserve public login/register/refresh and correctly authenticated webhook handling. Add focused HTTP/security regression tests.

**Exit gate:** anonymous deletion fails; normal-mode users cannot self-upgrade through `/dev/*`; no tokens appear in captured test logs; cross-user authorization tests fail safely. Existing legitimate login/chat routes still work.

### Step 02 — Make authentication and deletion reliable

**Read:** auth/token/account services, OAuth success handler, JWT authentication, user/refresh repositories, and frontend auth expectations (read-only).

**Implement:** serialize concurrent refresh rotation, apply disabled/deleted-account rules consistently, define logout/revocation behavior, and cover Redis failure behavior. Specify a safer OAuth handoff than a JWT in a redirect URL; expose any changed callback flow through a versioned or opt-in contract for Phase F. Handle OAuth authorization state across service instances. Define deletion behavior for in-flight work and access tokens; later jobs must honor it.

**Exit gate:** concurrent use of one refresh token cannot mint multiple successful replacement chains; revoked/disabled/deleted access is rejected as designed; Google round-trip behavior can be exercised with an identity-provider fixture; callback code/ticket replay fails. Real Google staging validation remains a Step 16 requirement. Existing frontend remains on the compatible path until Phase F.

### Step 03 — Fix payments and define entitlements

**Read:** payment/dev controllers, `SubscriptionService`, subscription expiry job, subscription/payment entities, V4, and frontend payment callers (read-only).

**Implement:** bind payments to a server-owned purchase/user record; verify amount, currency, capture status, and ownership. Use signed raw-body webhook validation and durable replay protection. Make duplicate and concurrent callbacks safe. Record the chosen renewal/expiry rule and require a deliberate decision before changing the ₹50 product price. Introduce a single server-side entitlement lookup used by later routing/quotas; clients cannot supply effective tier.

**Exit gate:** wrong-user, wrong-currency, low-amount, invalid-signature, duplicate, concurrent, and expired-entitlement tests pass. A valid purchase grants the intended entitlement exactly once. Do not redesign/refund/recurring-billing features beyond the selected purchase flow.

## Phase B — modernize and stabilize contracts/data

### Step 04 — Upgrade framework and dependencies in checkpoints

**Read:** `pom.xml`, wrapper/base images, Jackson configuration and imports, JJWT adapters, security configuration, all baseline tests, and official release guides.

**Implement:** select and record a maintained target release using current official documentation. Follow the 3.5 migration checkpoint and the applicable 4.x guides from FUTURE_ARCHITECTURE. Keep each checkpoint independently reviewable; retain Java 21 initially. Resolve starter, serialization, security, Hibernate/Flyway, and third-party Jackson compatibility explicitly. Produce a dependency inventory and scan report.

**Exit gate:** the same baseline tests pass at each checkpoint, including existing synthetic ciphertext reads, JSON date/boolean fields, JWTs, schema validation, and async dispatch. No applied SQL migration was rewritten. Record unsupported/overridden dependencies rather than silently ignoring them.

### Step 05 — Specify the HTTP and streaming contracts

**Read:** controllers/DTOs, security exceptions, `Frontend/src/api/*`, auth/subscription contexts, and chat page (read-only).

**Implement:** specify memory review/confirm/edit/resolve/forget/reset endpoints, optimistic concurrency, and canonical onboarding/persona fields from MEMORY_DESIGN. Add a versioned API specification and synthetic request/response fixtures, preferably `docs/api/openapi.yaml` plus `docs/api/streaming.md`. Cover current routes and proposed usage/status, idempotency, auth exchange, and stream events. Define validation limits, null/date/boolean serialization, ownership errors, 429/reset metadata, provider-unavailable errors, and a consistent error envelope. Specify stream start/delta/completion/error semantics, IDs, partial persistence, and cancellation.

**Exit gate:** backend contract tests validate the implemented portion. Every proposed endpoint/event is labeled pending until its implementing step passes. Choose an explicit compatibility strategy: preserve existing `/api/v1` semantics, or add opt-in/versioned changes that the old frontend never selects. Do not break live callers while deferring their changes to Phase F.

### Step 06 — Reconcile schema and profile/memory behavior

**Read:** profile/memory services, entities/repositories, V1–V4, encryption converter, and account deletion.

**Implement:** resolve the encrypted-memory-title size mismatch with a new migration; fix profile creation inside read-only transactions; validate lengths, page bounds, dates, and memory types. Implement the supported onboarding/persona mapping from MEMORY_DESIGN, preserving compatible aliases until frontend integration. Add memory lifecycle, evidence, confirmation, and version fields plus source suppression/context-revision metadata. Make user-confirmed profile facts authoritative. Specify null/clear behavior and implement forget/reset semantics across source eligibility and derived summaries; tests can use job fixtures before Step 11. Avoid speculative tables for deferred voice/Cognito.

**Exit gate:** empty-database and upgrade-from-existing-schema paths pass; long encrypted fixtures persist/decrypt; GET/create/update/delete and pagination behave according to the contract; user ownership holds. Existing UUIDs/ciphertext remain compatible. Pending memory candidates do not become confirmed facts automatically. Forget/reset excludes known source material and invalidates derived summaries; Step 11 must reject stale job writes using the context revision.

## Phase C — build the Bedrock application flow

### Step 07 — Define gateway and routing boundaries

**Completed:** `e5333c1` introduced the opt-in `GenerationGateway` boundary with provider-neutral command, result, stream-event, cancellation, usage, policy, and sanitized-failure types. `GenerationRouter` selects the model from trusted entitlement and purpose, rejects client model IDs, and has a deterministic fake for `dev`/`test`. Routing stays disabled by default and the legacy gateway remains isolated until Steps 08–12. CI passed routing tests for every purpose, FREE, active/expired PRO, rejected model selection, invalid configuration, cancellation, and the PostgreSQL/Redis suite (58 tests, no skips). No AWS invocation occurred; Step 09 retains that external validation gate.

**Read:** `AIGateway`, current gateway, `PromptBuilder`, chat/summary services, AI DTOs, and entitlement code.

**Implement:** introduce provider-neutral request/result/event types carrying purpose, approved model policy, usage, finish/error state, and cancellation. Resolve tier and model selection on the server. Use a deterministic fake gateway for development/tests. Wire configuration validation for FREE/PRO/summary model selections without inventing production values. Keep the current provider path isolated until Step 12 removes it from the target deployment.

**Exit gate:** routing tests cover every AI purpose and both tiers, expired subscriptions, rejected client-selected models, and invalid configuration. API DTOs do not leak SDK internals. No real provider call is introduced before budget enforcement exists.

### Step 08 — Implement atomic quotas and durable usage accounting

**Completed:** durable PostgreSQL accounting now records logical AI requests, idempotency keys, provider attempts, UTC daily user/account/model-region buckets, leases, and settlement. `UsageAccountingService` atomically reserves estimated input plus bounded output before execution; it releases abandoned pre-provider work and conservatively settles expired in-flight work. Redis supplies atomic per-minute admission and owned, renewable one-generation leases; an outage is rejected as `ACCOUNTING_UNAVAILABLE`, never treated as permission. `GET /usage/status` returns the server-derived entitlement tier, remaining request/token allowance, reserved tokens, policy ID, and UTC reset. Limits are configuration inputs under `AI_USAGE_*`; no provider price or spend amount is hardcoded. The legacy chat route has the short-window admission filter only; Step 10 will make its generation lifecycle create, settle, and reconcile durable reservations. Maven tests cover idempotency, settled attempts, PostgreSQL migration upgrade, mocked Redis failure/rejection, authenticated status, and concurrent durable reservations when container tests run in CI.

**Read:** current Redis filter, entitlement/routing contracts, FUTURE_ARCHITECTURE quota rules, and repositories/migrations.

**Implement:** add a durable usage/reservation ledger and idempotency keys; atomic Redis rate windows and expiring/renewed concurrency leases; FREE subsidy, PRO allowance, account-wide spend, and model/region quota gates. Cover greetings/extraction as well as chat. Define reservation/settlement/refund/reconciliation behavior for retries, crashes, timeouts, and missing final usage. Expose the agreed usage/reset API; values must come from configuration, not duplicate frontend constants.

**Exit gate:** concurrent requests cannot overspend a reservation budget or bypass one-generation rules; duplicate request IDs do not double-charge; restarts/Redis eviction do not erase paid usage; unavailable accounting rejects new billable work clearly. Test with a fake gateway and a controllable clock. Monetary caps remain deployment inputs until real model costs are measured.

### Step 09 — Implement Bedrock and evaluate candidates

**Progress:** the opt-in AWS SDK v2 Bedrock Converse/ConverseStream gateway, bounded async client, cancellation, error mapping, and synthetic evaluation fixture/report template are implemented. SDK fixture tests cover the provider boundary. The live candidate comparison, model access, selected account/region/profile, price assumptions, and smoke run remain pending AWS account inputs; no invocation has been made. Step 09 stays in progress until those results are recorded.

**Read:** gateway/routing contracts, AWS SDK BOM choices, FUTURE_ARCHITECTURE model candidates, and selected models' current official API/region/pricing documentation.

**Implement:** `BedrockGateway` for synchronous and streamed text with SDK credential-chain support, bounded deadlines, cancellation, throttling/error mapping, and token/usage reporting. Add a small synthetic conversation/extraction evaluation set and results report. Compare the selected FREE/PRO/summary candidates; record exact identifiers, region/inference profile, quality results, latency, and cost assumptions.

**Exit gate:** SDK-fixture tests cover normal completion, throttling, invalid model access, timeout, partial response, and disconnect. A bounded real Bedrock smoke/evaluation run validates the chosen account/region and usage accounting when access is available. Candidate names in the plan are not evidence of invocation support. No external-provider fallback is allowed in the new path.

### Step 10 — Rework chat and SSE lifecycle

**Progress:** the opt-in v2 path persists idempotent session starts and chat turns, reservations, terminal state, named SSE events, and owner-scoped request status. Provider calls execute outside short database transactions on a bounded executor. Session start/end and user turns use account/session row locks; PostgreSQL enforces one active conversation. V2 memory context is bounded and filters by confirmation, expiry, source eligibility, and revision, with topic/due/recency/importance ranking. Disconnects, first-token/total deadlines, and post-commit session end cancel active provider work. The PostgreSQL/Redis-backed CI suite passed all 81 tests with no skips, including idempotent replay, cancellation, session end during generation, and a real HTTP stream-client check for named SSE event framing and Unicode deltas ([run 39571804383](https://github.com/realHKV/AiTherapyLive/actions/runs/39571804383)). The latest local run passed 69 tests and skipped 12 container tests because Docker is unavailable there. Legacy routes remain the default for the current frontend; v2 stays disabled until the later frontend/traffic gate. No AWS setup or call is needed for Step 10; the user selected Mumbai (`ap-south-1`) for the later Bedrock evaluation.

**Read:** `ChatService`, `ChatController`, session/message schema, prompt builder, usage service, and stream contract.

**Implement:** make session creation/end and message submission idempotent; enforce conversation ownership and state transitions under concurrent clients. Avoid holding database transactions during model calls. Implement bounded per-user memory retrieval from MEMORY_DESIGN: lifecycle/confirmation filters, topic relevance, recency, due follow-ups, and importance; apply source suppression and summary revision checks. Bound assembled context by tokens. Persist explicit generation status so errors and incomplete streams cannot masquerade as successful replies. Cancel upstream work on client disconnect/deadline, settle usage, and send the agreed terminal event. Avoid paid greeting regeneration on reload.

**Exit gate:** repeat-end does not inflate session count; concurrent starts do not create unintended active sessions; repeated submissions produce one logical turn; failed/canceled streams have defined persisted state and usage. Test slow first token, Unicode/chunk boundaries, Redis loss, provider errors, and session end during generation with an HTTP stream client. Preserve the old frontend contract or its isolated legacy route until Phase F.

### Step 11 — Make summaries durable and extraction validated

**Progress:** complete. GitHub Actions run [39574509660](https://github.com/realHKV/AiTherapyLive/actions/runs/39574509660) passed all 85 tests with PostgreSQL and Redis containers and no skips. The local clean suite also passed; 17 container tests were skipped locally because Docker is unavailable, and Mockito required an explicit local JVM agent. Session completion creates a deduplicated job in the same transaction. The database worker uses leases, bounded retries and explicit replay, Bedrock routing with shared usage accounting, strict evidence validation, and atomic summary/pending-candidate persistence guarded by account, conversation, and context revision. The worker remains disabled by default until routing is configured. No AWS setup or calls are needed for this step.

**Read:** `SummarizationJob`, `SessionEndedEvent`, conversation repositories, profile/memory/trait models, deletion rules, and usage reservations.

**Implement:** use a PostgreSQL-backed extraction job table with safe claims, leases, deduplication, and retries. A queue/outbox is a later decision requiring evidence, not an initial dependency. Enqueue work atomically with session completion; store purpose, policy version, attempts, and explicit success/failure states. Use Bedrock through the same accounting boundary. Validate extraction values and source evidence; create pending candidates instead of overwriting confirmed profile facts. Recheck context revision inside the final persistence transaction, then save result/candidates and successful job status atomically. Deduplicate by job/output identity, not title equality, and make failures recoverable.

**Exit gate:** a process crash after commit loses no job; duplicate delivery creates no duplicate memories or session counts; failed extraction is not marked successful; deleted accounts/conversations cannot be resurrected; profile reset follows Step 06's policy. Retry/backoff/exhaustion and operator replay are tested with synthetic data. No AWS queue is required for the initial database-backed design.

## Phase D — make the backend release-ready

### Step 12 — Finalize Bedrock-only runtime and operations

**Progress:** complete. GitHub Actions run [39578380283](https://github.com/realHKV/AiTherapyLive/actions/runs/39578380283) passed all 98 tests with PostgreSQL and Redis containers and no skips. The local clean suite discovered 98 tests: 76 passed and 22 container tests were skipped because Docker is unavailable. The OpenAI-compatible adapter and API-key configuration are removed. Unversioned session and synchronous-message request shapes route through the accounted v2 workflow; the legacy stream returns 503 until the frontend migrates to v2 events in Step 17. Production-profile startup validates application secrets, HTTPS CORS origins, verified PostgreSQL TLS, Redis TLS, Bedrock/routing/model prerequisites, region alignment, v2 chat, and extraction worker settings. Actuator readiness checks PostgreSQL/Redis while liveness stays independent of dependencies and Bedrock; bounded metrics and correlation IDs exclude user content. Chat execution is bounded, stream shutdown cancels provider work through the normal settlement path, and the UTC subscription schedule uses a PostgreSQL advisory lock across replicas. Dollar spend is intentionally not estimated until model/region pricing is verified. No AWS calls or setup have been made.

**Read:** all AI entry points/configuration, async executor/schedulers, security, Dockerfile, and operational requirements in AWS_MIGRATION.

**Implement:** remove the old external-provider adapter/configuration/AI-key dependency from the target runtime after the new path passes. Add startup configuration validation, health/readiness, bounded execution, safe structured logs, correlation IDs, metrics for streams/jobs/tokens/spend, and graceful shutdown. Coordinate scheduled work across instances. Define health behavior for DB/Redis/model failures and minimize diagnostic exposure.

**Exit gate:** production-mode startup succeeds with only required Bedrock/AWS settings and other application secrets; missing required settings fail clearly. A test verifies no external AI endpoint can be invoked. Health/metrics reveal failures without user content/tokens. Shutdown drains or terminates work with tested settlement/recovery semantics. Update BACKEND_REFERENCE to implemented behavior.

### Step 13 — Run backend acceptance and freeze the release contract

**Progress:** complete. GitHub Actions run [39581071817](https://github.com/realHKV/AiTherapyLive/actions/runs/39581071817) passed all 104 tests with PostgreSQL and Redis containers and no skips. The OpenAPI and streaming specifications now describe implemented behavior, including the disabled legacy stream and v2 event contract; no documented operation is labeled as pending. JSON API errors use a consistent envelope across controller validation, framework 404s, security, OAuth callback identity rejection, and rate limiting. PostgreSQL acceptance covers concurrent duplicate callbacks, cross-purchase payment replay, same-key encrypted-data readability through a fresh service instance, confirmed/source-eligible memory filtering, extraction retries/deletion races, usage limits, and replica advisory locking. The local suite discovered 104 tests: 79 passed and 25 container-backed tests were skipped because Docker is unavailable. Real Bedrock, Google identity-provider, and Razorpay test-mode calls remain Step 16 gates because those accounts/services have not been configured.

**Read:** all previous handoffs, API/stream specifications, current reference, and AWS acceptance matrix.

**Implement:** close backend integration gaps using real PostgreSQL/Redis and isolated provider/payment fixtures. Exercise multiple application instances, duplicate callbacks, concurrency, encryption restores, failed summaries, quotas, account deletion, and API/stream compatibility. Run bounded live integration tests separately when credentials are available. Evaluate memory retrieval against synthetic topic changes, irrelevant high-importance facts, correction, pending candidates, and forget/reset races. Finalize the contract and sample fixtures for frontend use.

**Exit gate:** all local backend acceptance checks pass; no documented endpoint remains merely proposed in the release specification. Real-provider/account checks are either evidenced or explicitly tracked for Step 16. No unresolved defect permits unauthorized access, duplicate entitlement, lost encrypted data, or unbounded spending. Frontend implementation still waits for Step 17.

## Phase E — package and validate the backend on AWS

### Step 14 — Build the container and CI workflow

**Read:** Dockerfile, Maven build, repository workflow conventions, AWS_MIGRATION release pipeline, and registry decisions.

**Implement:** reproducible non-root Java image, separate tested migration-only entrypoint/artifact, PR verification workflow, and trusted-release Docker Hub publishing by commit tag/digest. Include scans, SBOM/provenance, appropriately scoped CI secrets, and immutable promotion metadata. Keep untrusted PR jobs unable to publish/deploy. Prepare optional ECR copying only if selected.

**Exit gate:** local/CI test and image build pass; the image starts with external config and supports shutdown. Migration-only execution exits without starting the web server. Publishing is performed only with implementation-session authorization; otherwise the reviewed workflow remains ready and the publication gate is pending. Record the actual digest only after publication.

### Step 15 — Prepare ECS Fargate infrastructure and deployment

**Read:** AWS_MIGRATION and recorded region/network/budget/registry decisions.

**Implement:** reviewable infrastructure-as-code for ECS Fargate, ALB, private data services, secret injection, separate task/execution/deployment roles, GitHub OIDC, logs/alarms, and service scaling. Implement one-off migration tasks, deploy-by-digest, service-stability checks, smoke tests, and rollback. Clarify CI versus infrastructure ownership of task revisions. Preserve application encryption/signing keys through secret references.

**Exit gate:** syntax/plan validation passes and the generated changes are reviewable; no plaintext secrets or EC2-server/Kubernetes management is introduced. Task roles, security groups, outbound access, total DB pool capacity, and resource costs are documented. Applying infrastructure follows session authorization and requires concrete account/region/budget inputs.

### Step 16 — Validate staging and rehearse recovery

**Read:** pipeline/runbooks, backend acceptance report, actual AWS settings, and AWS_MIGRATION cutover/rollback rules.

**Execute:** deploy the tested backend image to staging, run the one-off migrator, validate health/HTTPS/CORS and actual Google redirects, and run synthetic FREE/PRO chat, signed payment, quota, SSE, and job scenarios. Rehearse backup restoration with compatible keys and migration history. Replace tasks during streams/jobs; test fresh registry pulls and previous-revision rollback.

**Exit gate:** real AWS integration evidence, alarms, cost measurements, model access, restore, and rollback checks pass. Update rate/spend configuration from measured costs. Keep production traffic unchanged. A public health response alone is not sufficient evidence.

### Step 17 — Publish the backend handoff for frontend work

**Deliver:** a repository-local handoff report with the stable API specification, streaming protocol/examples, auth/callback flow, error codes, quota/reset semantics, payment flow, synthetic fixtures, staging base URL, and supported feature flags. Include migration/rollback constraints and exact backend revision/image digest. Reference secret locations only; never include credentials or reusable tokens.

**Exit gate:** a frontend agent can implement the client from this package without guessing response envelopes, boolean names, OAuth exchange, stream terminal states, payment ownership, or tier limits. Steps 00–16 have the required evidence; blocked cloud validation is visible and does not count as backend staging completion.

## Phase F — join the frontend and complete the release

### Step 18 — Integrate API client and authentication

**Read:** Step 17 handoff; `Frontend/src/api/client.js`, `auth.js`, auth context, login/register pages, OAuth redirect handler, and environment/build settings.

**Implement:** one consistent `/api/v1` base URL; correct envelope unwrapping; token/refresh/logout behavior matching the finalized backend contract; single-flight refresh handling and safe retry boundaries. Implement the new OAuth exchange and remove legacy token-in-URL handling when the backend migration path is ready. Configure credential/CORS/CSRF behavior to match the chosen storage design. Keep AWS credentials out of the browser.

**Exit gate:** browser registration/local login/Google login/expiry/refresh/logout/account deletion pass in staging. Concurrent expired requests do not trigger competing refreshes; non-idempotent operations are not blindly replayed. Production still uses its existing compatible release until Step 20.

### Step 19 — Integrate product flows and streaming

**Read:** Step 17 contract; chat/profile/payment API modules, chat/onboarding/profile pages, and subscription context.

**Implement:** fix the separate streaming base URL and double-unwrapping mismatch; parse SSE incrementally across chunk boundaries; handle terminal completion/error, cancel, retry, and partial messages according to backend rules. Reuse session/request IDs appropriately. Connect canonical profile/onboarding/persona fields and explicit onboarding completion. Add grouped candidate review plus memory save/edit/confirm/resolve/forget/reset flows, reflecting the documented source-history and deletion semantics. Correct encryption wording to describe field-level application encryption rather than end-to-end encryption. Show server-provided tier/usage/reset state and meaningful capacity errors. Use the server-owned purchase/verification flow and remove production development-upgrade controls.

**Exit gate:** FREE and PRO text conversations, canceled/failed streams, page refresh, quota exhaustion/reset, profile/memory changes, confirmation/forget/reset and stale-summary protection, purchase success/failure/duplicate callbacks, and tier expiry all work in browser tests. No frontend hardcoded entitlement or model selection can override server policy. Voice remains hidden/disabled.

### Step 20 — End-to-end acceptance and coordinated release

**Read:** backend/frontend handoffs, AWS cutover/rollback plan, production configuration references, and selected model/cost decision records.

**Execute:** run the complete browser suite against staging, including slow-network/disconnect behavior, expired tokens during active use, duplicate submissions, cross-account access, and mobile layout/accessibility of the changed flows. Confirm no keys/transcripts/tokens appear in logs or browser URLs. Record compatible frontend build/backend image/config/schema versions as one release manifest.

**Exit gate before traffic switch:** backend and browser acceptance pass, budgets/alerts and rollback resources exist, and the cutover/restore rehearsal has evidence. Apply the approved data migration and traffic switch only within session authorization. Deploy compatible backend changes before selecting new frontend behavior; keep old contract support for the rollback window. If switching databases, pause writes and use AWS_MIGRATION's data reconciliation rules—rolling back an image cannot recover divergent writes.

**Completion:** production smoke checks succeed, payment/job/stream monitoring is stable during the agreed observation window, and documentation reflects deployed behavior. If deployment is outside the authorized task, deliver the tested release package and mark the production gate pending instead of claiming completion.

## 4. Copyable next-agent prompt

```text
Implement Step [NN] from Backend/docs/DEVELOPMENT_PLAN.md.
Read Backend/docs/AI_CHANGE_GUIDE.md and the step's source files/handoffs first.
Inspect git status and preserve unrelated work.

Finish this step and its exit tests; do not combine unrelated later steps.
Use synthetic data. Do not treat proposed config/routes as implemented.
Follow session authorization for external actions and explain any external
validation that remains blocked while completing independent local work.

Update relevant API/current-state docs and docs/handoffs/STEP-NN.md.
Update the execution tracker with actual status and evidence.
Report behavior changes, tests actually run, unresolved items, and rollback.
Frontend edits start at Step 18; read callers earlier for compatibility only.
For memory/schema tasks, follow MEMORY_DESIGN.md and SCHEMA_REVIEW.md.
```

The first implementation request should be **Step 00**, followed by Step 01. This document itself does not start implementation.

## 5. Later releases

Cognito and PRO voice are deliberately outside this first-release sequence. Each later release should repeat the same order: backend design/contracts → implementation/tests → staging handoff → frontend integration → coordinated release. Use FUTURE_ARCHITECTURE's identity/voice sections when those features are explicitly selected; do not add them as hidden prerequisites for finishing text chat.
