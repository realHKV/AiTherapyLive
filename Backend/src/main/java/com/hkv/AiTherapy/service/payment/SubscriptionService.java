package com.hkv.AiTherapy.service.payment;

import com.hkv.AiTherapy.domain.Purchase;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.domain.UserSubscription;
import com.hkv.AiTherapy.dto.response.PurchaseResponse;
import com.hkv.AiTherapy.dto.response.SubscriptionStatusResponse;
import com.hkv.AiTherapy.repository.PurchaseRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class SubscriptionService {
    public static final int PRICE_PAISE = 5000;
    public static final String CURRENCY = "INR";

    private final UserRepository users;
    private final UserSubscriptionRepository subscriptions;
    private final PurchaseRepository purchases;
    private final RazorpayClient razorpay;
    private final PaymentCaptureService capture;
    private final EntitlementService entitlements;

    @Value("${razorpay.key-id:}") private String razorpayKeyId;

    public SubscriptionService(UserRepository users, UserSubscriptionRepository subscriptions,
                               PurchaseRepository purchases, RazorpayClient razorpay,
                               PaymentCaptureService capture, EntitlementService entitlements) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.purchases = purchases;
        this.razorpay = razorpay;
        this.capture = capture;
        this.entitlements = entitlements;
    }

    public SubscriptionStatusResponse getStatus(UUID userId) {
        return entitlements.status(userId);
    }

    /** Create a provider order before exposing its ID to the browser checkout. */
    public PurchaseResponse createPurchase(UUID userId) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        UUID purchaseId = UUID.randomUUID();
        String orderId = razorpay.createOrder(purchaseId, PRICE_PAISE, CURRENCY);
        Purchase purchase = Purchase.builder().id(purchaseId).user(user)
                .razorpayOrderId(orderId).expectedAmountPaise(PRICE_PAISE)
                .currency(CURRENCY).status("PENDING").build();
        purchases.save(purchase);
        return new PurchaseResponse(purchaseId.toString(), orderId, razorpayKeyId, PRICE_PAISE, CURRENCY);
    }

    /** The old payment-ID endpoint now requires a server-owned order. */
    public SubscriptionStatusResponse verifyAndUpgrade(UUID userId, String paymentId) {
        RazorpayClient.PaymentDetails details = razorpay.fetchPayment(paymentId);
        capture.capture(details, userId);
        return entitlements.status(userId);
    }

    public void handleWebhookCapture(String paymentId, String orderId, int amountPaise, String currency) {
        capture.capture(new RazorpayClient.PaymentDetails(paymentId, orderId, amountPaise, currency, "captured"), null);
    }

    @Transactional
    public int downgradeExpiredUsers() {
        return subscriptions.downgradeExpired(Instant.now());
    }

    @Transactional
    public SubscriptionStatusResponse devForceUpgrade(UUID userId) {
        User user = users.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        UserSubscription sub = subscriptions.findByUserId(userId)
                .orElseGet(() -> UserSubscription.builder().user(user).tier("FREE").build());
        sub.setTier("PRO");
        sub.setProExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptions.save(sub);
        return entitlements.status(userId);
    }

    @Transactional
    public SubscriptionStatusResponse devForceDowngrade(UUID userId) {
        User user = users.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        UserSubscription sub = subscriptions.findByUserId(userId)
                .orElseGet(() -> UserSubscription.builder().user(user).tier("FREE").build());
        sub.setTier("FREE");
        sub.setProExpiresAt(null);
        subscriptions.save(sub);
        return entitlements.status(userId);
    }
}
