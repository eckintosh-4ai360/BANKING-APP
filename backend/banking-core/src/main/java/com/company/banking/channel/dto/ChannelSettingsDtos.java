package com.company.banking.channel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class ChannelSettingsDtos {

    private ChannelSettingsDtos() {
    }

    /**
     * The institution's limits for app transfers, in its base currency.
     */
    public record Settings(String currency, BigDecimal maxTransferAmount, BigDecimal dailyTransferLimit,
                           int beneficiaryCooldownHours, BigDecimal cooldownMaxAmount, int newDeviceCooldownHours,
                           BigDecimal newDeviceMaxAmount, Instant updatedAt, Long version) {
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
