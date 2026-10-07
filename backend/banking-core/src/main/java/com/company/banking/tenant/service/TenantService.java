package com.company.banking.tenant.service;

import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.mapper.TenantMapper;
import com.company.banking.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to the tenant registry for other modules. In a tenant context only the own tenant is visible
 * (row-level security on {@code core.tenant}).
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    @Transactional(readOnly = true)
    public Optional<TenantSummary> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return tenantRepository.findByCode(code.trim().toLowerCase(Locale.ROOT)).map(tenantMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Optional<TenantSummary> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(tenantMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public TenantSummary getCurrent() {
        return findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Institution"));
    }
}
