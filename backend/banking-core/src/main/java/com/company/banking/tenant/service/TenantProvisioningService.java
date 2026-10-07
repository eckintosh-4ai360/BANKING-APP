package com.company.banking.tenant.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.tenant.dto.FeatureResponse;
import com.company.banking.tenant.dto.FeatureStateResponse;
import com.company.banking.tenant.dto.NewTenantCommand;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.entity.Feature;
import com.company.banking.tenant.entity.InstitutionProfile;
import com.company.banking.tenant.entity.InstitutionType;
import com.company.banking.tenant.entity.Tenant;
import com.company.banking.tenant.entity.TenantFeature;
import com.company.banking.tenant.entity.TenantFeatureId;
import com.company.banking.tenant.entity.TenantStatus;
import com.company.banking.tenant.exception.TenantErrorCode;
import com.company.banking.tenant.mapper.TenantMapper;
import com.company.banking.tenant.repository.FeatureRepository;
import com.company.banking.tenant.repository.InstitutionProfileRepository;
import com.company.banking.tenant.repository.TenantFeatureRepository;
import com.company.banking.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Platform-side lifecycle of tenants. Methods touching tenant-owned rows (profile, features) must run inside the
 * target tenant's context; the platform module arranges that.
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final InstitutionProfileRepository profileRepository;
    private final FeatureRepository featureRepository;
    private final TenantFeatureRepository tenantFeatureRepository;
    private final TenantMapper tenantMapper;

    /**
     * Creates the tenant (status {@code ONBOARDING}), its profile and its feature licences. Runs inside the new
     * tenant's context, where other tenants are invisible: the caller checks code availability in platform context
     * beforehand, and the unique constraint on {@code tenant.code} is the final guard.
     */
    @Transactional
    public TenantSummary createTenant(NewTenantCommand command) {
        assertInTenantContext(command.tenantId());
        Set<String> catalog = featureRepository.findAll().stream().map(Feature::getCode).collect(Collectors.toSet());
        if (!catalog.containsAll(command.licensedFeatures())) {
            throw new BankingException(TenantErrorCode.UNKNOWN_FEATURE);
        }

        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(command.tenantId(), command.code(),
                command.legalName(), command.displayName(), InstitutionType.valueOf(command.institutionType()),
                command.countryCode(), command.baseCurrency(), command.timezone(), command.locale(),
                command.licenceNumber()));
        profileRepository.save(new InstitutionProfile(tenant.getId(), command.contactEmail(),
                command.contactPhone()));
        catalog.stream().sorted().forEach(code -> tenantFeatureRepository.save(
                new TenantFeature(tenant.getId(), code, command.licensedFeatures().contains(code))));
        return tenantMapper.toSummary(tenant);
    }

    /**
     * @param targetStatus one of {@code ACTIVE, SUSPENDED, TERMINATED}
     */
    @Transactional
    public TenantSummary changeStatus(UUID tenantId, String targetStatus) {
        Tenant tenant = load(tenantId);
        tenant.changeStatus(TenantStatus.valueOf(targetStatus));
        return tenantMapper.toSummary(tenantRepository.saveAndFlush(tenant));
    }

    @Transactional(readOnly = true)
    public Page<TenantSummary> list(Pageable pageable) {
        return tenantRepository.findAll(pageable).map(tenantMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public TenantSummary get(UUID tenantId) {
        return tenantMapper.toSummary(load(tenantId));
    }

    @Transactional(readOnly = true)
    public List<FeatureStateResponse> featureStates(UUID tenantId) {
        assertInTenantContext(tenantId);
        Map<String, TenantFeature> states = tenantFeatureRepository.findByIdTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantFeature::getFeatureCode, Function.identity()));
        return featureRepository.findAllByOrderByCodeAsc().stream()
                .map(feature -> {
                    TenantFeature state = states.get(feature.getCode());
                    return new FeatureStateResponse(feature.getCode(), feature.getName(), feature.getDescription(),
                            state != null && state.isLicensed(), state != null && state.isEnabled());
                })
                .toList();
    }

    /**
     * @return the previous licence state
     */
    @Transactional
    public boolean setFeatureLicensed(UUID tenantId, String featureCode, boolean licensed) {
        assertInTenantContext(tenantId);
        if (!featureRepository.existsById(featureCode)) {
            throw new BankingException(TenantErrorCode.UNKNOWN_FEATURE);
        }
        TenantFeature feature = tenantFeatureRepository.findById(new TenantFeatureId(tenantId, featureCode))
                .orElseGet(() -> new TenantFeature(tenantId, featureCode, false));
        boolean previous = feature.isLicensed();
        feature.setLicensed(licensed);
        tenantFeatureRepository.saveAndFlush(feature);
        return previous;
    }

    @Transactional(readOnly = true)
    public List<FeatureResponse> catalog() {
        return featureRepository.findAllByOrderByCodeAsc().stream().map(tenantMapper::toResponse).toList();
    }

    private Tenant load(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Institution"));
    }

    private static void assertInTenantContext(UUID tenantId) {
        UUID current = TenantContext.currentTenantId().orElse(null);
        if (!tenantId.equals(current)) {
            throw new IllegalStateException("Operation must run in the context of tenant " + tenantId);
        }
    }
}
