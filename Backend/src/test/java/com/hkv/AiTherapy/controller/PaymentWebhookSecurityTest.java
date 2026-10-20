package com.hkv.AiTherapy.controller;

import tools.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.service.payment.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

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

    @Test
    void signedRawPayloadUsesOrderIdentityAndRejectsTampering() throws Exception {
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PaymentController controller = new PaymentController(subscriptions, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "webhookSecret", "synthetic-secret");
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_fixture123\",\"order_id\":\"order_fixture123\","
                + "\"amount\":5000,\"currency\":\"INR\",\"notes\":{\"userId\":\"untrusted\"}}}}}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("synthetic-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        assertEquals(200, controller.handleWebhook(signature, body).getStatusCode().value());
        verify(subscriptions).handleWebhookCapture("pay_fixture123", "order_fixture123", 5000, "INR");

        assertEquals(400, controller.handleWebhook(signature, body.replace("5000", "4999")).getStatusCode().value());
        verifyNoMoreInteractions(subscriptions);
    }
}
