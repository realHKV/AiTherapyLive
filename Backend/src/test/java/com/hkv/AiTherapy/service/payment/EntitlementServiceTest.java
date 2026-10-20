package com.hkv.AiTherapy.service.payment;

import com.hkv.AiTherapy.domain.UserSubscription;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntitlementServiceTest {
    @Test
    void expiredProIsEffectiveFreeEvenBeforeDowngradeJobRuns() {
        UUID userId = UUID.randomUUID();
        UserSubscriptionRepository repository = mock(UserSubscriptionRepository.class);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(UserSubscription.builder()
                .tier("PRO").proExpiresAt(Instant.now().minusSeconds(1)).build()));
        EntitlementService service = new EntitlementService(repository);
        assertFalse(service.isPro(userId));
        assertEquals("FREE", service.status(userId).getTier());
        assertFalse(service.status(userId).isPro());
    }
}
