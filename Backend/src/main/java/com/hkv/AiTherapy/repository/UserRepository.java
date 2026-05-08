package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Used during OAuth2 login to find-or-create a user from the provider's sub claim */
    Optional<User> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);
}
