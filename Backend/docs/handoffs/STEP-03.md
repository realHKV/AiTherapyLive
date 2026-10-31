# Step 03 handoff — purchase ownership and entitlements

Status: **in progress**. PostgreSQL migration and simultaneous capture/replay checks passed in Step 13 GitHub Actions run [39581071817](https://github.com/realHKV/AiTherapyLive/actions/runs/39581071817). Razorpay test-mode Checkout, provider fetch, and webhook validation still require account credentials and are not complete.

## Outcome and files

- Added Flyway V5 `purchases`, with a server-owned user, unique Razorpay order ID, exact expected amount/currency, capture state, unique captured payment ID, and state consistency constraints. Existing V1–V4 migrations are unchanged.
- Added authenticated `POST /payment/purchases`. It creates a Razorpay order for exactly 5000 paise in INR, stores the owner/order as a purchase, and returns `{purchaseId,orderId,keyId,amountPaise,currency}` for a future Checkout client. The price remains ₹50; it was not changed.
- `POST /payment/verify` now fetches the payment from Razorpay and accepts it only if captured and attached to a purchase owned by the authenticated user, with exact amount and currency. A payment from the existing shared link has no server-owned purchase and cannot grant PRO.
- The webhook still authenticates the exact raw body with HMAC-SHA256 before parsing. Capture uses the signed order ID, never `notes.userId`. The shared capture service locks the user and purchase rows, records the unique payment, marks the purchase captured, and grants entitlement in one transaction. Duplicate delivery of the same capture does not extend the term. Unknown orders return a retryable response.
- A paid renewal extends 30 days from the later of now or a still-active PRO expiry. A single `EntitlementService` resolves effective tier for both the status route and chat routing. Expired PRO is effective FREE without waiting for the daily downgrade job.
- Razorpay HTTP requests have connection/request deadlines, validate provider fields, and do not include provider response bodies or credentials in errors. No refund or recurring-billing behavior was added.

## Checks actually run

- `bash ./mvnw test -q` passed: 42 tests reported, 40 passed, 2 skipped. Tests cover wrong-user claim, low amount, wrong currency, uncaptured status, duplicate capture, previously claimed payment, renewal extension, expired entitlement, authenticated purchase creation, and signed raw-body tampering.
- `git diff --check` for Step 03 files passed. The two skipped Testcontainers tests need Docker.

## Checks not run and rollout

- V5 migration and Hibernate validation ran in CI on PostgreSQL. Concurrent callbacks for one order grant one subscription period; reusing one payment ID on a second purchase is rejected without granting the second account. These database gates passed in Step 13.
- Exercise Razorpay test-mode order creation, Checkout, manual fetch, webhook delivery/retry, and duplicate event behavior. Provider API shape was checked against [Razorpay's server-side order guidance](https://razorpay.com/docs/server-integration/python/test-app/) but not invoked with account credentials.
- The current frontend opens a shared fixed payment link and cannot use the new order-based flow. Do not deploy this backend payment change for paid traffic until the Phase F frontend creates purchases and starts Checkout with the returned order ID. Existing payments already captured before V5 are not retroactively linked or re-granted.
- Reverting the code after V5 is applied leaves the additive `purchases` table; do not edit or remove the applied migration. Rolling back a payment grant requires an explicit account/payment reconciliation, not just a code rollback. Current account deletion cascades payment and purchase rows; financial retention policy remains a separate product/legal decision.

## Next work

Complete the Razorpay test-mode fixture when account access is available. Frontend order-based Checkout belongs to Phase F.
