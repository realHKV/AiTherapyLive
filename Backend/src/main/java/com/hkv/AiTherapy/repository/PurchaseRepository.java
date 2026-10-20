package com.hkv.AiTherapy.repository;

import com.hkv.AiTherapy.domain.Purchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    Optional<Purchase> findByRazorpayOrderId(String razorpayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.razorpayOrderId = :orderId")
    Optional<Purchase> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
