package com.company.banking.teller.repository;

import com.company.banking.teller.entity.TellerSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TellerSessionRepository extends JpaRepository<TellerSession, UUID> {

    Optional<TellerSession> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Exclusive lock for closing and accepting differences: waits for cash transactions in flight on the drawer.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TellerSession s where s.tenantId = :tenantId and s.id = :id")
    Optional<TellerSession> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Shared lock taken by every cash transaction on the drawer, so a close cannot read the drawer balance while one
     * is in flight.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from TellerSession s where s.tenantId = :tenantId and s.tellerId = :tellerId and s.status = :status")
    Optional<TellerSession> lockSharedByTeller(@Param("tenantId") UUID tenantId, @Param("tellerId") UUID tellerId,
                                               @Param("status") TellerSession.Status status);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from TellerSession s where s.tenantId = :tenantId and s.cashDrawerId = :drawerId"
            + " and s.status = :status")
    Optional<TellerSession> lockSharedByDrawer(@Param("tenantId") UUID tenantId, @Param("drawerId") UUID drawerId,
                                               @Param("status") TellerSession.Status status);

    Optional<TellerSession> findFirstByTenantIdAndTellerIdAndStatusIn(UUID tenantId, UUID tellerId,
                                                                     Collection<TellerSession.Status> statuses);

    Optional<TellerSession> findFirstByTenantIdAndCashDrawerIdAndStatusIn(UUID tenantId, UUID drawerId,
                                                                         Collection<TellerSession.Status> statuses);

    List<TellerSession> findAllByTenantIdAndStatusIn(UUID tenantId, Collection<TellerSession.Status> statuses);

    List<TellerSession> findAllByTenantIdAndBusinessDateOrderByOpenedAt(UUID tenantId, LocalDate businessDate);
}
