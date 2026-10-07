package com.company.banking.customer.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.customer.dto.ChangeCustomerStatusRequest;
import com.company.banking.customer.dto.CreateCustomerRequest;
import com.company.banking.customer.dto.CustomerResponse;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.dto.UpdateCustomerRequest;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.customer.service.CustomerService.CustomerSearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAuthority('customer.view')")
    @Operation(summary = "Search customers in the caller's branch scope",
            description = "q matches the customer number exactly, the name partially, the phone number partially "
                    + "and the email exactly.")
    public ApiResponse<PageResponse<CustomerSummary>> search(
            @RequestParam(required = false) @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false)
            @Pattern(regexp = "^(PENDING|ACTIVE|DORMANT|RESTRICTED|FROZEN|CLOSED)$") String status,
            @RequestParam(required = false)
            @Pattern(regexp = "^(NOT_STARTED|IN_PROGRESS|PENDING_REVIEW|VERIFIED|REJECTED|EXPIRED)$") String kycStatus,
            @RequestParam(required = false) @Pattern(regexp = "^(INDIVIDUAL|BUSINESS|GROUP)$") String customerType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var criteria = new CustomerSearchCriteria(q, branchId, status, kycStatus, customerType);
        return ApiResponse.ok(customerService.search(criteria, PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer.create')")
    @Operation(summary = "Register a customer (status PENDING until KYC approval)")
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.ok("Customer registered", customerService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer.view')")
    @Operation(summary = "Customer with profile, contacts, masked identifications and documents")
    public ApiResponse<CustomerResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(customerService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('customer.edit', 'customer.create')")
    @Operation(summary = "Update contact and profile details",
            description = "customer.create alone only allows changes while the customer is being onboarded. "
                    + "Verified identity data needs a KYC update case.")
    public ApiResponse<CustomerResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateCustomerRequest request) {
        return ApiResponse.ok("Customer updated", customerService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('customer.freeze')")
    @Operation(summary = "Restrict, freeze, reactivate or close a customer relationship")
    public ApiResponse<CustomerResponse> changeStatus(@PathVariable UUID id,
                                                      @Valid @RequestBody ChangeCustomerStatusRequest request) {
        return ApiResponse.ok("Customer status changed", customerService.changeStatus(id, request));
    }
}
