package com.hkv.AiTherapy.service.memory;

import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.CreateMemoryRequest;
import com.hkv.AiTherapy.dto.request.MemoryPatchRequest;
import com.hkv.AiTherapy.dto.response.MemoryPageResponse;
import com.hkv.AiTherapy.dto.response.MemoryResponse;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private final LongTermMemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final ContextRevisionService contextRevisionService;
    private static final Set<String> TYPES = Set.of("life_event", "goal", "relationship", "preference", "fact");

    public MemoryService(LongTermMemoryRepository memoryRepository, UserRepository userRepository,
                         ContextRevisionService contextRevisionService) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.contextRevisionService = contextRevisionService;
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse getMemories(UUID userId, String type, Boolean resolved, int offset, int limit) {
        validatePage(offset, limit);
        if (type != null && !TYPES.contains(type)) bad("Unsupported memory type");
        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit);
        Page<LongTermMemory> memoryPage = memoryRepository.findByUserIdFiltered(userId, type, resolved, pageable);

        var memoryDtos = memoryPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return new MemoryPageResponse(memoryDtos, memoryPage.getTotalElements(), offset, limit);
    }

    @Transactional
    public MemoryResponse addManualMemory(UUID userId, CreateMemoryRequest request) {
        User user = lockUser(userId);
        validateType(request.getType());
        String title = bounded(request.getTitle(), 200, "title");
        String detail = request.getDetail() == null ? null : bounded(request.getDetail(), 2000, "detail");
        if (title.isBlank()) bad("title is required");
        if (request.getImportance() < 1 || request.getImportance() > 10) bad("importance must be 1-10");
        validateDates(request.getOccurredAt(), request.getFollowUpAt());

        LongTermMemory memory = LongTermMemory.builder()
                .user(user)
                .memoryType(request.getType())
                .title(title)
                .detail(detail)
                .importance(request.getImportance())
                .occurredAt(request.getOccurredAt())
                .followUpAt(request.getFollowUpAt())
                .isResolved(false)
                .status("active")
                .sourceKind("user_saved")
                .confirmedAt(Instant.now())
                .sourceRevision(user.getContextRevision())
                .build();

        memoryRepository.saveAndFlush(memory);
        return mapToDto(memory);
    }

    @Transactional
    public void deleteMemory(UUID userId, UUID memoryId) {
        lockUser(userId);
        forgetOwned(userId, owned(userId, memoryId));
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse getPending(UUID userId, int offset, int limit) {
        validatePage(offset, limit);
        Page<LongTermMemory> page = memoryRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, "pending", PageRequest.of(offset / limit, limit));
        return new MemoryPageResponse(page.map(this::mapToDto).getContent(), page.getTotalElements(), offset, limit);
    }

    @Transactional
    public MemoryResponse confirmCandidate(UUID userId, UUID id, String ifMatch, MemoryPatchRequest patch) {
        User user = lockUser(userId);
        LongTermMemory memory = owned(userId, id);
        checkVersion(memory, ifMatch);
        if (!"pending".equals(memory.getStatus())) conflict("Only pending candidates can be confirmed");
        if (memory.getSourceRevision() != user.getContextRevision())
            conflict("Candidate was created for an older context");
        if (memory.getSourceConversation() != null && !memory.getSourceConversation().isContextEligible())
            conflict("Candidate source is no longer eligible");
        if (patch != null) applyPatch(memory, patch, false);
        memory.setStatus("active");
        memory.setResolved(false);
        memory.setConfirmedAt(Instant.now());
        memoryRepository.saveAndFlush(memory);
        return mapToDto(memory);
    }

    @Transactional
    public void dismissCandidate(UUID userId, UUID id, String ifMatch) {
        lockUser(userId);
        LongTermMemory memory = owned(userId, id);
        checkVersion(memory, ifMatch);
        if (!"pending".equals(memory.getStatus())) conflict("Only pending candidates can be dismissed");
        memoryRepository.delete(memory);
    }

    @Transactional
    public MemoryResponse patchMemory(UUID userId, UUID id, String ifMatch, MemoryPatchRequest patch) {
        lockUser(userId);
        LongTermMemory memory = owned(userId, id);
        checkVersion(memory, ifMatch);
        if (!Set.of("active", "resolved").contains(memory.getStatus())) conflict("Memory cannot be edited");
        if (patch == null) bad("Patch body is required");
        UUID sourceId = memory.getSourceConversation() == null ? null : memory.getSourceConversation().getId();
        applyPatch(memory, patch, true);
        if (sourceId != null) {
            long revision = contextRevisionService.advance(userId, sourceId, false);
            memory.setSourceConversation(null);
            memory.setSourceMessage(null);
            memory.setSourceKind("user_saved");
            memory.setSourceRevision(revision);
        }
        memoryRepository.saveAndFlush(memory);
        return mapToDto(memory);
    }

    @Transactional
    public void forgetMemory(UUID userId, UUID id, String ifMatch) {
        lockUser(userId);
        LongTermMemory memory = owned(userId, id);
        checkVersion(memory, ifMatch);
        forgetOwned(userId, memory);
    }

    private void forgetOwned(UUID userId, LongTermMemory memory) {
        UUID sourceId = memory.getSourceConversation() == null ? null : memory.getSourceConversation().getId();
        contextRevisionService.advance(userId, sourceId, sourceId == null);
        if (sourceId != null) {
            List<LongTermMemory> fromSource = memoryRepository.findByUserIdAndSourceConversationId(userId, sourceId);
            memoryRepository.deleteAll(fromSource);
        } else memoryRepository.delete(memory);
    }

    private void applyPatch(LongTermMemory memory, MemoryPatchRequest patch, boolean allowStatus) {
        if (patch.getTitle() != null) {
            String title = bounded(patch.getTitle(), 200, "title");
            if (title.isBlank()) bad("title is required");
            memory.setTitle(title);
        }
        if (patch.getDetail() != null) memory.setDetail(bounded(patch.getDetail(), 2000, "detail"));
        if (patch.getImportance() != null) {
            if (patch.getImportance() < 1 || patch.getImportance() > 10) bad("importance must be 1-10");
            memory.setImportance(patch.getImportance());
        }
        if (patch.getOccurredAt() != null) memory.setOccurredAt(patch.getOccurredAt());
        if (patch.getFollowUpAt() != null) memory.setFollowUpAt(patch.getFollowUpAt());
        if (patch.getStatus() != null) {
            if (!allowStatus || !Set.of("active", "resolved").contains(patch.getStatus())) bad("Unsupported memory status");
            memory.setStatus(patch.getStatus());
            memory.setResolved("resolved".equals(patch.getStatus()));
        }
        if (patch.getClearFields() != null) {
            for (String field : patch.getClearFields()) {
                switch (field) {
                    case "detail" -> memory.setDetail(null);
                    case "occurredAt" -> memory.setOccurredAt(null);
                    case "followUpAt" -> memory.setFollowUpAt(null);
                    default -> bad("Unsupported clear field");
                }
            }
        }
        validateDates(memory.getOccurredAt(), memory.getFollowUpAt());
    }

    private LongTermMemory owned(UUID userId, UUID id) {
        return memoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found"));
    }

    private User lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private void checkVersion(LongTermMemory memory, String value) {
        if (value == null) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "If-Match required");
        if (!value.matches("\"[0-9]+\"")) bad("If-Match must be a quoted version");
        long expected;
        try { expected = Long.parseLong(value.substring(1, value.length() - 1)); }
        catch (NumberFormatException e) { bad("If-Match version is invalid"); return; }
        if (memory.getVersion() != expected)
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Memory changed; reload it");
    }

    private void validatePage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100 || offset % limit != 0) bad("Invalid memory page bounds");
    }

    private void validateType(String type) { if (type == null || !TYPES.contains(type)) bad("Unsupported memory type"); }

    private void validateDates(LocalDate occurredAt, LocalDate followUpAt) {
        LocalDate earliest = LocalDate.of(1900, 1, 1);
        LocalDate latest = LocalDate.of(2100, 12, 31);
        if ((occurredAt != null && (occurredAt.isBefore(earliest) || occurredAt.isAfter(latest)))
                || (followUpAt != null && (followUpAt.isBefore(earliest) || followUpAt.isAfter(latest))))
            bad("Memory date is outside the supported range");
        if (occurredAt != null && followUpAt != null && followUpAt.isBefore(occurredAt))
            bad("followUpAt must not precede occurredAt");
    }

    private String bounded(String text, int max, String name) {
        if (text == null || text.codePointCount(0, text.length()) > max) bad(name + " is too long");
        return text.trim();
    }

    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private MemoryResponse mapToDto(LongTermMemory memory) {
        return MemoryResponse.builder()
                .id(memory.getId().toString())
                .type(memory.getMemoryType())
                .title(memory.getTitle())
                .detail(memory.getDetail())
                .importance(memory.getImportance())
                .occurredAt(memory.getOccurredAt())
                .followUpAt(memory.getFollowUpAt())
                .isResolved(memory.isResolved())
                .status(memory.getStatus())
                .sourceKind(memory.getSourceKind())
                .confirmedAt(memory.getConfirmedAt())
                .updatedAt(memory.getUpdatedAt())
                .version(memory.getVersion())
                .sourceConversationId(memory.getSourceConversation() == null ? null : memory.getSourceConversation().getId().toString())
                .sourceMessageId(memory.getSourceMessage() == null ? null : memory.getSourceMessage().getId().toString())
                .build();
    }
}
