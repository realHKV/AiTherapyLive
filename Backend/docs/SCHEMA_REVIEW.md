# Schema and feature alignment review

Reviewed 2026-10-19 against V1–V4 migrations, all nine entities, repositories, relevant services/controllers/DTOs, and frontend onboarding/chat/profile/payment callers. **Static source review only:** no live schema/data inspection or database execution. Findings distinguish present defects from requirements of the proposed Bedrock release. No migration or application code was changed.

The finalized storage direction is encrypted PostgreSQL tables for active memories, with a PostgreSQL-backed extraction job table initially. [MEMORY_DESIGN](MEMORY_DESIGN.md) owns the proposed four-layer context, user confirmation, relevance, and forget/reset behavior. S3 active-memory files and vector search are outside the first release. Findings below remain a record of current code, not completed fixes.

## Verdict

The relational foundation fits the application: users, profiles, conversations/messages, memories/traits, and subscriptions/payments are sensible boundaries. Keep PostgreSQL and evolve these tables. The current schema, API, and UI do **not** fully agree, and the schema lacks durable state for the planned usage budgets and background processing. A wholesale rewrite, a vector database, or an immediate identity microservice is not justified by these findings.

The most urgent work is end-to-end field mapping, encrypted column sizing, purchase ownership, and atomic lifecycle transitions. Additional tables should support concrete operations, not merely mirror every service class.

## 1. Current feature-to-storage fit

| Feature | Current storage | Fit / action |
| --- | --- | --- |
| Local and Google login | `users`, `refresh_tokens` | Suitable first-release structure; strengthen normalized identity uniqueness and refresh concurrency |
| Onboarding and personalization | `therapy_profiles` plus V2 columns | Partial: fields exist in SQL but are absent from Java/API; frontend concern payload also mismatches |
| Therapist selection | `ai_persona` used by UI; unused `selected_therapist` SQL column | Conflicting meaning; define a canonical persona identifier and server-side prompt mapping |
| Text conversations | `conversations`, `messages` | Suitable content storage; missing request/generation state, deterministic turn order, and enforced lifecycle invariants |
| AI summary/extraction | Summary text and `summarized_at` | Insufficient for retries, durable jobs, progress, and truthful failure reporting |
| Long-term memories | `long_term_memories` | Useful shape; title size, provenance, deduplication, dates, and resolve/follow-up behavior need reconciliation |
| Personality traits | `personality_traits` | Stores current labels/confidence; source semantics and normalized uniqueness disagree with service logic |
| FREE/PRO access | `user_subscriptions`, `payments` | Works as a current entitlement snapshot; insufficient purchase binding and usage-period history |
| Bedrock budgets and quotas (planned) | Estimated `token_count` only | Missing usage/reservation/attempt records; estimates are not billing evidence |
| Account/profile deletion | User cascades plus explicit service deletes | Clear basic deletion paths; future jobs and retained purchase/usage data need an explicit deletion policy |

## 2. Findings, ordered by impact

### F01 — High: onboarding data does not reach persistent profile fields

[AboutYou.jsx](../../Frontend/src/pages/AboutYou.jsx) sends `gender`, `country`, `enjoyments`, `additionalNote`, and `topics_of_concern` as a JSON string. [UpdateProfileRequest](../src/main/java/com/hkv/AiTherapy/dto/request/UpdateProfileRequest.java) accepts only `preferredName`, `ageRange`, `communicationStyle`, `aiPersona`, and `topicsOfConcern` as a list. [ProfileService](../src/main/java/com/hkv/AiTherapy/service/profile/ProfileService.java) saves only those accepted fields.

[V2](../src/main/resources/db/migration/V2__add_onboarding_fields.sql) adds gender/country/enjoyments/selected therapist/onboarding completion, but [TherapyProfile](../src/main/java/com/hkv/AiTherapy/domain/TherapyProfile.java) and [ProfileResponse](../src/main/java/com/hkv/AiTherapy/dto/response/ProfileResponse.java) do not map them. `additionalNote` has no column. Unknown-field rejection versus silent ignoring depends on the running Jackson configuration; either way these values have no implemented persistence path. Even concerns have the wrong field name and JSON type.

