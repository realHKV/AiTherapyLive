# Memory design for the first text release

Status: agreed storage direction with recommended implementation defaults, consolidated 2026-10-19. **Not implemented.** Active memory stays in PostgreSQL with application-encrypted content. This document owns the proposed memory behavior; [SCHEMA_REVIEW](SCHEMA_REVIEW.md) describes current defects and [DEVELOPMENT_PLAN](DEVELOPMENT_PLAN.md) owns execution order.

## 1. Storage and scope

Use the existing database as the source of truth. Do not move active memories into S3 files or introduce a vector store for this release. Redis is for short-lived coordination/limits, not authoritative memory. S3 can be evaluated later for explicitly requested exports/archives; it is not a dependency of memory retrieval. Database backups remain an operational requirement and have their own retention policy.

Keep the backend as one application with internal modules. Use a PostgreSQL-backed extraction job table initially. A separate worker deployment, SQS, or an outbox becomes an explicit later change if workload or operational evidence justifies it.

## 2. Four context layers

| Layer | Source of truth | Write rule | Read rule |
| --- | --- | --- | --- |
| Confirmed profile | `therapy_profiles` | User edits and completed onboarding | Include only fields needed for the current response |
| Recent conversation | Ordered `messages` for the current conversation | Persist user turns and successful/explicitly partial assistant output with generation status | Bounded recent turns; exclude failed output and content suppressed from future AI use |
| Long-term memories | `long_term_memories` with lifecycle/provenance | Manual save or validated extraction candidate followed by confirmation | Only confirmed, active, relevant, unsuppressed items owned by the current user |
| Session summaries | Summary attached to `conversations`, produced by durable extraction | Successful validated job for a particular transcript/context revision | Recent relevant summaries that are still valid after edits/forget operations |

A running summary within a session is optional, added only if bounded recent turns prove insufficient. It must follow the same revision/invalidation rules as final summaries. Do not turn one ever-growing profile or summary into the sole memory store.

## 3. Profile, persona, and inferred observations

User-confirmed facts win over AI inference. Extraction must not silently change preferred name, age band, interests, or other manually edited fields. Return proposed changes for confirmation or store them as pending memory candidates.

Reconcile the existing onboarding fields in Steps 05–06: canonical camelCase DTOs; arrays for `topicsOfConcern` and `interests`; map interests to the existing `enjoyments` storage; support existing gender/country inputs if retained; add encrypted `additional_note` for the visible note field. Reuse the V2 onboarding-completion field and selected-therapist storage through one canonical `personaId` contract. A server catalog maps persona IDs to approved prompt behavior; communication style remains separate. Maintain compatibility aliases only for the old frontend's transition, with tests and a removal date.

Completion must be validated explicitly, not inferred from the default persona or profile existence. Preserve current user UUIDs and existing encrypted values. Unknown legacy onboarding data remains unknown; do not invent it during backfill.

For newly inferred personality information, prefer evidence-based observations over permanent labels. Example: “User said exams have been stressful lately,” not “User is an anxious person.” Preserve old trait records during migration, but do not automatically treat them as confirmed facts. Keep existing responses compatible until frontend integration; do not present model confidence as a calibrated psychological score.

## 4. Memory lifecycle and minimal fields

Retain the existing memory identity/owner, type, encrypted title/detail, importance, and dates. Add only fields needed for these behaviors:

- `status`: `pending`, `active`, `resolved`, `superseded`, or `expired`. Forgotten content is removed through the forget workflow, not kept indefinitely under a status flag.
- `source_kind`: user-saved, user-stated extraction, or AI-inferred observation; distinguish origin from user confirmation.
- Evidence: source conversation/message references and extraction job/revision identity. If one memory needs multiple evidence records, use a small linking table rather than a list hidden in prose.
- `confirmed_at`, `updated_at`, optional `expires_at`, and a version for optimistic concurrency. `occurred_at` is nullable when the actual event date is unknown; it is not the extraction timestamp.
- A replacement link when a new memory supersedes an old one; keep deletion behavior explicit for that link.

Use the current `is_resolved` boolean only as a compatibility projection during rollout; do not maintain two independent lifecycle authorities. Define one memory-type vocabulary across the prompt, DTOs, service validation, and SQL. Keep sensitive content encrypted as TEXT. Avoid duplicating encrypted memory content into an unencrypted search column.

Recommended first-release writes:

1. A user explicitly saves a memory: validate and store it as active/confirmed.
2. Extraction identifies a potentially useful fact, goal, preference, or event: save a pending candidate with evidence.
3. The UI offers concise grouped review: confirm, edit, or dismiss. Pending candidates are not inserted into future prompts as facts. Configure bounded candidate retention/cleanup.
4. A user corrects a memory: update with version checking or create an explicit replacement; invalidate derived summaries where necessary.
5. A goal/event resolves or expires: stop ordinary retrieval. Reopening requires a deliberate state change.

