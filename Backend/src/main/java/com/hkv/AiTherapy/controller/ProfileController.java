package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.request.CreateMemoryRequest;
import com.hkv.AiTherapy.dto.request.MemoryPatchRequest;
import com.hkv.AiTherapy.dto.request.UpdateProfileRequest;
import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.MemoryPageResponse;
import com.hkv.AiTherapy.dto.response.MemoryResponse;
import com.hkv.AiTherapy.dto.response.ProfileResponse;
import com.hkv.AiTherapy.dto.response.ProfileCandidateResponse;
import com.hkv.AiTherapy.service.memory.MemoryService;
import com.hkv.AiTherapy.service.profile.ProfileService;
import com.hkv.AiTherapy.service.profile.ProfileCandidateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/profile/me")
public class ProfileController {

    private final ProfileService profileService;
    private final MemoryService memoryService;
    private final ProfileCandidateService profileCandidateService;

    public ProfileController(ProfileService profileService, MemoryService memoryService,
                             ProfileCandidateService profileCandidateService) {
        this.profileService = profileService;
        this.memoryService = memoryService;
        this.profileCandidateService = profileCandidateService;
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

    @PostMapping("/context-reset")
    public ResponseEntity<ApiResponse<Map<String, Long>>> resetContext(@AuthenticationPrincipal String userId) {
        long revision = profileService.deleteProfile(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(Map.of("contextRevision", revision)));
    }

    @GetMapping("/profile-candidates")
    public ResponseEntity<ApiResponse<List<ProfileCandidateResponse>>> profileCandidates(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(profileCandidateService.pending(UUID.fromString(userId))));
    }

    @PostMapping("/profile-candidates/{candidateId}/confirm")
    public ResponseEntity<ApiResponse<ProfileResponse>> confirmProfileCandidate(
            @AuthenticationPrincipal String userId, @PathVariable UUID candidateId) {
        UUID owner = UUID.fromString(userId);
        profileCandidateService.accept(owner, candidateId);
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfile(owner)));
    }

    @DeleteMapping("/profile-candidates/{candidateId}")
    public ResponseEntity<Void> dismissProfileCandidate(@AuthenticationPrincipal String userId,
                                                         @PathVariable UUID candidateId) {
        profileCandidateService.dismiss(UUID.fromString(userId), candidateId);
        return ResponseEntity.noContent().build();
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

    @GetMapping("/memory-candidates")
    public ResponseEntity<ApiResponse<MemoryPageResponse>> pendingMemories(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(memoryService.getPending(UUID.fromString(userId), offset, limit)));
    }

    @PostMapping("/memory-candidates/{candidateId}/confirm")
    public ResponseEntity<ApiResponse<MemoryResponse>> confirmMemory(@AuthenticationPrincipal String userId,
            @PathVariable UUID candidateId, @RequestHeader(value = "If-Match", required = false) String version,
            @RequestBody(required = false) MemoryPatchRequest patch) {
        MemoryResponse result = memoryService.confirmCandidate(UUID.fromString(userId), candidateId, version, patch);
        return ResponseEntity.ok().eTag("\"" + result.getVersion() + "\"").body(ApiResponse.success(result));
    }

    @DeleteMapping("/memory-candidates/{candidateId}")
    public ResponseEntity<Void> dismissCandidate(@AuthenticationPrincipal String userId,
            @PathVariable UUID candidateId, @RequestHeader(value = "If-Match", required = false) String version) {
        memoryService.dismissCandidate(UUID.fromString(userId), candidateId, version);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/memories/{memoryId}")
    public ResponseEntity<ApiResponse<MemoryResponse>> patchMemory(@AuthenticationPrincipal String userId,
            @PathVariable UUID memoryId, @RequestHeader(value = "If-Match", required = false) String version,
            @RequestBody MemoryPatchRequest patch) {
        MemoryResponse result = memoryService.patchMemory(UUID.fromString(userId), memoryId, version, patch);
        return ResponseEntity.ok().eTag("\"" + result.getVersion() + "\"").body(ApiResponse.success(result));
    }

    @PostMapping("/memories/{memoryId}/forget")
    public ResponseEntity<Void> forgetMemory(@AuthenticationPrincipal String userId,
            @PathVariable UUID memoryId, @RequestHeader(value = "If-Match", required = false) String version) {
        memoryService.forgetMemory(UUID.fromString(userId), memoryId, version);
        return ResponseEntity.noContent().build();
    }
}
