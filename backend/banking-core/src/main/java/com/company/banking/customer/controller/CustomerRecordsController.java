package com.company.banking.customer.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.customer.dto.AddressRequest;
import com.company.banking.customer.dto.AddressResponse;
import com.company.banking.customer.dto.IdentificationRequest;
import com.company.banking.customer.dto.IdentificationResponse;
import com.company.banking.customer.dto.NextOfKinRequest;
import com.company.banking.customer.dto.NextOfKinResponse;
import com.company.banking.customer.dto.RelatedPartyRequest;
import com.company.banking.customer.dto.RelatedPartyResponse;
import com.company.banking.customer.dto.RevealedIdentification;
import com.company.banking.customer.service.CustomerContactService;
import com.company.banking.customer.service.CustomerIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Addresses, next of kin, related parties and identifications of a customer. Changes need customer.edit, or
 * customer.create while the customer is still being onboarded (enforced in the service).
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@RequiredArgsConstructor
@Tag(name = "Customers")
public class CustomerRecordsController {

    private static final String CAPTURE = "hasAnyAuthority('customer.edit', 'customer.create')";

    private final CustomerContactService contactService;
    private final CustomerIdentityService identityService;

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Add an address (the first one becomes primary)")
    public ApiResponse<AddressResponse> addAddress(@PathVariable UUID customerId,
                                                   @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok("Address added", contactService.addAddress(customerId, request));
    }

    @PutMapping("/addresses/{addressId}")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Update an address")
    public ApiResponse<AddressResponse> updateAddress(@PathVariable UUID customerId, @PathVariable UUID addressId,
                                                      @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok("Address updated", contactService.updateAddress(customerId, addressId, request));
    }

    @DeleteMapping("/addresses/{addressId}")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Remove an address (kept for history)")
    public ApiResponse<Void> removeAddress(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        contactService.removeAddress(customerId, addressId);
        return ApiResponse.message("Address removed");
    }

    @PostMapping("/next-of-kin")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Add a next of kin")
    public ApiResponse<NextOfKinResponse> addNextOfKin(@PathVariable UUID customerId,
                                                       @Valid @RequestBody NextOfKinRequest request) {
        return ApiResponse.ok("Next of kin added", contactService.addNextOfKin(customerId, request));
    }

    @PutMapping("/next-of-kin/{nextOfKinId}")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Update a next of kin")
    public ApiResponse<NextOfKinResponse> updateNextOfKin(@PathVariable UUID customerId,
                                                          @PathVariable UUID nextOfKinId,
                                                          @Valid @RequestBody NextOfKinRequest request) {
        return ApiResponse.ok("Next of kin updated",
                contactService.updateNextOfKin(customerId, nextOfKinId, request));
    }

    @DeleteMapping("/next-of-kin/{nextOfKinId}")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Remove a next of kin")
    public ApiResponse<Void> removeNextOfKin(@PathVariable UUID customerId, @PathVariable UUID nextOfKinId) {
        contactService.removeNextOfKin(customerId, nextOfKinId);
        return ApiResponse.message("Next of kin removed");
    }

    @PostMapping("/related-parties")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Add a director, owner or signatory of a business customer")
    public ApiResponse<RelatedPartyResponse> addRelatedParty(@PathVariable UUID customerId,
                                                             @Valid @RequestBody RelatedPartyRequest request) {
        return ApiResponse.ok("Related party added", contactService.addRelatedParty(customerId, request));
    }

    @PutMapping("/related-parties/{partyId}")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Update a related party")
    public ApiResponse<RelatedPartyResponse> updateRelatedParty(@PathVariable UUID customerId,
                                                                @PathVariable UUID partyId,
                                                                @Valid @RequestBody RelatedPartyRequest request) {
        return ApiResponse.ok("Related party updated",
                contactService.updateRelatedParty(customerId, partyId, request));
    }

    @DeleteMapping("/related-parties/{partyId}")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Remove a related party")
    public ApiResponse<Void> removeRelatedParty(@PathVariable UUID customerId, @PathVariable UUID partyId) {
        contactService.removeRelatedParty(customerId, partyId);
        return ApiResponse.message("Related party removed");
    }

    @PostMapping("/identifications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Add an identity document",
            description = "The number is validated against the institution's format for the type, checked for "
                    + "duplicates and stored encrypted. Responses only ever show a masked number.")
    public ApiResponse<IdentificationResponse> addIdentification(@PathVariable UUID customerId,
                                                                 @Valid @RequestBody IdentificationRequest request) {
        return ApiResponse.ok("Identification added", identityService.add(customerId, request));
    }

    @DeleteMapping("/identifications/{identificationId}")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Remove an identity document (not once verified)")
    public ApiResponse<Void> removeIdentification(@PathVariable UUID customerId,
                                                  @PathVariable UUID identificationId) {
        identityService.remove(customerId, identificationId);
        return ApiResponse.message("Identification removed");
    }

    @GetMapping("/identifications/{identificationId}/number")
    @PreAuthorize("hasAuthority('kyc.review')")
    @Operation(summary = "Reveal the full identity number (audited)")
    public ApiResponse<RevealedIdentification> revealIdentification(@PathVariable UUID customerId,
                                                                    @PathVariable UUID identificationId) {
        return ApiResponse.ok(identityService.reveal(customerId, identificationId));
    }
}
