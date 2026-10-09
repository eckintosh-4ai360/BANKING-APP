package com.company.banking.account.controller;

import com.company.banking.account.dto.AccountStatement;
import com.company.banking.account.service.AccountStatementService;
import com.company.banking.account.service.AccountStatementService.Format;
import com.company.banking.account.service.AccountStatementService.StatementFile;
import com.company.banking.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{id}/statement")
@RequiredArgsConstructor
@Tag(name = "Accounts")
public class AccountStatementController {

    private final AccountStatementService statementService;

    @GetMapping
    @PreAuthorize("hasAuthority('transaction.view')")
    @Operation(summary = "Account statement from the ledger (last 30 days by default, at most a year)")
    public ApiResponse<AccountStatement> statement(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(statementService.statement(id, from, to));
    }

    @GetMapping("/download")
    @PreAuthorize("hasAuthority('transaction.view')")
    @Operation(summary = "Download the statement as PDF or CSV (audited)")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "PDF") @Pattern(regexp = "^(PDF|CSV)$") String format) {
        StatementFile file = statementService.export(id, from, to, Format.valueOf(format));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}
