package com.company.banking.iam.repository;

import com.company.banking.iam.entity.AuthSession;
import com.company.banking.iam.entity.PrincipalType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Query("""
            select count(s) > 0 from AuthSession s
            where s.id = :id
              and s.status = com.company.banking.iam.entity.SessionStatus.ACTIVE
              and s.expiresAt > :now
            """)
    boolean isActive(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
            set s.status = com.company.banking.iam.entity.SessionStatus.REVOKED,
                s.revokedAt = :now, s.revokeReason = :reason, s.version = s.version + 1
            where s.principalType = :principalType and s.principalId = :principalId
              and s.status = com.company.banking.iam.entity.SessionStatus.ACTIVE
            """)
    int revokeAllOfPrincipal(@Param("principalType") PrincipalType principalType,
                             @Param("principalId") UUID principalId,
                             @Param("reason") String reason,
                             @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
            set s.status = com.company.banking.iam.entity.SessionStatus.REVOKED,
                s.revokedAt = :now, s.revokeReason = :reason, s.version = s.version + 1
            where s.tenantId = :tenantId
              and s.status = com.company.banking.iam.entity.SessionStatus.ACTIVE
            """)
    int revokeAllOfTenant(@Param("tenantId") UUID tenantId, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
            set s.status = com.company.banking.iam.entity.SessionStatus.REVOKED,
                s.revokedAt = :now, s.revokeReason = :reason, s.version = s.version + 1
            where s.principalType = :principalType and s.principalId = :principalId and s.deviceId = :deviceId
              and s.status = com.company.banking.iam.entity.SessionStatus.ACTIVE
            """)
    int revokeAllOfDevice(@Param("principalType") PrincipalType principalType,
                          @Param("principalId") UUID principalId,
                          @Param("deviceId") UUID deviceId,
                          @Param("reason") String reason,
                          @Param("now") Instant now);

    List<AuthSession> findByPrincipalTypeAndPrincipalIdOrderByCreatedAtDesc(PrincipalType principalType,
                                                                          UUID principalId, Limit limit);
}
