package com.company.banking.operations.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.operations.dto.BusinessDateResponse;
import com.company.banking.operations.dto.HolidayRequest;
import com.company.banking.operations.dto.HolidayResponse;
import com.company.banking.operations.dto.WorkingWeekRequest;
import com.company.banking.operations.service.BusinessCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@Tag(name = "Operations")
public class BusinessCalendarController {

    private final BusinessCalendarService calendarService;

    @PutMapping("/working-week")
    @PreAuthorize("hasAuthority('operations.manage')")
    @Operation(summary = "Set the days the institution opens")
    public ApiResponse<BusinessDateResponse> workingWeek(@Valid @RequestBody WorkingWeekRequest request) {
        return ApiResponse.ok("Working week saved", calendarService.changeWorkingWeek(request));
    }

    @GetMapping("/holidays")
    @PreAuthorize("hasAnyAuthority('operations.view', 'operations.manage')")
    @Operation(summary = "Holidays of a year")
    public ApiResponse<List<HolidayResponse>> holidays(@RequestParam @Min(2000) @Max(2200) int year) {
        return ApiResponse.ok(calendarService.holidays(year));
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('operations.manage')")
    @Operation(summary = "Declare a future day a holiday")
    public ApiResponse<HolidayResponse> addHoliday(@Valid @RequestBody HolidayRequest request) {
        return ApiResponse.ok("Holiday added", calendarService.addHoliday(request));
    }

    @DeleteMapping("/holidays/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('operations.manage')")
    @Operation(summary = "Remove a future holiday")
    public void removeHoliday(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        calendarService.removeHoliday(date);
    }
}
