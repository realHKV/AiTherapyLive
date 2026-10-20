# Server-owned payment purchases

Date: 2026-10-20. Status: implemented locally; integration gate pending.

## Problem and constraints

The shared frontend Razorpay link does not identify the intended account. A captured payment ID could be claimed by whichever user submits it first. The current ₹50 price is retained; recurring billing and refunds are outside this step. Backend implementation precedes frontend integration.

## Decision

Create a backend purchase and Razorpay order for every intended checkout. Store owner, provider order ID, exact 5000-paise INR expectation, and capture state. Manual verification fetches the payment from Razorpay; signed webhooks use the signed payment event. Both call the same transactional capture path, which locks the user and purchase rows and relies on unique payment and order IDs for replay protection. A duplicate of the same captured payment has no further entitlement effect. Effective PRO is evaluated against expiry at request time.

A paid renewal extends from the existing future expiry, or from now if expired, by 30 days. Keep ₹50 until a deliberate product decision changes it. Payments lacking a server-owned order cannot grant PRO. The existing shared link therefore requires a frontend switch to order-based Checkout in Phase F before this backend change can be released for purchases.

## Consequences and validation

An external order can be created even if the subsequent local purchase save fails; it cannot grant entitlement without a purchase row. Webhook events referencing an unknown order return a retryable response. User/account deletion currently cascades purchase and payment rows; financial-record retention still requires a separate product/legal decision.

Unit and HTTP tests cover ownership, exact amount/currency, capture state, replay, renewal, expiry, and signature tampering. PostgreSQL migration and simultaneous webhook/manual delivery must be verified on a Docker-capable runner. Razorpay test-mode Checkout and webhook delivery must be checked before rollout. [Razorpay's server-side order guidance](https://razorpay.com/docs/server-integration/python/test-app/) describes order creation and passing the order ID to Checkout.
