package com.company.banking.fieldops.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Requests that manage field officers.
 */
public final class OfficerRequests {

    private OfficerRequests() {
    }

    /**
     * Makes a staff member a field officer at their home branch, with a cash with collectors account in the
     * institution's base currency (or {@code currency}).
     *
     * @param maxOfflineAmount cash the officer may hold before syncing; a sync bringing more raises an alert
     * @param maxOfflineHours  how long a collection may wait on the device before it is late
     */
    public record Register(
            @NotNull UUID staffId,
            @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @DecimalMin(value = "0", inclusive = false) BigDecimal dailyTarget,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxOfflineAmount,
            @NotNull @Min(1) @Max(720) Integer maxOfflineHours) {
    }

    public record Update(
            @DecimalMin(value = "0", inclusive = false) BigDecimal dailyTarget,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxOfflineAmount,
            @NotNull @Min(1) @Max(720) Integer maxOfflineHours,
            @NotNull Long version) {
    }

    public record ChangeStatus(
            @NotBlank @Pattern(regexp = "^(ACTIVE|SUSPENDED)$") String status,
            @NotBlank @Size(max = 300) String reason,
            @NotNull Long version) {
    }

    public record Assign(@NotNull UUID customerId, @NotNull UUID officerId) {
    }

    public record EndAssignment(@NotBlank @Size(max = 300) String reason) {
    }

    /**
     * @param deviceKey the installation id the app generated for itself
     */
    public record RegisterDevice(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{8,100}$") String deviceKey,
            @NotBlank @Size(max = 100) String name) {
    }

    public record Decision(@NotBlank @Size(max = 300) String note, @NotNull Long version) {
    }
}
