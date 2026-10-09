package com.company.banking.fieldops.dto;

import com.company.banking.susu.dto.SusuDtos;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Field operations as the API shows them.
 */
public final class FieldResponses {

    private FieldResponses() {
    }

    /**
     * @param cashBalance cash the officer carries now, from the ledger
     */
    public record Officer(UUID staffId, String firstName, String lastName, UUID branchId, String currency,
                          String status, BigDecimal dailyTarget, BigDecimal maxOfflineAmount, int maxOfflineHours,
                          BigDecimal cashBalance, long assignedCustomers, long openAlerts, Instant createdAt,
                          Long version) {
    }

    /**
     * The officer's cash: what the ledger holds against what the officer's collections and remittances add up to.
     *
     * @param collected  posted collections that were not reversed, ever
     * @param remitted   cash handed to tellers, ever
     * @param expected   collected minus remitted
     * @param reconciled the ledger holds exactly {@code expected}
     */
    public record CashPosition(UUID officerId, String currency, LocalDate businessDate, BigDecimal ledgerBalance,
                               BigDecimal collected, BigDecimal remitted, BigDecimal expected, BigDecimal difference,
                               boolean reconciled, BigDecimal collectedToday, BigDecimal remittedToday) {
    }

    public record Assignment(UUID id, UUID customerId, String customerNumber, String customerName, UUID officerId,
                             Instant assignedAt, UUID assignedBy, Instant endedAt, String endReason) {
    }

    public record Device(UUID id, UUID officerId, String name, long lastSequenceNo, Instant registeredAt,
                         Instant lastSyncedAt, boolean live, Instant revokedAt, String revokeReason, Long version) {
    }

    public record CollectionView(UUID id, UUID clientReference, UUID deviceId, long sequenceNo, UUID officerId,
                                 UUID customerId, String targetType, UUID accountId, UUID susuPlanId,
                                 BigDecimal amount, String currency, Instant collectedAt, Instant receivedAt,
                                 String status, String rejectionCode, String rejectionReason,
                                 UUID transactionId, LocalDate businessDate, BigDecimal latitude, BigDecimal longitude,
                                 String note) {
    }

    public record Visit(UUID id, UUID clientReference, UUID officerId, UUID customerId, String purpose,
                        String outcome, String notes, Instant visitedAt, Instant receivedAt, BigDecimal latitude,
                        BigDecimal longitude) {
    }

    public record Alert(UUID id, UUID officerId, UUID deviceId, String alertType, String detail, Long missingFrom,
                        Long missingTo, UUID clientReference, String status, Instant raisedAt, Instant resolvedAt,
                        UUID resolvedBy, String resolution, Long version) {
    }

    /**
     * What the field app keeps on the phone to work offline: the officer's customers and the accounts it may collect
     * for.
     */
    public record MyCustomer(UUID customerId, String customerNumber, String displayName, String phone,
                             List<CollectableAccount> accounts, List<SusuDtos.CollectablePlan> susuPlans) {
    }

    public record CollectableAccount(UUID accountId, String accountNumber, String title, String productCode,
                                     String productType, String currency, String status) {
    }

    public record Me(Officer officer, CashPosition cash, List<Device> devices) {
    }
}
