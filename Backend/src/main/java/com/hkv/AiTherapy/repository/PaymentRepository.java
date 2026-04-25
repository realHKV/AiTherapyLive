package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Idempotency guard — prevent double-upgrading from repeated webhooks. */
    boolean existsByRazorpayPaymentId(String razorpayPaymentId);
}
