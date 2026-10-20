package com.hkv.AiTherapy.service.profile;

import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.UpdateProfileRequest;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.memory.ContextRevisionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    private final TherapyProfileRepository profiles = mock(TherapyProfileRepository.class);
    private final PersonalityTraitRepository traits = mock(PersonalityTraitRepository.class);
    private final ProfileService service = new ProfileService(profiles, traits,
            mock(LongTermMemoryRepository.class), mock(UserRepository.class),
            new ObjectMapper(), mock(ContextRevisionService.class));

    @Test
    void onboardingFieldsRoundTripWithoutInferringCompletionFromDefaultPersona() {
        UUID userId = UUID.randomUUID();
        var profile = TherapyProfile.builder().user(User.builder().id(userId).build()).build();
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profiles.save(profile)).thenReturn(profile);

        var request = new UpdateProfileRequest();
        request.setPreferredName(" Sam ");
        request.setAgeRange("25-34");
        request.setGender("nonbinary");
        request.setCountry("IN");
        request.setTopicsOfConcern(List.of("work stress"));
        request.setInterests(List.of("music"));
        request.setAdditionalNote("Synthetic note");
        var beforePersona = service.updateProfile(userId, request);
        assertFalse(beforePersona.isOnboardingComplete());
        assertEquals(List.of("music"), beforePersona.getInterests());
        assertEquals(List.of("work stress"), beforePersona.getTopicsOfConcern());

        var persona = new UpdateProfileRequest();
        persona.setAiPersona("dr_sarah"); // Existing frontend alias.
        var result = service.updateProfile(userId, persona);
        assertTrue(result.isOnboardingComplete());
        assertEquals("dr_sarah", result.getPersonaId());
        assertEquals("dr_sarah", result.getAiPersona());
        assertEquals("Sam", result.getPreferredName());
    }

    @Test
    void invalidPersonaDoesNotReachPersistence() {
        UUID userId = UUID.randomUUID();
        var profile = TherapyProfile.builder().user(User.builder().id(userId).build()).build();
        when(profiles.findByUserId(userId)).thenReturn(Optional.of(profile));
        var request = new UpdateProfileRequest();
        request.setPersonaId("not-approved");
        assertThrows(ResponseStatusException.class, () -> service.updateProfile(userId, request));
        verify(profiles, never()).save(any());
    }
}
