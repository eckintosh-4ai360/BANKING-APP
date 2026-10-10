package com.company.banking.channel.repository;

import com.company.banking.channel.entity.OtpChallenge;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    /** Row lock while a code is checked, so two answers at once cannot both use one attempt or one code. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpChallenge c where c.tenantId = :tenantId and c.id = :id")
    Optional<OtpChallenge> lock(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Codes requested for a phone since a moment (the hourly limit). */
    long countByTenantIdAndPhoneAndCreatedAtAfter(UUID tenantId, String phone, Instant since);
}
