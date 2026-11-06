package com.hkv.AiTherapy.service.payment;

import java.util.UUID;

public interface RazorpayClient {
    String createOrder(UUID purchaseId, int amountPaise, String currency);
    PaymentDetails fetchPayment(String paymentId);

    record PaymentDetails(String id, String orderId, int amountPaise, String currency, String status) {}
}
