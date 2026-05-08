package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.SubscriptionStatusResponse;
import com.hkv.AiTherapy.service.payment.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Dev utilities controller.
 * Always registered — endpoints still require a valid JWT so there is no security risk.
 * Remove this class (or gate with a flag) before open-source / public release.
 */
@RestController
@RequestMapping("/dev")
public class DevController {

    private static final Logger log = LoggerFactory.getLogger(DevController.class);

    private final SubscriptionService subscriptionService;

    public DevController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** Force-upgrades the calling user to PRO for 30 days. No payment required. */
    @PostMapping("/force-pro")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> forcePro(
            @AuthenticationPrincipal String userId) {
        try {
            log.info("[DEV] Force-upgrading user {} to PRO", userId);
            SubscriptionStatusResponse status = subscriptionService.devForceUpgrade(UUID.fromString(userId));
            log.info("[DEV] User {} upgraded to PRO successfully", userId);
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("[DEV] Force-pro failed for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("DEV_ERROR", e.getMessage()));
        }
    }

    /** Reverts the calling user back to FREE. */
    @PostMapping("/force-free")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> forceFree(
            @AuthenticationPrincipal String userId) {
        try {
            log.info("[DEV] Force-downgrading user {} to FREE", userId);
            SubscriptionStatusResponse status = subscriptionService.devForceDowngrade(UUID.fromString(userId));
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("[DEV] Force-free failed for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("DEV_ERROR", e.getMessage()));
        }
    }
}
