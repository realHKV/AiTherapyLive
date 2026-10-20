# Step 01 handoff — immediate production exposure

Status: **complete** for the Step 01 exit gate. Commit: `44e921d` on `codex/backend-baseline`; the branch push reported success. Step 00's Docker gate remains pending independently.

## Outcome and files

- `SecurityConfig` now permits only the public POST auth operations, OAuth entry/callback paths, and the POST Razorpay webhook. `DELETE /auth/account` requires authentication. Public login, registration, refresh, and logout keep their existing paths.
- `DevController` is registered only when `app.dev-mode=true` (default false). Normal-mode HTTP requests to `/dev/force-pro` return 404, including requests with a valid JWT.
- `OAuth2SuccessHandler`, `JwtTokenProvider`, and `JwtAuthenticationFilter` no longer print tokens, authentication objects, or validation exceptions. The older token-bearing source example was removed in Step 00; its historical commits still need separate treatment.
- `application.yaml` sets Spring Security logs to WARN and disables framework error messages, binding details, and stack traces in HTTP errors. `AuthController` sends fixed client messages rather than raw exception text; account-deletion failures return 500.
- `PaymentController` requires a configured webhook secret in every mode, verifies the raw body signature before processing, and compares signature bytes in constant time. Missing secret returns 503; invalid signature returns 400. Payment ownership and replay correctness remain Step 03 work.
- Cross-user conversation and memory operations return 404 without disclosing whether the resource exists. `BACKEND_REFERENCE.md` reflects these changes.
- No migration or new environment variable was introduced. `DEV_MODE` still defaults false; `RAZORPAY_WEBHOOK_SECRET` must be configured for webhook delivery.

## Checks actually run

- `bash ./mvnw -q -Dtest=SecurityHttpTest,TokenLoggingTest,ChatServiceTest,MemoryServiceTest test` passed.
- `bash ./mvnw test -q` passed: 23 tests reported, 21 passed, 2 skipped. Tests cover anonymous deletion, authenticated deletion identity, normal-mode development route absence, public login/refresh, authenticated chat, signed/unsigned webhook behavior, safe auth error text, captured token logs, and cross-user chat/memory rejection.

## Checks not run

- Two PostgreSQL/Redis Testcontainers checks remain skipped because this host has no Docker daemon. No real OAuth provider, payment gateway, or staging HTTP smoke test was run.
- Historical Git cleanup and any credential rotation are separate from Step 01 code. A previous attempt to rewrite all local refs was rejected by automatic approval review because of its broad irreversible scope. Do not consider the old Git objects erased.

## Compatibility, rollout, rollback

- Existing anonymous login/register/refresh/logout and signed webhook paths remain public. Account deletion now requires a JWT. Development upgrade paths are absent unless explicitly enabled. Clients that displayed raw server exception messages will receive fixed messages.
- Deploy with `DEV_MODE=false` and a configured `RAZORPAY_WEBHOOK_SECRET`. Existing unsigned development webhooks will be rejected. OAuth still places the access JWT in its redirect URL for frontend compatibility; Step 02 must replace this with a safer opt-in handoff before the frontend switches.
- Reverting this patch would reopen account deletion and subscription-upgrade exposure. There is no schema rollback.

## Next step

Step 02 may start after this handoff. Step 00's real database/Redis integration gate remains open, and the historical token must be revoked if it is still usable. Coordinate any Git history rewrite with explicit scope approval and a backup of affected refs.
