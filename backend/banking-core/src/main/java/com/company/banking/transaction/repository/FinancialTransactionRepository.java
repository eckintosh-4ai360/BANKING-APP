package com.company.banking.transaction.repository;

import com.company.banking.transaction.entity.FinancialTransaction;
import com.company.banking.transaction.model.TransactionStatus;
import com.company.banking.transaction.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    Optional<FinancialTransaction> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Money taken out of an account on a business date by the given kinds of transaction (fees excluded), for daily
     * limits. Callers hold the account row lock, so the sum cannot change underneath them.
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from FinancialTransaction t
            where t.tenantId = :tenantId and t.debitAccountId = :accountId and t.businessDate = :businessDate
              and t.status = :status and t.transactionType in :types""")
    BigDecimal sumDebits(@Param("tenantId") UUID tenantId, @Param("accountId") UUID accountId,
                         @Param("businessDate") LocalDate businessDate, @Param("status") TransactionStatus status,
                         @Param("types") Collection<TransactionType> types);

    @Query("""
            select t from FinancialTransaction t
            where t.tenantId = :tenantId and (t.debitAccountId = :accountId or t.creditAccountId = :accountId)
              and t.businessDate between :from and :to""")
    Page<FinancialTransaction> findByAccount(@Param("tenantId") UUID tenantId, @Param("accountId") UUID accountId,
                                             @Param("from") LocalDate from, @Param("to") LocalDate to,
                                             Pageable pageable);
}
