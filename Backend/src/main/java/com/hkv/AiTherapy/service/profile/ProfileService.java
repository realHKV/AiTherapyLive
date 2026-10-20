package com.hkv.AiTherapy.service.profile;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.domain.PersonalityTrait;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.request.UpdateProfileRequest;
import com.hkv.AiTherapy.dto.response.ProfileResponse;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.memory.ContextRevisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private final TherapyProfileRepository profileRepository;
    private final PersonalityTraitRepository traitRepository;
    private final LongTermMemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ContextRevisionService contextRevisionService;

    public ProfileService(TherapyProfileRepository profileRepository,
                          PersonalityTraitRepository traitRepository,
                          LongTermMemoryRepository memoryRepository,
                          UserRepository userRepository,
                          ObjectMapper objectMapper,
                          ContextRevisionService contextRevisionService) {
        this.profileRepository = profileRepository;
        this.traitRepository = traitRepository;
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.contextRevisionService = contextRevisionService;
    }

    @Transactional
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

        if (request.getPreferredName() != null) profile.setPreferredName(bounded(request.getPreferredName(), 100, "preferredName"));
        if (request.getAgeRange() != null) profile.setAgeRange(bounded(request.getAgeRange(), 50, "ageRange"));
        if (request.getCommunicationStyle() != null) profile.setCommunicationStyle(bounded(request.getCommunicationStyle(), 50, "communicationStyle"));
        if (request.getGender() != null) profile.setGender(bounded(request.getGender(), 30, "gender"));
        if (request.getCountry() != null) profile.setCountry(bounded(request.getCountry(), 100, "country"));
        if (request.getAdditionalNote() != null) profile.setAdditionalNote(bounded(request.getAdditionalNote(), 500, "additionalNote"));

        String selected = coalesce(request.getPersonaId(), request.getLegacySelectedTherapist(), "personaId");
        if (request.getAiPersona() != null) {
            String legacy = request.getAiPersona().trim();
            if (legacy.startsWith("dr_")) selected = coalesce(selected, legacy, "personaId");
            else profile.setAiPersona(bounded(legacy, 50, "aiPersona"));
        }
        if (selected != null) {
            if (!Set.of("dr_sarah", "dr_alex").contains(selected)) bad("personaId is not supported");
            profile.setSelectedTherapist(selected);
            profile.setAiPersona(selected); // Old frontend reads this property.
        }

        List<String> topics = coalesceList(request.getTopicsOfConcern(), request.getLegacyTopicsOfConcern(), "topicsOfConcern");
        if (topics != null) profile.setTopicsOfConcern(objectMapper.writeValueAsString(validItems(topics, "topicsOfConcern")));
        List<String> interests = coalesceList(request.getInterests(), request.getLegacyEnjoyments(), "interests");
        if (interests != null) profile.setEnjoyments(objectMapper.writeValueAsString(validItems(interests, "interests")));

        if (request.getClearFields() != null) {
            for (String field : request.getClearFields()) {
                switch (field) {
                    case "preferredName" -> profile.setPreferredName(null);
                    case "ageRange" -> profile.setAgeRange(null);
                    case "gender" -> profile.setGender(null);
                    case "country" -> profile.setCountry(null);
                    case "additionalNote" -> profile.setAdditionalNote(null);
                    case "topicsOfConcern" -> profile.setTopicsOfConcern(null);
                    case "interests" -> profile.setEnjoyments(null);
                    case "personaId" -> { profile.setSelectedTherapist(null); profile.setAiPersona("calm"); }
                    default -> bad("Unsupported clear field");
                }
            }
        }
        profile.setOnboardingComplete(profile.getSelectedTherapist() != null
                && hasText(profile.getPreferredName()) && hasText(profile.getAgeRange())
                && hasText(profile.getGender()) && hasText(profile.getCountry()));

        profile = profileRepository.save(profile);
        List<PersonalityTrait> traits = traitRepository.findByUserIdOrderByConfidenceDesc(userId);

        return buildProfileResponse(profile, traits);
    }

    /**
     * Deletes the AI-generated profile data (TherapyProfile, PersonalityTraits, LongTermMemories)
     * while keeping the user account itself intact.
     */
    @Transactional
    public long deleteProfile(UUID userId) {
        long revision = contextRevisionService.advance(userId, null, true);
        memoryRepository.deleteAllByUserId(userId);
        traitRepository.deleteAllByUserId(userId);
        profileRepository.deleteByUserId(userId);
        return revision;
    }

    private TherapyProfile createDefaultProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        TherapyProfile profile = TherapyProfile.builder()
                .user(user)
                .totalSessions(0)
                .build();
        return profileRepository.save(profile);
    }

    private ProfileResponse buildProfileResponse(TherapyProfile profile, List<PersonalityTrait> traits) {
        List<String> topics = Collections.emptyList();
        if (profile.getTopicsOfConcern() != null) {
            try {
                topics = objectMapper.readValue(profile.getTopicsOfConcern(), new TypeReference<>() {});
            } catch (JacksonException e) {
                throw new IllegalStateException("Stored profile topics are invalid", e);
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
                .personaId(profile.getSelectedTherapist())
                .onboardingComplete(profile.isOnboardingComplete())
                .gender(profile.getGender())
                .country(profile.getCountry())
                .interests(readItems(profile.getEnjoyments()))
                .additionalNote(profile.getAdditionalNote())
                .totalSessions(profile.getTotalSessions())
                .traits(traitDtos)
                .build();
    }

    private List<String> readItems(String json) {
        if (json == null) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JacksonException e) { throw new IllegalStateException("Stored profile list is invalid", e); }
    }

    private List<String> coalesceList(List<String> canonical, String legacy, String name) {
        if (legacy == null) return canonical;
        List<String> parsed;
        try { parsed = objectMapper.readValue(legacy, new TypeReference<>() {}); }
        catch (JacksonException e) { bad(name + " must be a JSON array"); return null; }
        if (canonical != null && !canonical.equals(parsed)) bad("Conflicting " + name + " values");
        return canonical != null ? canonical : parsed;
    }

    private List<String> validItems(List<String> items, String name) {
        if (items.size() > 20) bad(name + " has too many entries");
        List<String> result = new ArrayList<>();
        for (String item : items) {
            String value = bounded(Objects.requireNonNullElse(item, ""), 100, name);
            if (value.isBlank()) bad(name + " contains a blank entry");
            result.add(value);
        }
        return result;
    }

    private String coalesce(String first, String second, String name) {
        if (first != null && second != null && !first.equals(second)) bad("Conflicting " + name + " values");
        return first != null ? first : second;
    }

    private String bounded(String input, int max, String name) {
        String value = input.trim();
        if (value.codePointCount(0, value.length()) > max) bad(name + " is too long");
        return value;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
