package com.company.banking.operations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record HolidayRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 100) String name) {
}
