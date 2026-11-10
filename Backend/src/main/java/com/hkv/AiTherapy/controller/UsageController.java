package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.UsageStatusResponse;
import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import com.hkv.AiTherapy.service.payment.EntitlementService;
import com.hkv.AiTherapy.service.usage.UsageAccountingService;
import com.hkv.AiTherapy.service.usage.UsageException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/usage")
public class UsageController {
    private final UsageAccountingService accounting;
    private final EntitlementService entitlements;
    public UsageController(UsageAccountingService accounting, EntitlementService entitlements) {
        this.accounting = accounting; this.entitlements = entitlements;
    }
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<UsageStatusResponse>> status(@AuthenticationPrincipal String userId) {
        try {
            UUID id = UUID.fromString(userId);
            ResolvedGeneration.Tier tier = entitlements.isPro(id) ? ResolvedGeneration.Tier.PRO : ResolvedGeneration.Tier.FREE;
            var usage = accounting.status(id, tier);
            return ResponseEntity.ok(ApiResponse.success(UsageStatusResponse.builder().tier(usage.tier().name())
                    .policyId(usage.policyId()).remainingRequests(usage.remainingRequests())
                    .remainingTokens(usage.remainingTokens()).reservedTokens(usage.reservedTokens())
                    .resetAt(usage.resetAt()).build()));
        } catch (DataAccessException | UsageException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("ACCOUNTING_UNAVAILABLE", "Usage accounting is temporarily unavailable."));
        }
    }
}
