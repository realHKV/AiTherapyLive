package com.hkv.AiTherapy.service.memory;

import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.CreateMemoryRequest;
import com.hkv.AiTherapy.dto.response.MemoryPageResponse;
import com.hkv.AiTherapy.dto.response.MemoryResponse;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private final LongTermMemoryRepository memoryRepository;
    private final UserRepository userRepository;

    public MemoryService(LongTermMemoryRepository memoryRepository, UserRepository userRepository) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse getMemories(UUID userId, String type, Boolean resolved, int offset, int limit) {
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
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        LongTermMemory memory = LongTermMemory.builder()
                .user(user)
                .memoryType(request.getType())
                .title(request.getTitle())
                .detail(request.getDetail())
                .importance(request.getImportance())
                .occurredAt(request.getOccurredAt())
                .followUpAt(request.getFollowUpAt())
                .isResolved(false)
                .build();

        memoryRepository.save(memory);
        return mapToDto(memory);
    }

    @Transactional
    public void deleteMemory(UUID userId, UUID memoryId) {
        if (memoryRepository.existsByIdAndUserId(memoryId, userId)) {
            memoryRepository.deleteById(memoryId);
        } else {
            throw new RuntimeException("Memory not found or unauthorized");
        }
    }

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
                .build();
    }
}
