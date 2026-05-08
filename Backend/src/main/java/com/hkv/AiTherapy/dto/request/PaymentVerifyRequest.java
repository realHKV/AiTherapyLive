package com.hkv.AiTherapy.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body for POST /payment/verify — manual payment verification.
 */
@Data
public class PaymentVerifyRequest {

    @NotBlank(message = "paymentId is required")
    private String paymentId;  // pay_xxx from Razorpay
}
