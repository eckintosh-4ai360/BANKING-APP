package com.company.banking.operations.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Set;

/**
 * @param workingDays days the institution opens, e.g. {@code ["MONDAY", "TUESDAY", ...]}
 * @param version     the calendar version from the business date response (optimistic locking)
 */
public record WorkingWeekRequest(
        @NotEmpty Set<@NotNull @Pattern(regexp = "^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)$")
                String> workingDays,
        @NotNull Long version) {
}
