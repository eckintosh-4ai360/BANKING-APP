package com.company.banking.channel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ChannelSettingsDtos {

    private ChannelSettingsDtos() {
    }

    /**
     * The institution's limits for app transfers, in its base currency, and its sign-up settings.
     */
    public record Settings(String currency, BigDecimal maxTransferAmount, BigDecimal dailyTransferLimit,
                           int beneficiaryCooldownHours, BigDecimal cooldownMaxAmount, int newDeviceCooldownHours,
                           BigDecimal newDeviceMaxAmount, Onboarding onboarding, Instant updatedAt, Long version) {
    }

    /**
     * Sign-up in the app for people who are not yet customers.
     *
     * @param branchId    the branch new customers belong to
     * @param tierCode    the KYC tier they are verified to
     * @param productId   the product of the first account they open once verified
     * @param minimumAge  the youngest who may sign up, in years
     */
    public record Onboarding(boolean enabled, UUID branchId, String branchName, String tierCode, UUID productId,
                             String productName, int minimumAge) {
    }

    public record OnboardingUpdate(
            @NotNull Boolean enabled,
            UUID branchId,
            @Size(max = 30) String tierCode,
            UUID productId,
            @NotNull @Min(0) @Max(120) Integer minimumAge,
            @NotNull Long version) {
    }

    public record Update(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal maxTransferAmount,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal dailyTransferLimit,
            @NotNull @Min(0) @Max(720) Integer beneficiaryCooldownHours,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal cooldownMaxAmount,
            @NotNull @Min(0) @Max(720) Integer newDeviceCooldownHours,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal newDeviceMaxAmount,
            @NotNull Long version) {
    }
}
