# Step 06 — Profile and memory reconciliation

Status: **implementation in progress**. Java tests pass, but the PostgreSQL migration and persistence gate has not run because Docker is unavailable on this host. Do not treat the new routes as production verified yet.

## Implemented

- Added additive Flyway V6: encrypted memory titles use `TEXT`; user context revision, conversation eligibility and summary revision, profile note, and memory lifecycle/provenance/version columns are present. Legacy extracted memories become pending candidates. Legacy manual memories remain confirmed.
- Profile reads may create the default row inside a writable transaction. Canonical onboarding fields and the existing frontend's `aiPersona`, `topics_of_concern`, and `enjoyments` aliases map to one profile. `enjoyments` reads legacy plaintext JSON arrays and encrypts new writes. Approved personas are `dr_sarah` and `dr_alex`. Completion requires name, age range, gender, country, and approved persona.
- Manual memories are confirmed on creation. AI extracted memories are pending until explicit confirmation. Candidate review, confirm, dismiss, memory edit/resolve, forget, and context reset routes are implemented with owner checks and quoted `If-Match` versions. Prompt retrieval excludes pending, expired, and source-ineligible memories.
- Forget advances the user's context revision, suppresses a known source conversation and its other memories, and invalidates summaries. If provenance is missing, it conservatively excludes all prior conversations. Context reset deletes profile, traits, and memories while excluding all prior conversations. Existing `DELETE /memories/{id}` uses the forget behavior but keeps its 204 response.
- Summarization checks the revision again after the provider call. It discards stale output, creates pending candidates with deterministic output IDs, and leaves failed extraction unsummarized. Inferred traits/profile fields no longer override user confirmed profile data.
- The legacy mid-session refresh-profile route remains an ownership-checked success response but no longer makes an AI call or writes inferred profile fields.

## Validation

- `bash ./mvnw -q test` passed: 52 tests reported, zero failures/errors, three PostgreSQL/Redis Testcontainers checks skipped because `/var/run/docker.sock` is absent.
- The integration test now expects six migrations, checks a long encrypted title, and exercises a V5-to-V6 upgrade with legacy data, but those fixtures have not executed here.
- OpenAPI YAML parses. The Step 06 operations are labeled `in-progress-step-06` until the database gate passes.

## Required before closing Step 06

Run the Testcontainers suite on a host with Docker, including both empty and V5-to-V6 upgrade paths. Verify Hibernate schema validation and encrypted fixture round trips against PostgreSQL, then run owner/pagination/forget HTTP integration tests against PostgreSQL and Redis. If a migration issue appears, add V7; do not edit an applied migration.

No AWS account, credentials, or services are needed for Step 06. Bedrock inputs will be requested for Step 09 after quota controls are in place.
