package com.company.banking.customer.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.customer.dto.CustomerDocumentResponse;
import com.company.banking.customer.dto.DocumentReviewRequest;
import com.company.banking.customer.service.CustomerDocumentService;
import com.company.banking.document.dto.DocumentContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/documents")
@RequiredArgsConstructor
@Tag(name = "Customer documents")
public class CustomerDocumentController {

    private final CustomerDocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('customer.edit', 'customer.create')")
    @Operation(summary = "Upload a KYC document (JPEG, PNG or PDF; type detected from content)")
    public ApiResponse<CustomerDocumentResponse> upload(
            @PathVariable UUID customerId,
            @RequestParam @Pattern(regexp = "^(ID_FRONT|ID_BACK|SELFIE|PROOF_OF_ADDRESS|SIGNATURE|"
                    + "BUSINESS_REGISTRATION|TAX_CERTIFICATE|EMPLOYMENT_LETTER|OTHER)$") String documentType,
            @RequestPart("file") MultipartFile file) {
        try {
            return ApiResponse.ok("Document uploaded", documentService.upload(customerId, documentType,
                    file.getOriginalFilename(), file.getBytes()));
        } catch (IOException ex) {
            throw new BankingException(CommonErrorCode.MALFORMED_REQUEST, "The uploaded file could not be read.");
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('customer.view')")
    @Operation(summary = "List a customer's documents (metadata only)")
    public ApiResponse<List<CustomerDocumentResponse>> list(@PathVariable UUID customerId) {
        return ApiResponse.ok(documentService.list(customerId));
    }

    @GetMapping("/{documentId}/content")
    @PreAuthorize("hasAuthority('kyc.view')")
    @Operation(summary = "Download a document (audited)")
    public ResponseEntity<byte[]> download(@PathVariable UUID customerId, @PathVariable UUID documentId) {
        DocumentContent content = documentService.download(customerId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.info().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.info().fileName()).build().toString())
                .body(content.bytes());
    }

    @PostMapping("/{documentId}/review")
    @PreAuthorize("hasAuthority('kyc.review')")
    @Operation(summary = "Accept or reject a document (not one you uploaded)")
    public ApiResponse<CustomerDocumentResponse> review(@PathVariable UUID customerId, @PathVariable UUID documentId,
                                                        @Valid @RequestBody DocumentReviewRequest request) {
        return ApiResponse.ok("Document reviewed", documentService.review(customerId, documentId, request));
    }
}