**Recommendation:** define one canonical camelCase request/response contract. Send `topicsOfConcern` and interests as arrays; serialize/encrypt inside the service. Map the V2 columns for supported features. Add an encrypted `additional_note` only if retaining that form field is intended. Round-trip every visible field through save → reload → response → prompt selection. Existing SQL columns alone do not establish a working feature.

### F02 — High: persona selection and onboarding completion have conflicting meanings

[profile.js](../../Frontend/src/api/profile.js) writes therapist IDs such as `dr_sarah` into `aiPersona`. The entity defaults this field to `calm`, while V2 provides an unused `selected_therapist`. [Chat.jsx](../../Frontend/src/pages/Chat.jsx) treats any nonempty `aiPersona` as completed onboarding. A default profile can therefore pass that guard without a selection. [PromptBuilder](../src/main/java/com/hkv/AiTherapy/service/ai/PromptBuilder.java) inserts the string directly; it does not translate the selected card into a defined server-side persona prompt.

**Recommendation:** use one canonical persona ID, validated against a small versioned server catalog; keep communication style separate. Reuse/map `selected_therapist` or migrate to `persona_id`, but do not keep two competing sources of truth. Expose explicit onboarding status and set completion only after validating the required steps. Do not infer old completion from profile existence or the default `calm` value. Version/snapshot persona and prompt policy per conversation when reproducibility matters. A database persona catalog is unnecessary unless runtime administration is a requirement.

### F03 — High: encrypted memory titles can exceed their SQL column

[V1](../src/main/resources/db/migration/V1__init.sql) creates `long_term_memories.title VARCHAR(500)` while the [entity](../src/main/java/com/hkv/AiTherapy/domain/LongTermMemory.java) declares TEXT. [EncryptionService](../src/main/java/com/hkv/AiTherapy/encryption/EncryptionService.java) stores Base64 of plaintext bytes plus a 12-byte IV and 16-byte tag. Encoded length is `4 × ceil((plaintextBytes + 28) / 3)`: 347 plaintext bytes fit in 500 characters, but 348 need 504. Multibyte text reaches the limit with fewer characters. Manual title input has no length cap.

**Recommendation:** widen the SQL column to TEXT in a new migration, and set a separate validated plaintext product limit. This preserves existing ciphertext; it does not require re-encryption. Test long and multilingual titles against PostgreSQL. Do not assume Hibernate validation catches every semantic length discrepancy.

### F04 — High: payment records prove capture, but do not establish purchase ownership

[V4](../src/main/resources/db/migration/V4__add_subscription_and_payments.sql) stores a user ID and unique payment ID only after processing; it has no server-created purchase record. [SubscriptionService](../src/main/java/com/hkv/AiTherapy/service/payment/SubscriptionService.java) accepts a fetched captured payment and assigns it to the requesting user without validating its intended owner/order or currency. A globally unique payment ID blocks duplicate rows, not a first claim by the wrong user. A pre-insert existence check also does not make concurrent callbacks atomic.

`user_subscriptions` stores only current tier/expiry. An upgrade resets expiry to now + 30 days, and there is no durable entitlement-period boundary to anchor the planned period quota.

**Recommendation:** create a server-owned purchase with user, provider order/link identifier, expected amount/currency, product/policy version, and state before checkout. Link capture to that record and apply payment/entitlement updates atomically. Keep `user_subscriptions` as the current snapshot; add period history if quotas/renewals need it. Decide extension/reset semantics explicitly. Store currency alongside amounts, enforce positive captured amounts, and keep monetary values exact. The existing ₹50 price is a product decision, not an SQL default that validates payment.

### F05 — High: conversations/messages lack safe lifecycle and generation records

