package com.hkv.AiTherapy.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of a captured Razorpay payment.
 * Used for idempotency checks and dispute resolution.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The Razorpay payment ID, e.g. pay_Abc123. Always unique. */
    @Column(name = "razorpay_payment_id", nullable = false, unique = true, length = 100)
    private String razorpayPaymentId;

    /** Razorpay order_id if available (null for payment-link-based flows). */
    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    /** Amount in paise — 5000 = ₹50. */
    @Column(name = "amount_paise", nullable = false)
    private int amountPaise;

    @Column(name = "currency", nullable = false, length = 5)
    @Builder.Default
    private String currency = "INR";

    /** 'CAPTURED', 'FAILED', or 'REFUNDED'. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "CAPTURED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
