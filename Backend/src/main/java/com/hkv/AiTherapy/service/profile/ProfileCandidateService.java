package com.hkv.AiTherapy.service.profile;

import com.hkv.AiTherapy.domain.PersonalityTrait;
import com.hkv.AiTherapy.domain.ProfileCandidate;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.dto.response.ProfileCandidateResponse;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.ProfileCandidateRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** AI output remains a suggestion until its owner explicitly accepts it. */
@Service
public class ProfileCandidateService {
    private final ProfileCandidateRepository candidates;
    private final UserRepository users;
    private final TherapyProfileRepository profiles;
    private final PersonalityTraitRepository traits;

    public ProfileCandidateService(ProfileCandidateRepository candidates, UserRepository users,
                                   TherapyProfileRepository profiles, PersonalityTraitRepository traits) {
        this.candidates = candidates;
        this.users = users;
        this.profiles = profiles;
        this.traits = traits;
    }

    @Transactional(readOnly = true)
    public List<ProfileCandidateResponse> pending(UUID userId) {
        return candidates.findPendingCurrent(userId, PageRequest.of(0, 30))
                .stream().map(this::response).toList();
    }

    @Transactional
    public void accept(UUID userId, UUID candidateId) {
        var user = users.findByIdForUpdate(userId).filter(u -> u.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        ProfileCandidate candidate = ownedPending(userId, candidateId);
        if (candidate.getSourceRevision() != user.getContextRevision()
                || !candidate.getConversation().isContextEligible())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Suggestion is no longer current");

        String field = candidate.getFieldKey();
        String value = candidate.getProposedValue();
        if (field.startsWith("trait:")) {
            String key = field.substring("trait:".length());
            int delimiter = value.indexOf(": ");
            if (delimiter < 1 || delimiter + 2 >= value.length())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Suggestion is invalid");
            PersonalityTrait trait = traits.findByUserIdAndTraitKey(userId, key)
                    .orElseGet(() -> PersonalityTrait.builder().user(user).traitKey(key).build());
            trait.setTraitValue(value.substring(delimiter + 2));
            trait.setConfidence(candidate.getConfidence() == null ? 0.5 : candidate.getConfidence());
            trait.setSource("user_confirmed_ai");
            traits.save(trait);
        } else {
            TherapyProfile profile = profiles.findByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
            switch (field) {
                case "name" -> profile.setPreferredName(value);
                case "age_range" -> profile.setAgeRange(value);
                case "gender" -> profile.setGender(value);
                default -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Suggestion is invalid");
            }
            profiles.save(profile);
        }
        candidate.setStatus("accepted");
        candidates.save(candidate);
    }

    @Transactional
    public void dismiss(UUID userId, UUID candidateId) {
        ProfileCandidate candidate = ownedPending(userId, candidateId);
        candidate.setStatus("rejected");
        candidates.save(candidate);
    }

    private ProfileCandidate ownedPending(UUID userId, UUID candidateId) {
        ProfileCandidate candidate = candidates.findOwnedForUpdate(candidateId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        if (!"pending".equals(candidate.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Suggestion already reviewed");
        return candidate;
    }

    private ProfileCandidateResponse response(ProfileCandidate candidate) {
        return new ProfileCandidateResponse(candidate.getId(), candidate.getFieldKey(),
                candidate.getProposedValue(), candidate.getEvidence(), candidate.getConfidence());
    }
}