[ChatService](../src/main/java/com/hkv/AiTherapy/service/chat/ChatService.java) finds the latest active session, but SQL does not enforce one active session per user. Concurrent creation can produce multiple active sessions. Repeated end calls increment `total_sessions` repeatedly. Entity counters use read-modify-write without a version/lock. Streaming stores the user message before generation and saves the assistant only on completion; there is no durable request ID, generation state, or failed/partial marker.

**Recommendation:** decide whether one active conversation per user is the invariant. If yes, enforce it with an appropriate unique active-user constraint/index plus transactional conflict handling after repairing existing duplicates. Add idempotent state transitions and request IDs. Track a logical generation/request with pending/running/completed/failed/canceled state and its provider attempts; use locks or version checks where concurrent writes must be rejected. A constraint alone does not fix service retries or lost updates.

For turn order, use a stable per-conversation sequence or an explicitly defined tie-breaker. Current queries sort only on `created_at`; [findAfterCursor](../src/main/java/com/hkv/AiTherapy/repository/MessageRepository.java) uses a strict timestamp comparison and does not bind the cursor subquery to the same conversation. It is not currently exposed by a GET-history route, but needs fixing before history is added. Two equal timestamps must not cause skipped/reordered messages.

### F06 — High for the Bedrock release: token estimates cannot enforce spend

Message token counts use string length divided by four; greetings use constants. Conversation counts omit full repeated prompt context, summaries, provider attempts, model/region, output categories, and pricing. There are no reservations, daily/period usage records, or durable idempotency keys.

**Recommendation:** keep existing counts as legacy estimates/display statistics. Add a durable logical AI-request/reservation record and provider-attempt usage data, shared across chat, greetings, and extraction. Record tier/policy snapshot, operation, model/profile ID, region, actual/estimated usage, pricing version, reserved/settled cost, status, and timestamps. Separate logical user allowance from provider-attempt cost so retries do not double-charge the user but still count against spend. Redis handles short-window limits, not the sole durable billing ledger.

### F07 — High for reliability: summary status is not a job lifecycle

[SummarizationJob](../src/main/java/com/hkv/AiTherapy/service/job/SummarizationJob.java) uses an in-process event; no row records pending work/claims/retries. Extraction/persistence exceptions can still lead to `summarized_at` being set. Failed work and successful empty output are not reliably distinguishable, and replay can partially duplicate writes.

**Recommendation:** add a durable job row, atomically created on the session transition, with a deduplication key, input revision, state, attempts, claim/lease, next retry, safe error code, and completion timestamp. Only mark the conversation summarized after all required writes succeed and the captured context revision is still current. Store raw model content only in appropriately encrypted content storage if actually needed; job errors should not copy transcripts. Use a PostgreSQL-backed job table initially; a queue/outbox is a later architecture decision.

### F08 — Medium: memory/trait semantics do not match stored provenance

The summary builder never assigns `sourceConversation` to new memories despite the existing FK. It puts extraction day into `occurred_at`, which can misrepresent a past/future event. Memory title comparison decrypts all existing rows, conflates equal titles, and has no concurrent retry uniqueness. A memory type vocabulary in SQL comments differs from the AI extraction vocabulary; comments are not constraints. Importance and trait confidence have no database bounds; AI extraction bypasses manual-request validation.

Trait `source` is documented as `user_stated`/`ai_inferred` but is written as `Session YYYY-MM-DD`. Labels are compared case-insensitively in Java while SQL uniqueness uses the stored key. Newly created entries are not appended to the deduplication list, so duplicate labels within one extraction can also cause conflicts. `trait_value` is neither populated by extraction nor included in the profile response/prompt.

**Recommendation:** populate provenance, distinguish `extracted_at` from known `occurred_at`, and allow unknown occurrence dates. Define one memory-type vocabulary across DTO/prompt/SQL with a migration mapping old values. Separate source kind from source conversation and observation time. Add bounded validation and constraints after cleaning old data. Deduplicate extraction retries by job/output identity; do not globally make encrypted titles unique or make titles the identity of a real-world fact. If histories from multiple sessions matter, add evidence links later rather than overwriting provenance ambiguously.

