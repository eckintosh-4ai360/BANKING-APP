package com.company.banking.audit.controller;

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

@RestController
@RequestMapping("/api/v1/audit-seals")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Immutable audit trail of the institution")
public class AuditSealController {

    private final AuditSealService sealService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit.view')")
    @Operation(summary = "Seals of the institution's audit trail (newest first)")
    public ApiResponse<PageResponse<AuditSealResponse>> list(@RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(sealService.list(PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/verification")
    @PreAuthorize("hasAuthority('audit.view')")
    @Operation(summary = "Check the seals of a period (at most 31 days) against the audit trail as it is now")
    public ApiResponse<AuditVerificationReport> verify(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(sealService.verify(from, to));
    }
}
