package com.hkv.AiTherapy.service.payment;

import com.hkv.AiTherapy.domain.Purchase;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.PurchaseRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {
    @Test
    void purchaseOrderIsServerOwnedAndPriceIsFixed() {
        UserRepository users = mock(UserRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        RazorpayClient razorpay = mock(RazorpayClient.class);
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).isActive(true).build();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(razorpay.createOrder(any(), eq(5000), eq("INR"))).thenReturn("order_fixture123");
        SubscriptionService service = new SubscriptionService(users, mock(UserSubscriptionRepository.class),
                purchases, razorpay, mock(PaymentCaptureService.class), mock(EntitlementService.class));
        ReflectionTestUtils.setField(service, "razorpayKeyId", "rzp_test_fixture");

        var response = service.createPurchase(userId);
        ArgumentCaptor<Purchase> saved = ArgumentCaptor.forClass(Purchase.class);
        verify(purchases).save(saved.capture());
        assertEquals(userId, saved.getValue().getUser().getId());
        assertEquals("order_fixture123", saved.getValue().getRazorpayOrderId());
        assertEquals(5000, saved.getValue().getExpectedAmountPaise());
        assertEquals("INR", saved.getValue().getCurrency());
        assertEquals("order_fixture123", response.orderId());
        assertEquals("rzp_test_fixture", response.keyId());
    }
}
