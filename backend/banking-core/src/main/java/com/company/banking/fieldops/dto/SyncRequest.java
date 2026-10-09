package com.company.banking.fieldops.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a field device sends when it comes online: collections and visits recorded offline. Every item carries the
 * device's own reference (a UUID made when it was recorded), so sending the same batch again changes nothing.
 *
 * @param deviceId the registration id the server gave the device
 */
public record SyncRequest(
        @NotNull UUID deviceId,
        @Size(max = 100) List<@Valid @NotNull CollectionItem> collections,
        @Size(max = 100) List<@Valid @NotNull VisitItem> visits) {

    public List<CollectionItem> collectionsOrEmpty() {
        return collections == null ? List.of() : collections;
    }

    public List<VisitItem> visitsOrEmpty() {
        return visits == null ? List.of() : visits;
    }

    /**
     * @param sequenceNo the device's running number for its collections (1, 2, 3, ...), never reused
     * @param susuPlanId the susu plan the cash is for, if any (then {@code accountId} is the plan's account)
     * @param collectedAt when the cash was taken, by the device's clock
     */
    public record CollectionItem(
            @NotNull UUID clientReference,
            @NotNull @Positive Long sequenceNo,
            @NotNull UUID customerId,
            @NotNull UUID accountId,
            UUID susuPlanId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull Instant collectedAt,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 200) String note) {
    }

    public record VisitItem(
            @NotNull UUID clientReference,
            @NotNull UUID customerId,
            @NotBlank @Pattern(regexp = "^(COLLECTION|ONBOARDING|FOLLOW_UP|LOAN_MONITORING|RECOVERY|OTHER)$")
            String purpose,
            @NotBlank @Pattern(regexp = "^(MET|NOT_AVAILABLE|PROMISED_TO_PAY|REFUSED|RELOCATED|OTHER)$")
            String outcome,
            @Size(max = 1000) String notes,
            @NotNull Instant visitedAt,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
    }
}
