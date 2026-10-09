package com.company.banking.operations.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(LocalDate date, String name, Instant createdAt, UUID createdBy) {
}
