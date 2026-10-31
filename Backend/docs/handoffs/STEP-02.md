# Step 02 handoff — authentication lifecycle and deletion

Status: **in progress**. Local implementation is reviewable, but the Step 02 exit gate is not yet met: this host has no Docker daemon for concurrent PostgreSQL/Redis tests, and the Google callback has not been exercised against an identity-provider fixture across two service instances.

## Outcome and files

- Refresh rotation now locks the refresh-token row with `PESSIMISTIC_WRITE` and revokes the consumed token before issuing its replacement in one transaction. Disabled accounts cannot refresh. The JWT filter rechecks account existence and active status on every authenticated request; Redis failure rejects access.
- Logout checks token ownership when both token types are supplied. It writes the access denylist entry before committing refresh revocation; Redis failure returns 503. A retry can revoke the still-valid refresh token. A client that discards its token despite a 503 can no longer retry with it, so the Phase F client should handle this response deliberately.
- Added `OAUTH_TICKET_HANDOFF` (default `false`). The current frontend keeps the legacy Google callback. When enabled, the callback sends a random 60-second ticket; `POST /auth/oauth/exchange` atomically consumes it in Redis and returns the existing AuthResponse shape with both token types. Replay and inactive-account exchange fail. Redis-backed HTTP sessions carry Google authorization state across instances; JWT authentication itself is never saved in the session. Verified Google email and subject are required.
- Account deletion and summary persistence lock the same user row. The summary job rechecks account existence after provider work and discards results for a deleted/disabled account. Future durable jobs must use this same final transaction guard. Access tokens for a deleted account fail their next request because the user no longer exists.
- No Flyway migration or frontend file changed. Added the Spring Session Redis module to the existing Spring Boot dependency set. `spring.session.redis.namespace=aitherapy:oauth-session` and a five-minute servlet session timeout are configured.

## Checks actually run

- `bash ./mvnw -q -Dtest=SecurityHttpTest,TokenLoggingTest,OAuthTicketServiceTest,TokenServiceTest test` passed. The focused tests cover single-use tickets, disabled ticket exchange, revoked refresh replay, Redis logout failure, disabled-account access, and Redis fail-closed access.
- `bash ./mvnw test -q` passed: 33 reported, 31 passed, 2 skipped. The two skipped tests require Docker for PostgreSQL and Redis. Existing OAuth handler tests exercise both callback modes with synthetic identities, but not a complete provider redirect round trip.

## Pending exit gates and rollout

- Run real concurrent refresh requests against PostgreSQL and assert exactly one replacement chain. Exercise callback state on two app instances with a synthetic OAuth provider and test callback/ticket replay. Test Redis loss during authorization and exchange. A real Google staging check remains Step 16.
- Keep `OAUTH_TICKET_HANDOFF=false` until the Phase F frontend can exchange tickets and remove them from browser history. When the flag is enabled, old frontend Google login will not understand the callback. Deploy Spring Session Redis with a shared Redis namespace/connection before scaling OAuth callbacks across instances.
- Account deletion does not stop already running chat generation; Step 10 must check account state before final chat persistence and billing settlement. The current summary guard prevents its post-deletion persistence, but a Docker-backed deletion/summary race test is still needed.
- A leaked legacy redirect JWT remains usable until expiry; turning on ticket handoff is a later coordinated frontend rollout. Redis/session keys are ephemeral and need no database migration. Rolling back this patch removes the ticket route and Redis session support, so do not enable the new frontend flow before the backend is deployed.

## Next work

Complete the database concurrency and multi-instance OAuth fixture checks on a Docker-capable runner, then close this step. The independent Step 03 payment work can proceed while these external test gates remain visible.
