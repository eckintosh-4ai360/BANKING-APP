package com.company.banking.customer.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.DuplicateResourceException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.IdentificationTypeRequest;
import com.company.banking.customer.dto.IdentificationTypeResponse;
import com.company.banking.customer.entity.CustomerType;
import com.company.banking.customer.entity.IdentificationType;
import com.company.banking.customer.entity.IdentificationTypeId;
import com.company.banking.customer.exception.CustomerErrorCode;
import com.company.banking.customer.mapper.CustomerMapper;
import com.company.banking.customer.repository.IdentificationTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Identity document types accepted by an institution. Defaults depend on the institution's country; everything is
 * editable configuration.
 */
@Service
@RequiredArgsConstructor
public class IdentificationTypeService {

    private final IdentificationTypeRepository repository;
    private final CustomerMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<IdentificationTypeResponse> list() {
        return repository.findByIdTenantIdOrderBySortOrderAscIdCodeAsc(TenantContext.requireTenantId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public IdentificationTypeResponse create(IdentificationTypeRequest request) {
        if (request.code() == null) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED, "A code is required.");
        }
        IdentificationTypeId id = new IdentificationTypeId(TenantContext.requireTenantId(), request.code());
        if (repository.existsById(id)) {
            throw new DuplicateResourceException("Identification type " + request.code() + " already exists.");
        }
        IdentificationType type = new IdentificationType(id);
        apply(type, request);
        IdentificationTypeResponse created = mapper.toResponse(repository.saveAndFlush(type));
        auditService.record(AuditEvent.builder("IDENTIFICATION_TYPE_CREATED", "IDENTIFICATION_TYPE")
                .resourceId(request.code())
                .after(created)
                .build());
        return created;
    }

    @Transactional
    public IdentificationTypeResponse update(String code, IdentificationTypeRequest request) {
        IdentificationType type = repository.findById(new IdentificationTypeId(TenantContext.requireTenantId(), code))
                .orElseThrow(() -> new ResourceNotFoundException("Identification type"));
        ConcurrentModificationException.assertVersion(request.version(), type.getVersion());
        IdentificationTypeResponse before = mapper.toResponse(type);
        apply(type, request);
        IdentificationTypeResponse after = mapper.toResponse(repository.saveAndFlush(type));
        auditService.record(AuditEvent.builder("IDENTIFICATION_TYPE_UPDATED", "IDENTIFICATION_TYPE")
                .resourceId(code)
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * An active type that applies to the customer type, or {@code IDENTIFICATION_TYPE_NOT_ACCEPTED}.
     */
    @Transactional(readOnly = true)
    public IdentificationType requireUsable(String code, CustomerType customerType) {
        return repository.findById(new IdentificationTypeId(TenantContext.requireTenantId(),
                        code.trim().toUpperCase(Locale.ROOT)))
                .filter(IdentificationType::isActive)
                .filter(type -> customerType == null || type.appliesTo(customerType))
                .orElseThrow(() -> new BankingException(CustomerErrorCode.IDENTIFICATION_TYPE_NOT_ACCEPTED));
    }

    @Transactional(readOnly = true)
    public boolean supportsElectronicVerification(String code) {
        return repository.findById(new IdentificationTypeId(TenantContext.requireTenantId(), code))
                .map(IdentificationType::isSupportsElectronicVerification)
                .orElse(false);
    }

    /**
     * Creates the default types for the current institution if it has none. Idempotent.
     */
    @Transactional
    public void provisionDefaults(String countryCode) {
        UUID tenantId = TenantContext.requireTenantId();
        if (repository.existsByIdTenantId(tenantId)) {
            return;
        }
        int order = 0;
        for (Default definition : defaultsFor(countryCode)) {
            IdentificationType type = new IdentificationType(new IdentificationTypeId(tenantId, definition.code()));
            type.configure(definition.name(), definition.appliesTo(), definition.regex(), definition.hint(),
                    definition.requiresExpiry(), definition.electronic(), true, order++);
            repository.save(type);
        }
        repository.flush();
    }

    private static List<Default> defaultsFor(String countryCode) {
        if ("GH".equals(countryCode)) {
            return List.of(
                    new Default("GHANA_CARD", "Ghana Card", "INDIVIDUAL", "^GHA-[0-9]{9}-[0-9]$",
                            "GHA-000000000-0", true, true),
                    new Default("PASSPORT", "Passport", "INDIVIDUAL", "^[A-Z0-9]{6,9}$", null, true, false),
                    new Default("VOTER_ID", "Voter ID", "INDIVIDUAL", null, null, false, false),
                    new Default("DRIVERS_LICENCE", "Driver's Licence", "INDIVIDUAL", null, null, true, false),
                    new Default("BUSINESS_REGISTRATION", "Business Registration Certificate", "BUSINESS", null,
                            null, false, false));
        }
        return List.of(
                new Default("NATIONAL_ID", "National ID", "INDIVIDUAL", null, null, false, false),
                new Default("PASSPORT", "Passport", "INDIVIDUAL", "^[A-Z0-9]{6,9}$", null, true, false),
                new Default("DRIVERS_LICENCE", "Driver's Licence", "INDIVIDUAL", null, null, true, false),
                new Default("BUSINESS_REGISTRATION", "Business Registration Certificate", "BUSINESS", null, null,
                        false, false));
    }

    private static void apply(IdentificationType type, IdentificationTypeRequest request) {
        String regex = request.formatRegex() == null || request.formatRegex().isBlank() ? null : request.formatRegex();
        if (regex != null) {
            try {
                Pattern.compile(regex);
            } catch (PatternSyntaxException ex) {
                throw new BankingException(CustomerErrorCode.INVALID_IDENTIFICATION_TYPE_FORMAT);
            }
        }
        type.configure(request.name().trim(), request.appliesTo(), regex, request.formatHint(),
                request.requiresExpiry(), request.supportsElectronicVerification(), request.active(),
                request.sortOrder());
    }

    private record Default(String code, String name, String appliesTo, String regex, String hint,
                           boolean requiresExpiry, boolean electronic) {
    }
}
