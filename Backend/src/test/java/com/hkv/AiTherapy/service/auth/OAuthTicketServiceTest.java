package com.hkv.AiTherapy.service.auth;

import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.response.AuthResponse;
import com.hkv.AiTherapy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OAuthTicketServiceTest {
    @Test
    void ticketIsSingleUseAndBoundToAnActiveAccount() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Map<String, String> stored = new HashMap<>();
        doAnswer(inv -> { stored.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));
        when(values.getAndDelete(anyString())).thenAnswer(inv -> stored.remove(inv.getArgument(0)));

        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).isActive(true).build();
        when(users.findById(id)).thenReturn(Optional.of(user));
        AuthResponse response = new AuthResponse();
        when(auth.issueForUser(user, "fixture-device")).thenReturn(response);

        OAuthTicketService service = new OAuthTicketService(redis, users, auth);
        String ticket = service.issue(id);
        assertEquals(response, service.exchange(ticket, "fixture-device"));
        assertThrows(IllegalArgumentException.class, () -> service.exchange(ticket, "fixture-device"));
        verify(auth, times(1)).issueForUser(user, "fixture-device");

        user.setActive(false);
        String disabledTicket = service.issue(id);
        assertThrows(IllegalArgumentException.class, () -> service.exchange(disabledTicket, "fixture-device"));
        verify(auth, times(1)).issueForUser(any(), any());
    }
}
