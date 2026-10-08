package com.company.banking.account.controller;

import com.company.banking.account.dto.AccountResponse;
import com.company.banking.account.dto.AccountSummary;
import com.company.banking.account.dto.ChangeAccountStatusRequest;
import com.company.banking.account.dto.CloseAccountRequest;
import com.company.banking.account.dto.HoldResponse;
import com.company.banking.account.dto.OpenAccountRequest;
import com.company.banking.account.dto.PlaceHoldRequest;
import com.company.banking.account.dto.ReleaseHoldRequest;
import com.company.banking.account.service.AccountHoldService;
import com.company.banking.account.service.AccountService;
import com.company.banking.account.service.AccountService.AccountSearchCriteria;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountHoldService holdService;

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('account.view')")
    @Operation(summary = "Search accounts within the caller's branch scope")
    public ApiResponse<PageResponse<AccountSummary>> search(
            @RequestParam(required = false) @Pattern(regexp = "^[0-9]{10,20}$") String accountNumber,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false)
            @Pattern(regexp = "^(PENDING|ACTIVE|RESTRICTED|FROZEN|DORMANT|CLOSED)$") String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(accountService.search(new AccountSearchCriteria(accountNumber, branchId, productId,
                status), PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('account.view')")
    @Operation(summary = "An account with its holders and live balance")
    public ApiResponse<AccountResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(accountService.get(id));
    }

    @GetMapping("/customers/{customerId}/accounts")
    @PreAuthorize("hasAuthority('account.view')")
    @Operation(summary = "Accounts a customer holds")
    public ApiResponse<List<AccountSummary>> heldBy(@PathVariable UUID customerId) {
        return ApiResponse.ok(accountService.heldBy(customerId));
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('account.create')")
    @Operation(summary = "Open an account under a product's published terms")
    public ApiResponse<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        return ApiResponse.ok("Account opened", accountService.open(request));
    }

    @PostMapping("/accounts/{id}/status")
    @PreAuthorize("hasAuthority('account.freeze')")
    @Operation(summary = "Restrict, freeze or reactivate an account")
    public ApiResponse<AccountResponse> changeStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody ChangeAccountStatusRequest request) {
        return ApiResponse.ok("Account status changed", accountService.changeStatus(id, request));
    }

    @PostMapping("/accounts/{id}/close")
    @PreAuthorize("hasAuthority('account.close')")
    @Operation(summary = "Close an account with a zero balance and no active holds")
    public ApiResponse<AccountResponse> close(@PathVariable UUID id, @Valid @RequestBody CloseAccountRequest request) {
        return ApiResponse.ok("Account closed", accountService.close(id, request));
    }

    @GetMapping("/accounts/{id}/holds")
    @PreAuthorize("hasAuthority('account.view')")
    @Operation(summary = "Holds on an account, newest first")
    public ApiResponse<List<HoldResponse>> holds(@PathVariable UUID id) {
        return ApiResponse.ok(holdService.list(id));
    }

    @PostMapping("/accounts/{id}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('account.freeze')")
    @Operation(summary = "Reserve funds on an account (lien, legal or fraud-review hold)")
    public ApiResponse<HoldResponse> placeHold(@PathVariable UUID id, @Valid @RequestBody PlaceHoldRequest request) {
        return ApiResponse.ok("Hold placed", holdService.place(id, request));
    }

    @PostMapping("/accounts/{id}/holds/{holdId}/release")
    @PreAuthorize("hasAuthority('account.freeze')")
    @Operation(summary = "Release a hold")
    public ApiResponse<HoldResponse> releaseHold(@PathVariable UUID id, @PathVariable UUID holdId,
                                                 @Valid @RequestBody ReleaseHoldRequest request) {
        return ApiResponse.ok("Hold released", holdService.release(id, holdId, request));
    }
}
