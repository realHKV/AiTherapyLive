package com.hkv.AiTherapy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.service.payment.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PaymentWebhookSecurityTest {

    @Test
    void missingWebhookSecretRejectsPayloadBeforeProcessing() {
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PaymentController controller = new PaymentController(subscriptions, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "webhookSecret", "");

        var response = controller.handleWebhook(null, "{\"event\":\"payment.captured\"}");

        assertEquals(503, response.getStatusCode().value());
        verifyNoInteractions(subscriptions);
    }
}
