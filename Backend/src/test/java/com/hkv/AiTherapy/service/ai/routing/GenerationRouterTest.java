package com.hkv.AiTherapy.service.ai.routing;

import com.hkv.AiTherapy.domain.UserSubscription;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import com.hkv.AiTherapy.service.payment.EntitlementService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerationRouterTest {
    private static final List<GenerationMessage> MESSAGES = List.of(
            new GenerationMessage(GenerationMessage.Role.USER, "Synthetic request"));

    @Test
    void everyPurposeRoutesForBothEffectiveTiers() {
        UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
        UUID free = UUID.randomUUID();
        UUID pro = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        when(subscriptions.findByUserId(free)).thenReturn(Optional.empty());
        when(subscriptions.findByUserId(pro)).thenReturn(Optional.of(UserSubscription.builder()
                .tier("PRO").proExpiresAt(Instant.now().plusSeconds(3600)).build()));
        when(subscriptions.findByUserId(expired)).thenReturn(Optional.of(UserSubscription.builder()
                .tier("PRO").proExpiresAt(Instant.now().minusSeconds(3600)).build()));
        GenerationRouter router = new GenerationRouter(new EntitlementService(subscriptions),
                new GenerationPolicy("policy-test", "free-test", "pro-test", "summary-test"),
                new DeterministicFakeGateway());

        for (GenerationPurpose purpose : GenerationPurpose.values()) {
            for (UUID user : List.of(free, pro, expired)) {
                var command = GenerationCommand.serverSelected(user, purpose, MESSAGES, 128);
                var route = router.resolve(command);
                assertEquals(user.equals(pro) ? ResolvedGeneration.Tier.PRO : ResolvedGeneration.Tier.FREE,
                        route.tier());
                String expected = switch (purpose) {
                    case GREETING, CHAT -> user.equals(pro) ? "pro-test" : "free-test";
                    case SUMMARY, PROFILE_REFRESH -> "summary-test";
                };
                assertEquals(expected, route.modelId());
                assertEquals("policy-test", route.policyId());
                assertEquals(expected, router.generate(command, new CancellationToken()).modelId());
            }
        }
    }

    @Test
    void clientModelSelectionIsRejectedBeforeEntitlementOrGatewayCall() {
        EntitlementService entitlements = mock(EntitlementService.class);
        GenerationGateway gateway = mock(GenerationGateway.class);
        GenerationRouter router = new GenerationRouter(entitlements,
                new GenerationPolicy("p", "f", "pro", "s"), gateway);
        var command = new GenerationCommand(UUID.randomUUID(), GenerationPurpose.CHAT, MESSAGES,
                128, "client-selected-model");
        assertThrows(IllegalArgumentException.class, () -> router.generate(command, new CancellationToken()));
        verifyNoInteractions(entitlements, gateway);
    }

    @Test
    void allModelInputsAndPolicyIdAreRequired() {
        assertThrows(IllegalStateException.class, () -> new GenerationPolicy("", "f", "p", "s"));
        assertThrows(IllegalStateException.class, () -> new GenerationPolicy("v", " ", "p", "s"));
        assertThrows(IllegalStateException.class, () -> new GenerationPolicy("v", "f", "", "s"));
        assertThrows(IllegalStateException.class, () -> new GenerationPolicy("v", "f", "p", null));
    }

    @Test
    void routingConfigurationIsOptInAndFailsClosedWhenIncomplete() {
        var runner = new ApplicationContextRunner().withUserConfiguration(GenerationPolicy.class);
        runner.run(context -> assertFalse(context.containsBean("generationPolicy")));
        runner.withPropertyValues("ai.routing.enabled=true").run(context ->
                assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("ai.routing.enabled=true", "ai.routing.policy-id=test-policy",
                "ai.routing.free-model-id=free", "ai.routing.pro-model-id=pro",
                "ai.routing.summary-model-id=summary")
                .run(context -> assertEquals("test-policy", context.getBean(GenerationPolicy.class).policyId()));
    }

    @Test
    void fakeStreamingIsDeterministicAndCancellationTerminates() {
        var request = new ResolvedGeneration(UUID.randomUUID(), GenerationPurpose.CHAT,
                ResolvedGeneration.Tier.FREE, "p", "f", MESSAGES, 128);
        var fake = new DeterministicFakeGateway();
        var events = new ArrayList<GenerationEvent>();
        fake.stream(request, new CancellationToken(), events::add);
        assertEquals(3, events.size());
        assertInstanceOf(GenerationEvent.Started.class, events.get(0));
        assertInstanceOf(GenerationEvent.Delta.class, events.get(1));
        assertInstanceOf(GenerationEvent.Completed.class, events.get(2));
        assertEquals(GenerationUsage.unknown(), ((GenerationEvent.Completed) events.get(2)).result().usage());

        CancellationToken cancelled = new CancellationToken();
        cancelled.cancel();
        events.clear();
        fake.stream(request, cancelled, events::add);
        assertEquals(1, events.size());
        assertEquals(GenerationFailure.Code.CANCELLED,
                ((GenerationEvent.Failed) events.getFirst()).failure().code());
    }
}
