package com.hkv.AiTherapy.service.payment;

import com.hkv.AiTherapy.domain.Payment;
import com.hkv.AiTherapy.domain.Purchase;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.domain.UserSubscription;
import com.hkv.AiTherapy.repository.PaymentRepository;
import com.hkv.AiTherapy.repository.PurchaseRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PaymentCaptureService {
    private final UserRepository users;
    private final PurchaseRepository purchases;
    private final PaymentRepository payments;
    private final UserSubscriptionRepository subscriptions;

    public PaymentCaptureService(UserRepository users, PurchaseRepository purchases,
                                 PaymentRepository payments, UserSubscriptionRepository subscriptions) {
        this.users = users;
        this.purchases = purchases;
        this.payments = payments;
        this.subscriptions = subscriptions;
    }

    /** One user lock serializes all of their captures and renewal expiry changes. */
    @Transactional
    public UUID capture(RazorpayClient.PaymentDetails details, UUID expectedUserId) {
        if (details == null || details.orderId() == null || details.orderId().isBlank()) {
            throw new IllegalArgumentException("Payment is not linked to a purchase");
        }
        Purchase reference = purchases.findByRazorpayOrderId(details.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown purchase"));
        UUID ownerId = reference.getUser().getId();
        if (expectedUserId != null && !expectedUserId.equals(ownerId)) {
            throw new IllegalArgumentException("Payment belongs to another account");
        }
        User user = users.findByIdForUpdate(ownerId).filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        Purchase purchase = purchases.findByOrderIdForUpdate(details.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown purchase"));
        if (!ownerId.equals(purchase.getUser().getId())) {
            throw new IllegalArgumentException("Purchase owner changed");
        }

        if (!"captured".equalsIgnoreCase(details.status())
                || details.amountPaise() != purchase.getExpectedAmountPaise()
                || !purchase.getCurrency().equals(details.currency())
                || details.id() == null || !details.id().matches("pay_[A-Za-z0-9]{6,90}")) {
            throw new IllegalArgumentException("Payment does not match the purchase");
        }
        if ("CAPTURED".equals(purchase.getStatus())) {
            if (!details.id().equals(purchase.getRazorpayPaymentId())) {
                throw new IllegalArgumentException("Purchase already captured by another payment");
            }
            return ownerId;
        }
        if (payments.findByRazorpayPaymentId(details.id()).isPresent()) {
            throw new IllegalArgumentException("Payment already belongs to another purchase");
        }

        Payment payment = Payment.builder().user(user).razorpayPaymentId(details.id())
                .razorpayOrderId(details.orderId()).amountPaise(details.amountPaise())
                .currency(details.currency()).status("CAPTURED").build();
        payments.saveAndFlush(payment);

        Instant now = Instant.now();
        UserSubscription sub = subscriptions.findByUserId(ownerId)
                .orElseGet(() -> UserSubscription.builder().user(user).tier("FREE").build());
        Instant start = "PRO".equals(sub.getTier()) && sub.getProExpiresAt() != null
                && sub.getProExpiresAt().isAfter(now) ? sub.getProExpiresAt() : now;
        sub.setTier("PRO");
        sub.setProExpiresAt(start.plus(30, ChronoUnit.DAYS));
        subscriptions.save(sub);

        purchase.setStatus("CAPTURED");
        purchase.setRazorpayPaymentId(details.id());
        purchase.setCapturedAt(now);
        purchases.save(purchase);
        return ownerId;
    }
}
