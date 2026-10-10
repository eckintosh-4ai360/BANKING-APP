package com.company.banking.tenant.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.tenant.dto.FeatureStateResponse;
import com.company.banking.tenant.dto.InstitutionProfileResponse;
import com.company.banking.tenant.dto.InstitutionResponse;
import com.company.banking.tenant.dto.PublicBrandingResponse;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.dto.UpdateBrandingRequest;
import com.company.banking.tenant.dto.UpdateInstitutionProfileRequest;
import com.company.banking.tenant.entity.Feature;
import com.company.banking.tenant.entity.InstitutionProfile;
import com.company.banking.tenant.entity.Tenant;
import com.company.banking.tenant.entity.TenantFeature;
import com.company.banking.tenant.entity.TenantFeatureId;
import com.company.banking.tenant.exception.TenantErrorCode;
import com.company.banking.tenant.mapper.TenantMapper;
import com.company.banking.tenant.repository.FeatureRepository;
import com.company.banking.tenant.repository.InstitutionProfileRepository;
import com.company.banking.tenant.repository.TenantFeatureRepository;
import com.company.banking.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The institution's own view and management of its profile, branding and features.
 */
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final TenantRepository tenantRepository;
    private final InstitutionProfileRepository profileRepository;
    private final FeatureRepository featureRepository;
    private final TenantFeatureRepository tenantFeatureRepository;
    private final TenantService tenantService;
    private final TenantMapper tenantMapper;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public InstitutionResponse getInstitution() {
        UUID tenantId = TenantContext.requireTenantId();
        Tenant tenant = loadTenant(tenantId);
        return new InstitutionResponse(
                tenantMapper.toSummary(tenant),
                tenantMapper.toResponse(loadProfile(tenantId)),
                featureStates(tenantId));
    }

    @Transactional
    public InstitutionProfileResponse updateProfile(UpdateInstitutionProfileRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Tenant tenant = loadTenant(tenantId);
        InstitutionProfile profile = loadProfile(tenantId);
        ConcurrentModificationException.assertVersion(request.version(), profile.getVersion());

        InstitutionProfileResponse before = tenantMapper.toResponse(profile);
        String previousName = tenant.getDisplayName();
        tenant.rename(request.displayName().trim());
        profile.updateContact(request.contactEmail(), request.contactPhone(), request.supportEmail(),
                request.supportPhone(), request.websiteUrl(), request.addressLine1(), request.addressLine2(),
                request.city(), request.region(), request.digitalAddress());
        profileRepository.saveAndFlush(profile);
        tenantRepository.saveAndFlush(tenant);

        InstitutionProfileResponse after = tenantMapper.toResponse(profile);
        auditService.record(AuditEvent.builder("INSTITUTION_PROFILE_UPDATED", "INSTITUTION")
                .resourceId(tenantId)
                .resourceReference(tenant.getCode())
                .before(Map.of("displayName", previousName, "profile", before))
                .after(Map.of("displayName", tenant.getDisplayName(), "profile", after))
                .build());
        return after;
    }

    @Transactional
    public InstitutionProfileResponse updateBranding(UpdateBrandingRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        InstitutionProfile profile = loadProfile(tenantId);
        ConcurrentModificationException.assertVersion(request.version(), profile.getVersion());

        InstitutionProfileResponse before = tenantMapper.toResponse(profile);
        profile.updateBranding(request.logoUrl(), request.primaryColor().toUpperCase(Locale.ROOT),
                request.secondaryColor().toUpperCase(Locale.ROOT), request.smsSenderId(), request.emailSenderName(),
                request.emailSenderAddress());
        profileRepository.saveAndFlush(profile);

        InstitutionProfileResponse after = tenantMapper.toResponse(profile);
        auditService.record(AuditEvent.builder("INSTITUTION_BRANDING_UPDATED", "INSTITUTION")
                .resourceId(tenantId)
                .before(before)
                .after(after)
                .build());
        return after;
    }

    @Transactional
    public FeatureStateResponse setFeatureEnabled(String featureCode, boolean enabled) {
        UUID tenantId = TenantContext.requireTenantId();
        Feature feature = featureRepository.findById(featureCode)
                .orElseThrow(() -> new BankingException(TenantErrorCode.UNKNOWN_FEATURE));
        TenantFeature tenantFeature = tenantFeatureRepository.findById(new TenantFeatureId(tenantId, featureCode))
                .orElseThrow(() -> new BankingException(TenantErrorCode.FEATURE_NOT_LICENSED));
        boolean previous = tenantFeature.isEnabled();
        tenantFeature.setEnabled(enabled);
        tenantFeatureRepository.saveAndFlush(tenantFeature);

        if (previous != enabled) {
            auditService.record(AuditEvent.builder(enabled ? "FEATURE_ENABLED" : "FEATURE_DISABLED", "FEATURE")
                    .resourceId(featureCode)
                    .before(Map.of("enabled", previous))
                    .after(Map.of("enabled", enabled))
                    .build());
        }
        return toState(feature, tenantFeature);
    }

    /**
     * Whether the current institution has a feature switched on (and licensed by the platform).
     */
    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String featureCode) {
        return tenantFeatureRepository.findById(new TenantFeatureId(TenantContext.requireTenantId(), featureCode))
                .map(feature -> feature.isEnabled() && feature.isLicensed())
                .orElse(false);
    }

    /**
     * Unauthenticated white-label bootstrap. Only active institutions are visible.
     */
    public PublicBrandingResponse getPublicBranding(String tenantCode) {
        TenantSummary tenant = tenantService.findByCode(tenantCode)
                .filter(TenantSummary::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Institution"));
        return TenantContext.callAs(tenant.id(), () -> transactionTemplate.execute(status -> {
            InstitutionProfile profile = loadProfile(tenant.id());
            List<String> enabledFeatures = tenantFeatureRepository.findByIdTenantId(tenant.id()).stream()
                    .filter(TenantFeature::isEnabled)
                    .map(TenantFeature::getFeatureCode)
                    .sorted()
                    .toList();
            return new PublicBrandingResponse(tenant.code(), tenant.displayName(), profile.getLogoUrl(),
                    profile.getPrimaryColor(), profile.getSecondaryColor(), profile.getSupportEmail(),
                    profile.getSupportPhone(), tenant.baseCurrency(), tenant.locale(), enabledFeatures);
        }));
    }

    private List<FeatureStateResponse> featureStates(UUID tenantId) {
        Map<String, TenantFeature> states = tenantFeatureRepository.findByIdTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantFeature::getFeatureCode, Function.identity()));
        return featureRepository.findAllByOrderByCodeAsc().stream()
                .map(feature -> toState(feature, states.get(feature.getCode())))
                .toList();
    }

    private static FeatureStateResponse toState(Feature feature, TenantFeature state) {
        return new FeatureStateResponse(feature.getCode(), feature.getName(), feature.getDescription(),
                state != null && state.isLicensed(), state != null && state.isEnabled());
    }

    private Tenant loadTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Institution"));
    }

    private InstitutionProfile loadProfile(UUID tenantId) {
        return profileRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution profile"));
    }
}
