package com.hkv.AiTherapy.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.domain.Payment;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.domain.UserSubscription;
import com.hkv.AiTherapy.dto.response.SubscriptionStatusResponse;
import com.hkv.AiTherapy.repository.PaymentRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    /** ₹50 = 5000 paise */
    private static final int EXPECTED_AMOUNT_PAISE = 5000;

    public SubscriptionService(UserRepository userRepository,
                               UserSubscriptionRepository subscriptionRepository,
                               PaymentRepository paymentRepository,
                               ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the subscription status for a user.
     * Creates a FREE record lazily if none exists.
     */
    @Transactional
    public SubscriptionStatusResponse getStatus(UUID userId) {
        UserSubscription sub = getOrCreateSubscription(userId);
        return toResponse(sub);
    }

    /**
     * Verifies a Razorpay payment ID via the Razorpay Fetch Payment API,
     * then upgrades the user to PRO for 30 days.
     *
     * Used as the manual / dev-mode fallback when keys are available.
     */
    @Transactional
    public SubscriptionStatusResponse verifyAndUpgrade(UUID userId, String paymentId) {
        // 1. Idempotency — don't double-process the same payment
        if (paymentRepository.existsByRazorpayPaymentId(paymentId)) {
            // Already processed — just return current status
            return getStatus(userId);
        }

        // 2. Fetch payment details from Razorpay API
        JsonNode paymentNode = fetchRazorpayPayment(paymentId);

        String status = paymentNode.path("status").asText();
        int amount = paymentNode.path("amount").asInt();
        String currency = paymentNode.path("currency").asText("INR");

        if (!"captured".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Payment not captured. Status: " + status);
        }

        if (amount < EXPECTED_AMOUNT_PAISE) {
            throw new IllegalStateException("Payment amount too low: " + amount + " paise");
        }

        // 3. Record payment
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Payment payment = Payment.builder()
                .user(user)
                .razorpayPaymentId(paymentId)
                .razorpayOrderId(paymentNode.path("order_id").asText(null))
                .amountPaise(amount)
                .currency(currency)
                .status("CAPTURED")
                .build();
        paymentRepository.save(payment);

        // 4. Upgrade subscription
        return upgradeUserToPro(userId);
    }

    /**
     * Called from the webhook handler after signature verification.
     * Upgrades user to PRO for 30 days.
     */
    @Transactional
    public void handleWebhookCapture(String razorpayPaymentId, String razorpayOrderId,
                                     int amountPaise, String currency, UUID userId) {
        if (amountPaise < EXPECTED_AMOUNT_PAISE) {
            throw new IllegalStateException("CRITICAL: Webhook payment amount too low: " + amountPaise + " paise");
        }

        if (paymentRepository.existsByRazorpayPaymentId(razorpayPaymentId)) {
            return; // Idempotent — already processed
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Payment payment = Payment.builder()
                .user(user)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpayOrderId(razorpayOrderId)
                .amountPaise(amountPaise)
                .currency(currency)
                .status("CAPTURED")
                .build();
        paymentRepository.save(payment);

        upgradeUserToPro(userId);
    }

    /**
     * Downgrades all users whose PRO has expired back to FREE.
     * Called by the hourly scheduler.
     */
    @Transactional
    public int downgradeExpiredUsers() {
        return subscriptionRepository.downgradeExpired(Instant.now());
    }

    // ─── Dev-only helpers (only called when app.dev-mode=true) ───────────────

    /** Force-upgrades a user to PRO for 30 days without any payment. */
    @Transactional
    public SubscriptionStatusResponse devForceUpgrade(UUID userId) {
        return upgradeUserToPro(userId);
    }

    /** Force-reverts a user back to FREE. */
    @Transactional
    public SubscriptionStatusResponse devForceDowngrade(UUID userId) {
        UserSubscription sub = getOrCreateSubscription(userId);
        sub.setTier("FREE");
        sub.setProExpiresAt(null);
        subscriptionRepository.save(sub);
        return toResponse(sub);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private SubscriptionStatusResponse upgradeUserToPro(UUID userId) {
        UserSubscription sub = getOrCreateSubscription(userId);
        sub.setTier("PRO");
        sub.setProExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptionRepository.save(sub);
        return toResponse(sub);
    }

    private UserSubscription getOrCreateSubscription(UUID userId) {
        return subscriptionRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            UserSubscription newSub = UserSubscription.builder()
                    .user(user)
                    .tier("FREE")
                    .build();
            return subscriptionRepository.save(newSub);
        });
    }

    private SubscriptionStatusResponse toResponse(UserSubscription sub) {
        return SubscriptionStatusResponse.builder()
                .tier(sub.getTier())
                .proExpiresAt(sub.getProExpiresAt())
                .isPro(sub.isPro())
                .build();
    }

    /**
     * Calls Razorpay GET /payments/{id} and returns the JSON node.
     * Uses HTTP Basic auth: key_id:key_secret.
     */
    private JsonNode fetchRazorpayPayment(String paymentId) {
        try {
            String credentials = razorpayKeyId + ":" + razorpayKeySecret;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/payments/" + paymentId))
                    .header("Authorization", "Basic " + encoded)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new IllegalArgumentException("Payment not found on Razorpay: " + paymentId);
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Razorpay API error: " + response.statusCode() + " " + response.body());
            }

            return objectMapper.readTree(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch payment from Razorpay", e);
        }
    }
}
