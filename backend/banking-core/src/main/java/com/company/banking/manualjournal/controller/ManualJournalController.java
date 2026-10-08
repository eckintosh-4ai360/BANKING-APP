package com.company.banking.manualjournal.controller;

import com.company.banking.approval.dto.ApprovalResponse;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.manualjournal.dto.ManualJournalRequest;
import com.company.banking.manualjournal.service.ManualJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger/manual-journals")
@RequiredArgsConstructor
@Tag(name = "Ledger")
public class ManualJournalController {

    private final ManualJournalService manualJournalService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('ledger.post')")
    @Operation(summary = "Prepare a manual journal; it posts when a second person approves it")
    public ApiResponse<ApprovalResponse> submit(@Valid @RequestBody ManualJournalRequest request) {
        return ApiResponse.ok("Manual journal waiting for approval", manualJournalService.submit(request));
    }
}
