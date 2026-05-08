package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    Optional<UserSubscription> findByUserId(UUID userId);

    /** Find all PRO subscriptions that have expired — used by downgrade job. */
    @Query("SELECT s FROM UserSubscription s WHERE s.tier = 'PRO' AND s.proExpiresAt < :now")
    List<UserSubscription> findExpiredProSubscriptions(@Param("now") Instant now);

    /** Bulk downgrade expired PRO users back to FREE. */
    @Modifying
    @Query("UPDATE UserSubscription s SET s.tier = 'FREE', s.proExpiresAt = NULL WHERE s.tier = 'PRO' AND s.proExpiresAt < :now")
    int downgradeExpired(@Param("now") Instant now);
}
