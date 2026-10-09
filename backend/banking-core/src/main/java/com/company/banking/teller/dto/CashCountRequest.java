package com.company.banking.teller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Cash counted note by note: denomination (a decimal string, e.g. {@code "200"} or {@code "0.50"}) to number of
 * pieces. The server computes the total; the client never sends one.
 */
public record CashCountRequest(
        @NotEmpty @Size(max = 30) Map<@NotNull @Pattern(regexp = "^[0-9]{1,7}(\\.[0-9]{1,4})?$") String,
                @NotNull @PositiveOrZero @Max(10_000_000) Integer> denominations) {
}