Do not deduplicate by title equality. Two different events can have the same title, and the same fact can have different titles. Ensure retries are idempotent using job/output identity. Potential semantic duplicates can be proposed for merging; do not automatically overwrite a confirmed memory based on an AI guess.

## 5. Retrieval without vector search

For each request:

1. Resolve the authenticated internal user and verify conversation ownership before retrieving context.
2. Load the relevant confirmed profile, ordered recent turns, and valid summaries. Exclude suppressed source material and stale summary revisions.
3. Query a bounded per-user set of confirmed active memories using lifecycle, expiry, due date, type, recency, and importance. Apply owner filtering in the database before decrypting content.
4. Rank that bounded set for the current topic in application memory, using a simple normalized keyword/token-overlap baseline plus recency, relevant unresolved goals, due follow-ups, and importance. Give topic relevance priority over a high importance score alone. Avoid storing extracted sensitive keywords as plaintext indexes by default.
5. Select only items that add useful context within the assembled-input token budget. Do not force a fixed number of memories when none are relevant. Record selected IDs/policy versions for debugging, not decrypted contents in logs.

Set candidate count and ranking weights in validated configuration; begin with a modest bounded set such as 50 candidates and tune against evaluation results. This can miss older relevant memories and is not a semantic-search guarantee. Measure those misses rather than assuming the baseline is sufficient forever.

Treat profile/memory/transcript content as untrusted data, not instructions that can override system policy. Retrieval should not make every reply repeat a user's old concern or introduce a private fact unrelated to the current conversation.

## 6. Forget, correct, and reset semantics

Deleting a memory row alone is insufficient when old messages, summaries, pending jobs, or caches can reintroduce it. Define “forget” as removal from future AI context and derived memory, with a clear UI explanation of whether the original chat history remains visible.

Recommended initial implementation:

- Remove the selected memory content and related pending duplicates/evidence as appropriate; retain only minimal non-content suppression metadata required to prevent reuse of its source.
- Mark supporting source messages as excluded from future AI context and extraction. If safe message-level provenance is unavailable, conservatively exclude the source conversation rather than claiming fine-grained forgetting.
- Invalidate affected stored/running summaries; regenerate only from eligible input or omit them. Remove affected candidates and cached context.
- Increment the user's context revision. Jobs capture that revision and source range; workers recheck it transactionally before persisting results. A stale worker cannot write after a forget/reset just because it started earlier.
- Profile/memory reset advances a reset boundary that prevents older transcripts from rebuilding the removed profile/memories automatically. Account deletion cancels work and removes dependent content according to the agreed retention policy.

This controls known source-derived information. Without retaining the forgotten content, the system cannot promise semantic detection of every future paraphrase or duplicate fact from unrelated sources. Do not claim otherwise. Newly supplied information can be offered as a new pending candidate, requiring explicit confirmation. Explain that already-sent model requests cannot be retracted; cancel active work where possible and apply the new revision to subsequent requests.

Restore procedures must respect deletion/reset records since a backup was taken, or they can resurrect removed data. Define retention and replay of minimal deletion metadata separately from erased content. Avoid an indefinite plaintext list of forgotten facts.

## 7. Extraction and transactions

In the transaction that completes a session, create a deduplicated extraction job with source range/revision. A worker claims the job, assembles eligible input, reserves usage, and calls Bedrock outside the database transaction. Validate output types, lengths, ranges, dates, provenance, and candidate limits before writing.

In a final transaction, recheck ownership/existence/context revision, persist the validated summary/candidates, and mark the job successful. Failed or stale results cannot set `summarized_at` as if processing succeeded. Use bounded retries, lease expiry, and safe replay. Keep actual provider-attempt cost even if the result is discarded after a concurrent reset.

No AI operation bypasses the shared budget boundary. A cheap extraction model is a candidate, not permission to relax accuracy or let extraction silently overwrite user-confirmed information.

## 8. Acceptance and later evolution

Required tests span Steps 06, 09–11, 13, and 19–20:

- Profile/onboarding round-trip and confirmed facts surviving contradictory extraction.
- Pending candidates excluded from prompts; manual saves and confirmations included when relevant.
- Different events sharing a title remain distinct; retrying the same extraction produces no duplicates.
- Topic changes retrieve appropriate memories; irrelevant high-importance memories are omitted.
- Resolved/expired/superseded memories are excluded, and uncertain dates remain unknown.
- Forget/reset racing a worker or stream cannot restore the removed context in later requests.
- Cross-user retrieval, unauthorized edits, and stale-version updates fail safely.
- Evidence survives valid updates, and affected summaries/caches are invalidated on correction/deletion.
- Retrieval relevance, unwanted recall, extraction factuality, response quality, latency, and token cost are evaluated on synthetic sessions with expected results and human review.

Add embeddings only if measured retrieval misses justify them. PostgreSQL vector support can be evaluated then; embeddings are a derived sensitive representation with its own authorization, deletion, and rebuild rules. They do not replace the canonical encrypted rows or solve lifecycle/provenance problems. Active S3 memory files, a separate vector database, and managed memory services are not first-release requirements.
