package com.hkv.AiTherapy.dto.response;

public record PurchaseResponse(String purchaseId, String orderId, String keyId,
                               int amountPaise, String currency) {}
