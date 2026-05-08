-- ─── V4: Subscription & Payment Tables ───────────────────────────────────────

-- Stores the active subscription record for each user.
-- Separate table so the users table stays clean.
CREATE TABLE user_subscriptions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    tier            VARCHAR(10) NOT NULL DEFAULT 'FREE'   CHECK (tier IN ('FREE','PRO')),
    pro_expires_at  TIMESTAMPTZ NULL,        -- NULL for FREE tier
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_subscriptions_user_id ON user_subscriptions(user_id);
CREATE INDEX idx_user_subscriptions_pro_expires ON user_subscriptions(pro_expires_at) WHERE tier = 'PRO';

-- Stores successful Razorpay payment records for auditing / idempotency.
CREATE TABLE payments (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    razorpay_payment_id    VARCHAR(100) NOT NULL UNIQUE,  -- pay_xxx from Razorpay
    razorpay_order_id      VARCHAR(100),                   -- order_xxx (may be null for link-based payments)
    amount_paise           INTEGER     NOT NULL,           -- 5000 = ₹50
    currency               VARCHAR(5)  NOT NULL DEFAULT 'INR',
    status                 VARCHAR(20) NOT NULL DEFAULT 'CAPTURED' CHECK (status IN ('CAPTURED','FAILED','REFUNDED')),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_user_id ON payments(user_id);
