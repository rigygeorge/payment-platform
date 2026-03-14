package com.paymentplatform.merchant.repository;

import com.paymentplatform.merchant.entity.Merchant;
import com.paymentplatform.merchant.entity.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByEmail(String email);

    Optional<Merchant> findByApiKey(String apiKey);

    boolean existsByEmail(String email);

    Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);

    @Query("SELECT m FROM Merchant m WHERE m.status = 'ACTIVE' ORDER BY m.createdAt DESC")
    List<Merchant> findActiveMerchantsOrderByCreatedAt();

    long countByStatus(MerchantStatus status);
}