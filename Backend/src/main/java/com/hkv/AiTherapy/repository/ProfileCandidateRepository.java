package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.ProfileCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileCandidateRepository extends JpaRepository<ProfileCandidate, UUID> {
    long countByUserIdAndStatus(UUID userId, String status);
}
