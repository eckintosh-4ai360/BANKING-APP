package com.company.banking.operations.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.operations.dto.EodRunResponse;
import com.company.banking.operations.service.EndOfDayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/eod")
@RequiredArgsConstructor
@Tag(name = "Operations")
public class EndOfDayController {

    private final EndOfDayService endOfDayService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('operations.manage')")
    @Operation(summary = "Close the current business date: roll to the next working day, then run the end-of-day steps")
    public ApiResponse<EodRunResponse> start() {
        return ApiResponse.ok("End-of-day started", endOfDayService.start());
    }

    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('operations.manage')")
    @Operation(summary = "Resume a failed or interrupted run from its unfinished steps")
    public ApiResponse<EodRunResponse> resume(@PathVariable UUID id) {
        return ApiResponse.ok("End-of-day resumed", endOfDayService.resume(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('operations.view', 'operations.manage')")
    @Operation(summary = "An end-of-day run with the progress of its steps")
    public ApiResponse<EodRunResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(endOfDayService.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('operations.view', 'operations.manage')")
    @Operation(summary = "End-of-day runs, latest first")
    public ApiResponse<PageResponse<EodRunResponse>> list(@RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(endOfDayService.list(PageRequests.of(page, size, Sort.unsorted())));
    }
}
