# Step 06 — Profile and memory reconciliation

Status: **complete for Step 06**. GitHub Actions ran the PostgreSQL and Redis integration suite with Docker: [backend test run](https://github.com/realHKV/AiTherapyLive/actions/runs/39436690647).

## Implemented

- Added additive Flyway V6: encrypted memory titles use `TEXT`; user context revision, conversation eligibility and summary revision, profile note, and memory lifecycle/provenance/version columns are present. Legacy extracted memories become pending candidates. Legacy manual memories remain confirmed.
- Profile reads may create the default row inside a writable transaction. Canonical onboarding fields and the existing frontend's `aiPersona`, `topics_of_concern`, and `enjoyments` aliases map to one profile. `enjoyments` reads legacy plaintext JSON arrays and encrypts new writes. Approved personas are `dr_sarah` and `dr_alex`. Completion requires name, age range, gender, country, and approved persona.
- Manual memories are confirmed on creation. AI extracted memories are pending until explicit confirmation. Candidate review, confirm, dismiss, memory edit/resolve, forget, and context reset routes are implemented with owner checks and quoted `If-Match` versions. Prompt retrieval excludes pending, expired, and source-ineligible memories.
- Forget advances the user's context revision, suppresses a known source conversation and its other memories, and invalidates summaries. If provenance is missing, it conservatively excludes all prior conversations. Context reset deletes profile, traits, and memories while excluding all prior conversations. Existing `DELETE /memories/{id}` uses the forget behavior but keeps its 204 response.
- Summarization checks the revision again after the provider call. It discards stale output, creates pending candidates with deterministic output IDs, and leaves failed extraction unsummarized. Inferred traits/profile fields no longer override user confirmed profile data.
- The legacy mid-session refresh-profile route remains an ownership-checked success response but no longer makes an AI call or writes inferred profile fields.

## Validation

- Local `bash ./mvnw -q test` passed; container tests skipped because `/var/run/docker.sock` is absent.
- GitHub Actions ran 53 tests with zero failures, errors, or skips. Flyway V6 passed on an empty schema and a V5 upgrade fixture. A long encrypted title persisted and decrypted. Full Spring startup passed Hibernate schema validation, and the persistence test exercised profile creation/update/reset, memory edit/forget/pagination, owner checks, version conflicts, and summary suppression.
- OpenAPI YAML parses. Step 06 operations are marked current after the database gate passed.

## Follow-up

Step 11 will make extraction durable and retryable. Step 13 will exercise the final HTTP envelope and route contract against PostgreSQL/Redis. The full application test also exposed a preexisting password encoder dependency cycle; its bean was moved to an independent configuration before this step was closed.

No AWS account, credentials, or services are needed for Step 06. Bedrock inputs will be requested for Step 09 after quota controls are in place.
