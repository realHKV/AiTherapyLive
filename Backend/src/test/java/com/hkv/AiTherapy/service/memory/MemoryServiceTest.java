package com.hkv.AiTherapy.service.memory;

import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.dto.request.MemoryPatchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock private LongTermMemoryRepository memoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContextRevisionService contextRevisionService;
    @InjectMocks private MemoryService memoryService;

    @Test
    void foreignMemoryIsNotDeleted() {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).isActive(true).build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> memoryService.deleteMemory(userId, memoryId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(memoryRepository).findByIdAndUserId(memoryId, userId);
        verify(memoryRepository, never()).deleteById(any());
    }

    @Test
    void staleVersionCannotChangeOwnedMemory() {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        var memory = LongTermMemory.builder().id(memoryId).user(User.builder().id(userId).build())
                .status("active").title("Synthetic").memoryType("goal").build();
        memory.setVersion(2);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).isActive(true).build()));
        when(memoryRepository.findByIdAndUserId(memoryId, userId)).thenReturn(Optional.of(memory));

        var patch = new MemoryPatchRequest();
        patch.setTitle("Changed");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> memoryService.patchMemory(userId, memoryId, "\"1\"", patch));
        assertEquals(HttpStatus.PRECONDITION_FAILED, error.getStatusCode());
        verify(memoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidPageBoundsAreRejectedBeforeRepositoryAccess() {
        assertThrows(ResponseStatusException.class,
                () -> memoryService.getMemories(UUID.randomUUID(), null, null, 1, 20));
        verifyNoInteractions(memoryRepository);
    }
}
