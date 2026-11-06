CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    razorpay_order_id VARCHAR(100) NOT NULL UNIQUE,
    expected_amount_paise INTEGER NOT NULL CHECK (expected_amount_paise > 0),
    currency VARCHAR(5) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CAPTURED')),
    razorpay_payment_id VARCHAR(100) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    captured_at TIMESTAMPTZ,
    CONSTRAINT purchases_capture_consistency CHECK (
        (status = 'PENDING' AND razorpay_payment_id IS NULL AND captured_at IS NULL) OR
        (status = 'CAPTURED' AND razorpay_payment_id IS NOT NULL AND captured_at IS NOT NULL)
    )
);

CREATE INDEX idx_purchases_user_created ON purchases(user_id, created_at DESC);