### F09 — Medium: inferred profile data silently overwrites user-entered facts

The summarizer writes extracted names and free-form ages directly into `preferred_name`/`age_range`. Onboarding expects categorical age ranges, while extraction can write an exact age or arbitrary string. Truncating to 50 characters prevents overflow but does not enforce the intended meaning.

**Recommendation:** treat manually edited profile values as authoritative. Store AI observations as memories or proposed updates until confirmed. Choose a canonical age-band representation if that is the product input; do not add exact birth dates just to fix the mismatch. If automatic updates remain a requirement, record provenance/confirmation and concurrency/version information to avoid overwriting newer user edits.

### F10 — Medium: several columns and repository comments describe unfinished features

There is no memory update/resolve endpoint; `is_resolved` is initialized false. `findDueFollowUps()` is unused by PromptBuilder. Mood-start/end have no request/update path. Session-history repository methods are not exposed by the controller. `total_sessions` is a profile counter that resets on profile deletion even though conversations remain. Cleanup query methods exist without corresponding scheduled invocation in the reviewed jobs.

**Recommendation:** mark these as inactive capabilities; either implement their user-facing contracts or leave them explicitly deferred. Define whether session count means lifetime completed conversations or sessions since profile reset, then derive it or maintain it transactionally from the correct source. A follow-up date is not evidence of a functioning reminder feature. Do not delete dormant columns until deployment usage and historical data are checked.

### F11 — Medium: identity and refresh invariants need strengthening

Email is unique as stored, but registration/login do not define normalization. OAuth provider/subject has a non-unique index although lookup expects one identity. Refresh rows have revocation state but no atomic single-use claim in the service; concurrent rotations can issue multiple chains.

**Recommendation:** define email normalization before changing uniqueness and inspect existing collisions. Enforce unique non-null provider/subject pairs for the current one-provider design. Lock/conditionally revoke refresh rows so only one rotation succeeds. Token-family/replacement metadata is useful if replay detection/logout-all is implemented, but not every identity refactor must await Cognito. Add `external_identities` only when actual multi-provider/Cognito linking is selected.

### F12 — Medium: encryption and deletion promises need precise boundaries

Encryption covers selected content fields, not all metadata; trait labels, ages, identity and payment metadata remain readable. The V2 `enjoyments` comment does not encrypt data without entity/converter support. The onboarding UI claims end-to-end encryption, but the backend decrypts prompt data and sends it to an AI service. Account deletion cascades through payment history; profile deletion retains transcripts capable of regenerating memories.

**Recommendation:** document field-level application encryption accurately and correct the UI claim in the frontend phase. Decide which new profile fields require converters and how existing plaintext, if any, is migrated. Preserve the current key during cloud migration. Define financial-record retention with the product owner rather than keeping audit records accidentally or silently deleting them. Use a reset epoch/cancellation policy so pending extraction cannot undo a profile reset. Adding soft deletion is not a substitute for an explicit deletion policy.

## 3. Minimum target data model

This is a design recommendation, not ready-to-apply DDL. Match names to the implementation and avoid overlapping ledgers.

| Area | Keep/change | New state only where required |
| --- | --- | --- |
| Identity | Keep users and hashed refresh tokens; fix invariants | Refresh lineage only if selected; external identities deferred with Cognito |
| Profile | Map supported V2 fields, canonical persona/onboarding state, encrypted note if retained | Context reset revision/version for stale-job protection |
| Conversations/messages | Keep encrypted content; enforce lifecycle/idempotency/order | Message/generation status and stable request linkage |
| Memories/traits | Keep core entities; correct title type, provenance, bounds, vocabulary | Extraction identity/provenance, lifecycle and confirmation state; source suppression for forget/reset |
| Purchases/entitlements | Keep captured payments and current subscription snapshot | Server-owned `purchases`; stable entitlement periods for period-based quotas; webhook receipt metadata if event-level replay/recovery requires it |
| Usage and generations | Existing counts remain estimates | Logical `ai_requests` with reservation/state/idempotency; `ai_attempts` for provider usage/retries; optional transactional budget aggregates for enforcement |
| Jobs | Keep summary result on conversation | `extraction_jobs` with durable claims/retries/deduplication; outbox only if queue design requires one |

