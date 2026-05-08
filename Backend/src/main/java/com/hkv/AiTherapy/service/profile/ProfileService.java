package com.hkv.AiTherapy.service.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.domain.PersonalityTrait;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.UpdateProfileRequest;
import com.hkv.AiTherapy.dto.response.ProfileResponse;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private final TherapyProfileRepository profileRepository;
    private final PersonalityTraitRepository traitRepository;
    private final LongTermMemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ProfileService(TherapyProfileRepository profileRepository,
                          PersonalityTraitRepository traitRepository,
                          LongTermMemoryRepository memoryRepository,
                          UserRepository userRepository,
                          ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.traitRepository = traitRepository;
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        TherapyProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));

        List<PersonalityTrait> traits = traitRepository.findByUserIdOrderByConfidenceDesc(userId);

        return buildProfileResponse(profile, traits);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        TherapyProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));

        if (request.getPreferredName() != null) profile.setPreferredName(request.getPreferredName());
        if (request.getAgeRange() != null) profile.setAgeRange(request.getAgeRange());
        if (request.getCommunicationStyle() != null) profile.setCommunicationStyle(request.getCommunicationStyle());
        if (request.getAiPersona() != null) profile.setAiPersona(request.getAiPersona());
        if (request.getTopicsOfConcern() != null) {
            try {
                profile.setTopicsOfConcern(objectMapper.writeValueAsString(request.getTopicsOfConcern()));
            } catch (JsonProcessingException e) {
                // Ignore parsing errors for simple profile update
            }
        }

        profile = profileRepository.save(profile);
        List<PersonalityTrait> traits = traitRepository.findByUserIdOrderByConfidenceDesc(userId);

        return buildProfileResponse(profile, traits);
    }

    /**
     * Deletes the AI-generated profile data (TherapyProfile, PersonalityTraits, LongTermMemories)
     * while keeping the user account itself intact.
     */
    @Transactional
    public void deleteProfile(UUID userId) {
        memoryRepository.deleteAllByUserId(userId);
        traitRepository.deleteAllByUserId(userId);
        profileRepository.deleteByUserId(userId);
    }

    private TherapyProfile createDefaultProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        TherapyProfile profile = TherapyProfile.builder()
                .user(user)
                .preferredName(user.getDisplayName() != null ? user.getDisplayName() : "Friend")
                .totalSessions(0)
                .build();
        return profileRepository.save(profile);
    }

    private ProfileResponse buildProfileResponse(TherapyProfile profile, List<PersonalityTrait> traits) {
        List<String> topics = Collections.emptyList();
        if (profile.getTopicsOfConcern() != null) {
            try {
                topics = objectMapper.readValue(profile.getTopicsOfConcern(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                // Return empty if parsing fails
            }
        }

        List<ProfileResponse.TraitDto> traitDtos = traits.stream()
                .map(t -> new ProfileResponse.TraitDto(t.getTraitKey(), t.getConfidence(), t.getSource()))
                .collect(Collectors.toList());

        return ProfileResponse.builder()
                .userId(profile.getUser().getId().toString())
                .preferredName(profile.getPreferredName())
                .ageRange(profile.getAgeRange())
                .communicationStyle(profile.getCommunicationStyle())
                .aiPersona(profile.getAiPersona())
                .topicsOfConcern(topics)
                .totalSessions(profile.getTotalSessions())
                .traits(traitDtos)
                .build();
    }
}
