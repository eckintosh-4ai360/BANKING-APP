package com.company.banking.platform.controller;

import com.company.banking.audit.dto.AuditSealResponse;
import com.company.banking.audit.dto.AuditVerificationReport;
import com.company.banking.audit.service.AuditSealService;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seals of the platform's own audit trail (institution trails are sealed separately and not visible here).
 */
@RestController
@RequestMapping("/api/v1/platform/audit-seals")
@RequiredArgsConstructor
@Tag(name = "Platform audit")
public class PlatformAuditSealController {

    private final AuditSealService sealService;

    @GetMapping
    @PreAuthorize("hasAuthority('platform.audit.view')")
    @Operation(summary = "Seals of the platform audit trail (newest first)")
    public ApiResponse<PageResponse<AuditSealResponse>> list(@RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(sealService.list(PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/verification")
    @PreAuthorize("hasAuthority('platform.audit.view')")
    @Operation(summary = "Check the platform trail's seals of a period (at most 31 days)")
    public ApiResponse<AuditVerificationReport> verify(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(sealService.verify(from, to));
    }
}