Link generation state and cost to the same logical request where practical rather than creating competing `generations`, `reservations`, and `usage` records without clear ownership. A database-backed extraction job represents workflow; AI attempts represent potentially billable invocations within that workflow.

Future Bedrock model choice does not require a model table: a versioned allowlisted configuration can select models. Keep enough identifier/pricing/policy metadata on attempts to explain historical usage. No vector index or audio-blob table is needed for the first text release.

## 4. Constraints and indexes to validate during implementation

- Add agreed enum/range checks for conversation status, message role, memory importance, trait confidence, nonnegative counters, and state/timestamp coherence. Validate/repair old rows before enabling them; checks must match actual service transitions.
- Enforce current identity/purchase/request/job uniqueness at the database boundary. Application existence checks alone are insufficient.
- For a one-active-session rule, index the user key for active rows, not just the `status` column. Make duplicate cleanup and concurrent service behavior part of the change.
- Ensure a memory's source conversation belongs to the same user. Independent user/conversation FKs do not enforce that relationship; validate it transactionally or design a compatible composite constraint, including its delete behavior.
- Existing unique email/user_id constraints already support lookup; explicit duplicate indexes on email, subscription user_id, and profile-like keys should be reviewed before adding more. Do not drop indexes without inspecting the deployed schema and actual query plans.
- Add job claim/retry, usage user/period, purchase/provider identity, and deterministic history indexes with their corresponding queries. Consider expiry cleanup indexing only once cleanup is implemented and measured.
- ORM `@UpdateTimestamp` does not imply a database trigger. Bulk subscription downgrade currently bypasses entity updates; decide whether audit timestamps must be updated explicitly in bulk SQL.

## 5. Migration and development-plan integration

Do not rewrite V1–V4. Allocate the next unused Flyway version when implementation begins; do not preassign conflicting V5 files to different agents. Rehearse both clean creation and upgrade from the current schema, with synthetic legacy ciphertext.

| Development step | Required schema-review inputs |
| --- | --- |
| 00 | Verify actual schema/migration history and characterize F01–F03 with fixtures |
| 02 | Identity/refresh invariants and deletion semantics: F11–F12 |
| 03 | Server-owned purchases, atomic capture, entitlement periods: F04 |
| 05–06 | Canonical onboarding/persona contract, title sizing, profile authority, vocabulary: F01–F03, F08–F10 |
| 08 | Durable requests/attempts/budget accounting: F06 |
| 10 | Conversation state/idempotency/order and linked generation status: F05 |
| 11 | Durable extraction, provenance, reset protection: F07–F09, F12 |
| 18–20 | Round-trip every supported onboarding field; correct completion guard, persona mapping, and encryption wording |

Before backfill, inspect duplicate normalized identities, active sessions, trait keys, oversized/invalid field values, existing plaintext in newly mapped columns, and actual onboarding completion evidence. Do not fabricate user-provided facts or mark all existing profiles complete. Keep expand/backfill/validate/contract stages compatible with the old frontend until its final integration phase.

Required acceptance cases: pending candidates excluded from prompts; relevant recall across topic changes; confirmed memories corrected/resolved/forgotten without stale-source reintroduction; save/reload full onboarding; distinct persona behavior; long/multilingual encrypted title; confirmed user facts survive extraction; duplicate session end; simultaneous message retries; failed generation with cost settlement; duplicate payment capture; crashed summary job; profile reset during pending extraction; legacy schema/key restore. These checks have **not** been executed in this review.
