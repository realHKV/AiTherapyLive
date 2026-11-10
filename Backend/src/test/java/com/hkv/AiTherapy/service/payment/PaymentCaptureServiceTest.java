package com.hkv.AiTherapy.service.payment;

import com.hkv.AiTherapy.domain.Purchase;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.domain.UserSubscription;
import com.hkv.AiTherapy.repository.PaymentRepository;
import com.hkv.AiTherapy.repository.PurchaseRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentCaptureServiceTest {
    private UserRepository users;
    private PurchaseRepository purchases;
    private PaymentRepository payments;
    private UserSubscriptionRepository subscriptions;
    private PaymentCaptureService service;
    private User owner;
    private Purchase purchase;
    private UserSubscription subscription;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        purchases = mock(PurchaseRepository.class);
        payments = mock(PaymentRepository.class);
        subscriptions = mock(UserSubscriptionRepository.class);
        service = new PaymentCaptureService(users, purchases, payments, subscriptions);
        owner = User.builder().id(UUID.randomUUID()).isActive(true).build();
        purchase = Purchase.builder().id(UUID.randomUUID()).user(owner)
                .razorpayOrderId("order_fixture123").expectedAmountPaise(5000)
                .currency("INR").status("PENDING").build();
        subscription = UserSubscription.builder().user(owner).tier("FREE").build();
        when(purchases.findByRazorpayOrderId("order_fixture123")).thenReturn(Optional.of(purchase));
        when(purchases.findUserIdByRazorpayOrderId("order_fixture123")).thenReturn(Optional.of(owner.getId()));
        when(users.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(purchases.findByOrderIdForUpdate("order_fixture123")).thenReturn(Optional.of(purchase));
        when(subscriptions.findByUserId(owner.getId())).thenReturn(Optional.of(subscription));
    }

    private RazorpayClient.PaymentDetails payment(int amount, String currency, String status) {
        return new RazorpayClient.PaymentDetails("pay_fixture123", "order_fixture123", amount, currency, status);
    }

    @Test
    void wrongUserCannotClaimPayment() {
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(payment(5000, "INR", "captured"), UUID.randomUUID()));
        verify(users, never()).findByIdForUpdate(any());
        verifyNoInteractions(payments);
    }

    @Test
    void onlyExactCapturedAmountAndCurrencyQualify() {
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(payment(4999, "INR", "captured"), owner.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(payment(5001, "INR", "captured"), owner.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(payment(5000, "USD", "captured"), owner.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(payment(5000, "INR", "authorized"), owner.getId()));
        verifyNoInteractions(payments);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void successfulCaptureGrantsExactlyOnceAndDuplicateDoesNotExtendExpiry() {
        UUID result = service.capture(payment(5000, "INR", "captured"), owner.getId());
        assertEquals(owner.getId(), result);
        assertEquals("CAPTURED", purchase.getStatus());
        assertEquals("PRO", subscription.getTier());
        Instant firstExpiry = subscription.getProExpiresAt();
        assertTrue(firstExpiry.isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));

        service.capture(payment(5000, "INR", "captured"), owner.getId());
        assertEquals(firstExpiry, subscription.getProExpiresAt());
        verify(payments, times(1)).saveAndFlush(any());
        verify(subscriptions, times(1)).save(subscription);
    }

    @Test
    void validRenewalExtendsRemainingPaidTime() {
        Instant existingExpiry = Instant.now().plus(10, ChronoUnit.DAYS);
        subscription.setTier("PRO");
        subscription.setProExpiresAt(existingExpiry);
        service.capture(payment(5000, "INR", "captured"), owner.getId());
        assertEquals(existingExpiry.plus(30, ChronoUnit.DAYS), subscription.getProExpiresAt());
    }

    @Test
    void alreadyClaimedPaymentCannotFundAnotherPurchase() {
        when(payments.findByRazorpayPaymentId("pay_fixture123"))
                .thenReturn(Optional.of(com.hkv.AiTherapy.domain.Payment.builder().user(owner).build()));
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(payment(5000, "INR", "captured"), owner.getId()));
        verify(subscriptions, never()).save(any());
    }
}
