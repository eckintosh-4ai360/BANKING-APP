package com.company.banking.channel.controller;

import com.company.banking.account.dto.AccountStatement;
import com.company.banking.account.service.AccountStatementService;
import com.company.banking.channel.dto.BankingDtos;
import com.company.banking.channel.service.CustomerBankingService;
import com.company.banking.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's accounts, statements, transfers and beneficiaries.
 */
@Validated
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
@Tag(name = "Customer banking")
public class CustomerBankingController {

    private final CustomerBankingService bankingService;

    /** Looks up who an account number belongs to; in the body, to keep account numbers out of URLs and logs. */
    public record DestinationQuery(@NotBlank @Size(max = 34) String accountNumber) {
    }

    @GetMapping("/accounts")
    @Operation(summary = "My accounts with balances, and what is available in total")
    public ApiResponse<BankingDtos.Accounts> accounts() {
        return ApiResponse.ok(bankingService.accounts());
    }

    @GetMapping("/accounts/{id}/statement")
    @Operation(summary = "Statement of one of my accounts (the last 30 days unless dates are given)")
    public ApiResponse<AccountStatement> statement(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(bankingService.statement(id, from, to));
    }

    @GetMapping("/accounts/{id}/statement/download")
    @Operation(summary = "Download a statement of one of my accounts as PDF or CSV")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "PDF") @Pattern(regexp = "^(PDF|CSV)$") String format) {
        AccountStatementService.StatementFile file = bankingService.statementFile(id, from, to,
                AccountStatementService.Format.valueOf(format));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }

    @PostMapping("/transfers/destination")
    @Operation(summary = "Who an account number belongs to (partly hidden), to check before paying it")
    public ApiResponse<BankingDtos.Destination> destination(@Valid @RequestBody DestinationQuery query) {
        return ApiResponse.ok(bankingService.destination(query.accountNumber()));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send money to my own account, a beneficiary or an account number, with my PIN")
    public ApiResponse<BankingDtos.TransferReceipt> transfer(
            @Parameter(description = "Unique per intended transfer; reuse it only to retry")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BankingDtos.Transfer request) {
        return ApiResponse.ok("Transfer sent", bankingService.transfer(idempotencyKey, request));
    }

    @GetMapping("/beneficiaries")
    @Operation(summary = "My saved beneficiaries, favourites first")
    public ApiResponse<List<BankingDtos.Beneficiary>> beneficiaries() {
        return ApiResponse.ok(bankingService.beneficiaries());
    }

    @PostMapping("/beneficiaries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a beneficiary, with my PIN; transfers to it are capped while it is new")
    public ApiResponse<BankingDtos.Beneficiary> addBeneficiary(
            @Valid @RequestBody BankingDtos.NewBeneficiary request) {
        return ApiResponse.ok("Beneficiary saved", bankingService.addBeneficiary(request));
    }

    @PutMapping("/beneficiaries/{id}")
    @Operation(summary = "Rename, favourite or set the limit of a beneficiary (raising the limit needs my PIN)")
    public ApiResponse<BankingDtos.Beneficiary> editBeneficiary(@PathVariable UUID id,
                                                               @Valid @RequestBody BankingDtos.BeneficiaryEdit request) {
        return ApiResponse.ok("Beneficiary updated", bankingService.editBeneficiary(id, request));
    }

    @DeleteMapping("/beneficiaries/{id}")
    @Operation(summary = "Remove a beneficiary")
    public ApiResponse<Void> removeBeneficiary(@PathVariable UUID id) {
        bankingService.removeBeneficiary(id);
        return ApiResponse.ok("Beneficiary removed", null);
    }
}
