package com.hkv.AiTherapy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.dto.request.PaymentVerifyRequest;
import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.SubscriptionStatusResponse;
import com.hkv.AiTherapy.service.payment.SubscriptionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    public PaymentController(SubscriptionService subscriptionService, ObjectMapper objectMapper) {
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    // ─── GET /payment/status ─────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> getStatus(
            @AuthenticationPrincipal String userId) {
        SubscriptionStatusResponse status = subscriptionService.getStatus(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ─── POST /payment/verify ────────────────────────────────────────────────

    /**
     * Manual verification endpoint (dev/test mode).
     * The frontend calls this after the user completes the Razorpay payment link
     * and the redirect URL gives back a payment ID.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> verifyPayment(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody PaymentVerifyRequest request) {
        try {
            SubscriptionStatusResponse status = subscriptionService.verifyAndUpgrade(
                    UUID.fromString(userId), request.getPaymentId());
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("PAYMENT_NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("PAYMENT_INVALID", e.getMessage()));
        } catch (Exception e) {
            log.error("Payment verification failed", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("VERIFY_FAILED", "Verification failed. Please try again."));
        }
    }

    // ─── POST /payment/webhook ───────────────────────────────────────────────

    /**
     * Razorpay webhook endpoint — NO JWT auth (Razorpay calls this, not the browser).
     * Secured via HMAC SHA-256 signature verification.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        // 1. Verify HMAC signature (Strictly required in production)
        if (webhookSecret == null || webhookSecret.isBlank()) {
            if (devMode) {
                log.warn("RAZORPAY_WEBHOOK_SECRET not configured. Accepting webhook because DEV_MODE is true.");
            } else {
                log.error("CRITICAL: RAZORPAY_WEBHOOK_SECRET is missing in PRODUCTION. Rejecting webhook!");
                return ResponseEntity.status(500).body("Server configuration error.");
            }
        } else {
            if (signature == null || !verifyWebhookSignature(rawBody, signature)) {
                log.warn("Webhook signature verification failed.");
                return ResponseEntity.status(400).body("Invalid signature");
            }
        }

        // 2. Parse event
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();

            if ("payment.captured".equals(event)) {
                JsonNode paymentNode = root.path("payload").path("payment").path("entity");

                String paymentId = paymentNode.path("id").asText();
                String orderId = paymentNode.path("order_id").asText(null);
                int amount = paymentNode.path("amount").asInt();
                String currency = paymentNode.path("currency").asText("INR");

                // Razorpay payment link notes contain the user email or notes.
                // For now we use the "notes" field if it contains userId, otherwise
                // we log and skip (dev mode — use /payment/verify instead).
                JsonNode notes = paymentNode.path("notes");
                String userIdStr = notes.path("userId").asText(null);

                if (userIdStr == null || userIdStr.isBlank()) {
                    log.info("Webhook payment.captured received but no userId in notes. paymentId={}", paymentId);
                    // Still return 200 so Razorpay doesn't retry
                    return ResponseEntity.ok("ok");
                }

                subscriptionService.handleWebhookCapture(
                        paymentId, orderId, amount, currency, UUID.fromString(userIdStr));

                log.info("Webhook: upgraded user {} to PRO via payment {}", userIdStr, paymentId);
            }

            return ResponseEntity.ok("ok");

        } catch (Exception e) {
            log.error("Webhook processing error", e);
            return ResponseEntity.status(500).body("error");
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private boolean verifyWebhookSignature(String payload, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return computed.equalsIgnoreCase(receivedSignature);
        } catch (Exception e) {
            log.error("HMAC verification error", e);
            return false;
        }
    }
}
