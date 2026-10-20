package com.hkv.AiTherapy.service.ai.routing;

import com.hkv.AiTherapy.service.payment.EntitlementService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** Routes by trusted entitlement and purpose, never by a client supplied model. */
@Service
@ConditionalOnProperty(name = "ai.routing.enabled", havingValue = "true")
public class GenerationRouter {
    private final EntitlementService entitlements;
    private final GenerationPolicy policy;
    private final GenerationGateway gateway;

    public GenerationRouter(EntitlementService entitlements, GenerationPolicy policy, GenerationGateway gateway) {
        this.entitlements = entitlements;
        this.policy = policy;
        this.gateway = gateway;
    }

    public ResolvedGeneration resolve(GenerationCommand command) {
        if (command.requestedModelId() != null)
            throw new IllegalArgumentException("Client model selection is not allowed");
        ResolvedGeneration.Tier tier = entitlements.isPro(command.userId())
                ? ResolvedGeneration.Tier.PRO : ResolvedGeneration.Tier.FREE;
        return new ResolvedGeneration(command.userId(), command.purpose(), tier,
                policy.policyId(), policy.modelFor(command.purpose(), tier),
                command.messages(), command.maxOutputTokens());
    }

    public GenerationResult generate(GenerationCommand command, CancellationToken cancellation) {
        return gateway.generate(resolve(command), cancellation);
    }

    public void stream(GenerationCommand command, CancellationToken cancellation, Consumer<GenerationEvent> events) {
        gateway.stream(resolve(command), cancellation, events);
    }
}
