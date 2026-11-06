package com.hkv.AiTherapy.service.payment;

import com.hkv.AiTherapy.domain.UserSubscription;
import com.hkv.AiTherapy.dto.response.SubscriptionStatusResponse;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class EntitlementService {
    private final UserSubscriptionRepository subscriptions;

    public EntitlementService(UserSubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    public boolean isPro(UUID userId) {
        return subscriptions.findByUserId(userId).map(this::isActivePro).orElse(false);
    }

    public SubscriptionStatusResponse status(UUID userId) {
        return subscriptions.findByUserId(userId).map(sub -> {
                    boolean pro = isActivePro(sub);
                    return SubscriptionStatusResponse.builder().tier(pro ? "PRO" : "FREE")
                            .proExpiresAt(sub.getProExpiresAt()).isPro(pro).build();
                })
                .orElseGet(() -> SubscriptionStatusResponse.builder()
                        .tier("FREE").isPro(false).build());
    }

    private boolean isActivePro(UserSubscription sub) {
        return "PRO".equals(sub.getTier()) && sub.getProExpiresAt() != null
                && sub.getProExpiresAt().isAfter(Instant.now());
    }
}
