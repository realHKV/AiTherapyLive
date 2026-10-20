# Step 04 — framework and dependency checkpoints

Status: **in progress**. Target implementation is Spring Boot 4.1.1 on Java 21. The database/schema and vulnerability-scan exit gates are still open.

## Outcome

- Upgraded the existing suite on 3.4.4, then committed passing 3.5.16 (`2218e61`), 4.0.8 (`6716cea`), and 4.1.1 (`1091d87`) checkpoints. These commits are independently reviewable.
- Replaced renamed web MVC and OAuth client starters; added the Flyway, Spring Session Data Redis, MVC test, Jackson test, and Security test starters. Replaced Testcontainers 1.x artifact names with their 2.x equivalents.
- Migrated application JSON code from Jackson 2 to Jackson 3 (`tools.jackson`), retaining `com.fasterxml.jackson.annotation` for annotations as required by Jackson 3. `spring.jackson.use-jackson2-defaults=true` preserves the prior API JSON defaults. Updated the Spring Session Redis namespace property path to `spring.session.data.redis.namespace`; its value remains `aitherapy:oauth-session`.
- Updated JJWT to 0.13.0. Its Jackson 2 adapter remains a **runtime-only** dependency; application and HTTP serialization use Jackson 3. No Jackson 2 bridge or explicit `jackson-databind` override remains.
- Added signed JWT and asynchronous session-event dispatch tests. No applied Flyway SQL file changed. The Dockerfile still uses Java 21; image build and runtime validation belong to Step 14.

## Dependency inventory

From `./mvnw dependency:tree` at the 4.1.1 target:

| Component | Resolved version | Scope or reason |
| --- | --- | --- |
| Spring Boot starters | 4.1.1 | Parent-managed |
| Spring Security | 7.1.1 | Parent-managed |
| Hibernate ORM | 7.4.5.Final | Parent-managed |
| Flyway core/PostgreSQL | 12.4.0 | Parent-managed |
| PostgreSQL driver | 42.7.13 | Runtime, parent-managed |
| Spring Session Data Redis | 4.1.1 | Parent-managed |
| Jackson 3 databind | 3.1.5 | Parent-managed application JSON |
| Jackson 2 databind | 2.21.5 | Runtime transitive of JJWT adapter |
| JJWT API/implementation/adapter | 0.13.0 | Explicit `${jjwt.version}` override; latest published JJWT release reviewed for this step |
| Testcontainers | 2.0.5 | Test, parent-managed |
| Embedded Tomcat | 11.0.24 | Parent-managed |

The Maven dependency tree has 178 lines including transitives. JJWT is the only explicit library version override in `pom.xml`; Java remains pinned to 21. There is no unsupported override known from compilation, but real runtime integration remains unverified.

## Validation and scan

| Check | Result |
| --- | --- |
| `bash ./mvnw test -q` on 3.4.4 | Passed: 42 tests reported, 2 Testcontainers tests skipped |
| `bash ./mvnw test -q` on 3.5.16 | Passed: 42 reported, 2 skipped |
| `bash ./mvnw test -q` on 4.0.8 | Passed: 42 reported, 2 skipped |
| `bash ./mvnw test -q` on final 4.1.1 | Passed: 44 reported, 2 skipped, no failures/errors |
| Synthetic ciphertext and JSON envelope tests | Passed; JSON test checks boolean and ISO timestamp |
| Signed JWT and async event dispatch | Passed with newly added regression tests |
| PostgreSQL Flyway migration, encrypted column persistence, Redis reachability | **Not run**: Testcontainers skipped because `/var/run/docker.sock` is absent |
| Hibernate `ddl-auto=validate` against migrated PostgreSQL | **Not run**: no local database available; must be exercised with the container stack |
| Dependency vulnerability scan | **Not completed**: OWASP Dependency-Check 13.0.0 failed during NVD data initialization (`Invalid API Key, length of 0`; `NoDataException`). It produced no findings report, so this is not a clean scan. |
| `bash ./mvnw package -DskipTests -q` | Passed; Boot 4.1.1 executable JAR created |

The dependency inventory is a resolved-tree report, **not** a vulnerability scan. The scan command attempted was `timeout 180s bash ./mvnw org.owasp:dependency-check-maven:13.0.0:check -DfailBuildOnCVSS=0 -q`; it exited 1 before analysis, with no usable NVD data. Do not treat a passing unit suite as schema compatibility evidence.

## Compatibility and rollout

Existing `/api/v1` routes and data formats are intended to remain stable; the HTTP serialization test confirms the main response envelope, while a full live-client contract test remains for Step 13. The OAuth session namespace string remains the same after its configuration path change. Existing JWTs use the same RSA key and JJWT parser path; the new signed-token test covers generation and verification with synthetic keys. Database migrations and encrypted values are unchanged.

Deploy the new runtime only after a migrated PostgreSQL/Redis environment passes the schema and OAuth-session checks. For rollback, revert the application artifact to the last validated version; no database rollback is needed for Step 04 because this step adds no migration. A rollback between Boot 4 and Boot 3 must retain compatible external session data and unchanged signing/encryption keys.

## Outstanding work

1. Run the Testcontainers integration tests and a full application startup against migrated PostgreSQL/Redis with Docker available. Confirm Hibernate validation, Flyway, OAuth session persistence, and encrypted field reads.
2. Complete and review a vulnerability scan report; triage any findings without suppressing them silently.
3. Continue Step 05 contract specification in parallel with these external gates. Step 04 remains in progress until they pass.
