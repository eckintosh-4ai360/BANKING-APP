package com.company.banking.audit.controller;

import com.company.banking.audit.dto.AuditLogResponse;
import com.company.banking.audit.dto.AuditLogSearchCriteria;
import com.company.banking.audit.service.AuditQueryService;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Immutable audit trail of the institution")
public class AuditLogController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit.view')")
    @Operation(summary = "Search the institution's audit trail (newest first)")
    public ApiResponse<PageResponse<AuditLogResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var criteria = new AuditLogSearchCriteria(from, to, actorId, action, resourceType, resourceId, outcome);
        return ApiResponse.ok(auditQueryService.search(criteria, PageRequests.of(page, size, Sort.unsorted())));
    }
}
