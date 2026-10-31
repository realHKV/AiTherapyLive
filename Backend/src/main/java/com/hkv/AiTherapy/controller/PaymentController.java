package com.hkv.AiTherapy.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import com.hkv.AiTherapy.dto.request.PaymentVerifyRequest;
import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.SubscriptionStatusResponse;
import com.hkv.AiTherapy.dto.response.PurchaseResponse;
import com.hkv.AiTherapy.service.payment.SubscriptionService;
import com.hkv.AiTherapy.service.payment.PaymentGatewayException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    @PostMapping("/purchases")
    public ResponseEntity<ApiResponse<PurchaseResponse>> createPurchase(@AuthenticationPrincipal String userId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(subscriptionService.createPurchase(UUID.fromString(userId))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("PURCHASE_INVALID", "Purchase cannot be created."));
        } catch (PaymentGatewayException | DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("PAYMENT_UNAVAILABLE", "Payment service is temporarily unavailable."));
        } catch (Exception e) {
            log.error("Purchase creation failed");
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("PURCHASE_FAILED", "Purchase could not be created."));
        }
    }

    /**
     * Manual verification for a payment attached to a server-created purchase.
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
            return ResponseEntity.badRequest().body(ApiResponse.error("PAYMENT_INVALID", "Payment does not match your purchase."));
        } catch (PaymentGatewayException | DataAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("PAYMENT_UNAVAILABLE", "Payment service is temporarily unavailable."));
        } catch (Exception e) {
            log.error("Payment verification failed");
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

        // 1. Verify HMAC signature before parsing or processing any event.
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Razorpay webhook secret is not configured.");
            return ResponseEntity.status(503).body("Server configuration error.");
        }
        if (signature == null || !verifyWebhookSignature(rawBody, signature)) {
            log.warn("Webhook signature verification failed.");
            return ResponseEntity.status(400).body("Invalid signature");
        }

        // 2. Parse event
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();

            if ("payment.captured".equals(event)) {
                JsonNode paymentNode = root.path("payload").path("payment").path("entity");

                if (!paymentNode.path("id").isTextual() || !paymentNode.path("order_id").isTextual()
                        || !paymentNode.path("amount").isIntegralNumber()
                        || !paymentNode.path("currency").isTextual()) {
                    return ResponseEntity.badRequest().body("Invalid event");
                }
                subscriptionService.handleWebhookCapture(
                        paymentNode.path("id").asText(), paymentNode.path("order_id").asText(),
                        paymentNode.path("amount").intValue(), paymentNode.path("currency").asText());
            }

            return ResponseEntity.ok("ok");

        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body("Invalid event");
        } catch (IllegalArgumentException e) {
            log.warn("Webhook capture rejected: {}", e.getMessage());
            return ResponseEntity.status(503).body("retry");
        } catch (Exception e) {
            log.error("Webhook processing error");
            return ResponseEntity.status(503).body("retry");
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private boolean verifyWebhookSignature(String payload, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(receivedSignature);
            return MessageDigest.isEqual(hash, received);
        } catch (Exception e) {
            log.error("HMAC verification error", e);
            return false;
        }
    }
}
