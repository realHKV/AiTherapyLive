package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.request.CreateMemoryRequest;
import com.hkv.AiTherapy.dto.request.UpdateProfileRequest;
import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.MemoryPageResponse;
import com.hkv.AiTherapy.dto.response.MemoryResponse;
import com.hkv.AiTherapy.dto.response.ProfileResponse;
import com.hkv.AiTherapy.service.memory.MemoryService;
import com.hkv.AiTherapy.service.profile.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/profile/me")
public class ProfileController {

    private final ProfileService profileService;
    private final MemoryService memoryService;

    public ProfileController(ProfileService profileService, MemoryService memoryService) {
        this.profileService = profileService;
        this.memoryService = memoryService;
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@AuthenticationPrincipal String userId) {
        ProfileResponse profile = profileService.getProfile(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(@AuthenticationPrincipal String userId,
                                                                      @RequestBody UpdateProfileRequest request) {
        ProfileResponse updatedProfile = profileService.updateProfile(UUID.fromString(userId), request);
        return ResponseEntity.ok(ApiResponse.success(updatedProfile));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<String>> deleteProfile(@AuthenticationPrincipal String userId) {
        profileService.deleteProfile(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success("Profile deleted successfully. Your account remains active."));
    }

    // ── Memories ──────────────────────────────────────────────────────────────

    @GetMapping("/memories")
    public ResponseEntity<ApiResponse<MemoryPageResponse>> getMemories(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        
        MemoryPageResponse page = memoryService.getMemories(UUID.fromString(userId), type, resolved, offset, limit);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @PostMapping("/memories")
    public ResponseEntity<ApiResponse<MemoryResponse>> addMemory(@AuthenticationPrincipal String userId,
                                                                 @Valid @RequestBody CreateMemoryRequest request) {
        MemoryResponse memory = memoryService.addManualMemory(UUID.fromString(userId), request);
        return ResponseEntity.ok(ApiResponse.success(memory));
    }

    @DeleteMapping("/memories/{memoryId}")
    public ResponseEntity<Void> deleteMemory(@AuthenticationPrincipal String userId,
                                             @PathVariable UUID memoryId) {
        memoryService.deleteMemory(UUID.fromString(userId), memoryId);
        return ResponseEntity.noContent().build();
    }
}
