package com.hkv.AiTherapy.service.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpRazorpayClient implements RazorpayClient {
    private static final String API = "https://api.razorpay.com/v1";
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${razorpay.key-id:}") private String keyId;
    @Value("${razorpay.key-secret:}") private String keySecret;

    public HttpRazorpayClient(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public String createOrder(UUID purchaseId, int amountPaise, String currency) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "amount", amountPaise, "currency", currency,
                    "receipt", purchaseId.toString(), "partial_payment", false));
            HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/orders"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authorization())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            JsonNode order = send(request, 200, 201);
            String id = requiredText(order, "id");
            if (!id.matches("order_[A-Za-z0-9]{6,90}")
                    || requiredInt(order, "amount") != amountPaise
                    || !currency.equals(requiredText(order, "currency"))
                    || !purchaseId.toString().equals(requiredText(order, "receipt"))) {
                throw new PaymentGatewayException("Razorpay order response did not match the request");
            }
            return id;
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Could not create payment order", e);
        }
    }

    @Override
    public PaymentDetails fetchPayment(String paymentId) {
        if (paymentId == null || !paymentId.matches("pay_[A-Za-z0-9]{6,90}")) {
            throw new IllegalArgumentException("Invalid payment identifier");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/payments/" + paymentId))
                .timeout(Duration.ofSeconds(10)).header("Authorization", authorization()).GET().build();
        try {
            JsonNode payment = send(request, 200);
            String id = requiredText(payment, "id");
            if (!paymentId.equals(id)) throw new PaymentGatewayException("Razorpay payment identifier mismatch");
            return new PaymentDetails(id, requiredText(payment, "order_id"),
                    requiredInt(payment, "amount"), requiredText(payment, "currency"),
                    requiredText(payment, "status"));
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Could not verify payment", e);
        }
    }

    private JsonNode send(HttpRequest request, int... acceptedStatuses) throws Exception {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Payment request interrupted", e);
        }
        for (int status : acceptedStatuses) {
            if (response.statusCode() == status) return mapper.readTree(response.body());
        }
        if (response.statusCode() == 404) throw new IllegalArgumentException("Payment not found");
        throw new PaymentGatewayException("Payment provider unavailable (HTTP " + response.statusCode() + ")");
    }

    private String authorization() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new PaymentGatewayException("Payment provider credentials are not configured");
        }
        String combined = keyId + ":" + keySecret;
        return "Basic " + Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    private String requiredText(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new PaymentGatewayException("Incomplete payment provider response");
        }
        return value.asText();
    }

    private int requiredInt(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new PaymentGatewayException("Incomplete payment provider response");
        }
        return value.intValue();
    }
}
