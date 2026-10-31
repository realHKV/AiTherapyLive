# Step 00 handoff — local baseline

Status: **in progress**. The Docker integration gate is pending because this host has no running Docker daemon (`/var/run/docker.sock` is absent). Do not advance the tracker to complete on the basis of skipped tests.

Implementation commit after the approved history cleanup: `7c0cc58` on `codex/backend-baseline`. The user confirmed the repository, and the branch push reported success.

## Outcome and files

- Recorded the initial baseline: two existing mock-based tests passed under Java 21 and Maven 3.9.14. The wrapper script lacks execute permission in Git, so documented commands invoke it with `bash`. The sandbox initially prevented Maven cache access; the normal Maven run succeeded when granted local cache access.
- Added `compose.yaml` for loopback-only PostgreSQL 16 and Redis 7, `config/local.env.example` for local configuration, and updated `run-with-env.sh` to load it from `.env`, check key settings, and start Maven from `Backend`.
- Added Testcontainers dependencies and `InfrastructureBaselineTest` for Flyway V1–V4, an encrypted PostgreSQL field, and Redis connectivity. Container tests skip when Docker is absent. Added synthetic encryption and API JSON characterization tests.
- Set `spring.jackson.serialization.write-dates-as-timestamps=false` so API `Instant` fields serialize as ISO strings. Made encryption UTF-8 explicit and report malformed Base64 keys with a clear startup error. Existing ciphertext format is unchanged.
- Removed the unused, comment-only `JacksonConfig.java`, which contained a token-bearing example, without printing its contents. The local line-ending edit to that file had no runtime behavior. The three approved GitHub branches were subsequently rewritten to remove that path from their reachable histories. GitHub caches, pull request refs, local objects, and clones may retain historical copies; revoke any credential that was real and remains usable.

## Checks actually run

- Before changes: `bash ./mvnw test -q` passed the two existing tests once Maven cache access was available.
- After changes: `bash ./mvnw test -q` passed (9 tests reported, 2 skipped). The JSON test initially exposed numeric timestamp output; configuration was corrected and the rerun passed.
- `docker info --format '{{.ServerVersion}}'` failed because the Docker socket does not exist. The PostgreSQL/Redis Testcontainers tests were therefore skipped.

## Checks not run

- `docker compose up -d --wait`, real Flyway migration, encrypted PostgreSQL read/write, Redis ping, and full application startup: Docker daemon unavailable. A Docker-capable runner must execute these before Step 00 can close.
- No live AI, OAuth, payment, AWS, or deployment check is part of this baseline.

## Compatibility, rollout, rollback

- No API paths or database migrations changed. ISO timestamp output is an intentional serialization contract; clients should parse `Instant` as ISO-8601. UTF-8 encryption preserves the stored AES-GCM wire format and makes non-ASCII behavior deterministic.
- The Compose passwords are local fixtures only. Deployment settings are unchanged. Reverting this patch removes the local stack/tests; changing an existing production encryption key is not a rollback option.

## Outstanding and next step

Run the documented Docker-backed tests and application startup on a host with a Docker daemon. Fix any actual migration, JPA validation, or Redis failure found there, then mark Step 00 complete. The removed token-bearing comment must still be treated as potentially exposed in historical copies outside the cleaned branch tips.
