package com.hkv.AiTherapy.service.memory;

import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock private LongTermMemoryRepository memoryRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private MemoryService memoryService;

    @Test
    void foreignMemoryIsNotDeleted() {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> memoryService.deleteMemory(userId, memoryId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(memoryRepository).existsByIdAndUserId(memoryId, userId);
        verify(memoryRepository, never()).deleteById(any());
    }
}
