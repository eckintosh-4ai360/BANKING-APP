package com.company.banking.susu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SusuDtos {

    private SusuDtos() {
    }

    public record Frequency(String code, String name, String intervalUnit, int intervalCount, boolean active) {
    }

    public record NewFrequency(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,19}$") String code,
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Pattern(regexp = "^(DAY|WEEK|MONTH)$") String intervalUnit,
            @NotNull @Min(1) @Max(12) Integer intervalCount) {
    }

    /**
     * Opens a plan on a susu account the customer holds.
     *
     * @param cycleLength             contributions per cycle (e.g. 31 daily contributions)
     * @param commissionContributions contributions per cycle kept as the collector's commission (e.g. 1)
     * @param startDate               first due date; today's business date when omitted
     * @param endDate                 last date contributions are scheduled; open-ended when omitted
     */
    public record OpenPlan(
            @NotNull UUID customerId,
            @NotNull UUID accountId,
            @NotBlank String frequencyCode,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal contributionAmount,
            @NotNull @Min(1) @Max(366) Integer cycleLength,
            @NotNull @Min(0) Integer commissionContributions,
            LocalDate startDate,
            LocalDate endDate,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal targetAmount) {
    }

    public record Close(@NotBlank @Size(max = 300) String reason, @NotNull Long version) {
    }

    public record Waive(@NotBlank @Size(max = 300) String reason) {
    }

    /**
     * @param missed     contributions past their due date and still unpaid
     * @param arrears    what the missed contributions add up to
     * @param nextDue    the earliest unpaid contribution's due date
     * @param totalPaid  paid contributions times the contribution amount
     */
    public record Plan(UUID id, String planNumber, UUID customerId, UUID accountId, UUID branchId,
                       String frequencyCode, BigDecimal contributionAmount, String currency, int cycleLength,
                       int commissionContributions, LocalDate startDate, LocalDate endDate, BigDecimal targetAmount,
                       String status, int currentCycle, long paid, long missed, BigDecimal arrears, LocalDate nextDue,
                       BigDecimal totalPaid, Instant createdAt, Instant closedAt, String closeReason, Long version) {
    }

    public record Contribution(int sequenceNo, int cycleNo, LocalDate dueDate, BigDecimal amount, String status,
                               Instant paidAt, UUID collectionId, UUID transactionId, String waiveReason) {
    }

    public record Commission(int cycleNo, BigDecimal amountDue, BigDecimal amountCharged, LocalDate businessDate) {
    }

    public record PlanDetail(Plan plan, List<Contribution> contributions, List<Commission> commissions) {
    }

    /**
     * What the field app keeps about a plan to collect for it offline.
     */
    public record CollectablePlan(UUID planId, String planNumber, UUID accountId, BigDecimal contributionAmount,
                                  String currency, String frequencyCode, LocalDate nextDue, long missed,
                                  long unpaidScheduled) {
    }
}
