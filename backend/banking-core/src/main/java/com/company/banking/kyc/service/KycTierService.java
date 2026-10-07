package com.company.banking.kyc.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.kyc.dto.KycTierResponse;
import com.company.banking.kyc.dto.UpdateKycTierRequest;
import com.company.banking.kyc.entity.KycTier;
import com.company.banking.kyc.entity.KycTierId;
import com.company.banking.kyc.exception.KycErrorCode;
import com.company.banking.kyc.mapper.KycMapper;
import com.company.banking.kyc.repository.KycTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Institution-configured KYC tiers and the evidence each requires.
 */
@Service
@RequiredArgsConstructor
public class KycTierService {

    private final KycTierRepository repository;
    private final KycMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<KycTierResponse> list() {
        return repository.findByIdTenantIdOrderByTierRankAsc(TenantContext.requireTenantId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public KycTierResponse update(String code, UpdateKycTierRequest request) {
        KycTier tier = repository.findById(new KycTierId(TenantContext.requireTenantId(), code))
                .orElseThrow(() -> new ResourceNotFoundException("KYC tier"));
        ConcurrentModificationException.assertVersion(request.version(), tier.getVersion());
        KycTierResponse before = mapper.toResponse(tier);
        tier.setName(request.name().trim());
        tier.setDescription(request.description());
        tier.setRequiresIdentification(request.requiresIdentification());
        tier.setRequiresIdDocument(request.requiresIdDocument());
        tier.setRequiresSelfie(request.requiresSelfie());
        tier.setRequiresAddress(request.requiresAddress());
        tier.setRequiresProofOfAddress(request.requiresProofOfAddress());
        tier.setRequiresIdentityVerification(request.requiresIdentityVerification());
        tier.setRequiresNextOfKin(request.requiresNextOfKin());
        tier.setRequiresSignature(request.requiresSignature());
        tier.setRequiresEmploymentInfo(request.requiresEmploymentInfo());
        tier.setActive(request.active());
        KycTierResponse after = mapper.toResponse(repository.saveAndFlush(tier));
        auditService.record(AuditEvent.builder("KYC_TIER_UPDATED", "KYC_TIER")
                .resourceId(code)
                .before(before)
                .after(after)
                .build());
        return after;
    }

    @Transactional(readOnly = true)
    public KycTier requireActive(String code) {
        return repository.findById(new KycTierId(TenantContext.requireTenantId(), code))
                .filter(KycTier::isActive)
                .orElseThrow(() -> new BankingException(KycErrorCode.KYC_TIER_NOT_AVAILABLE));
    }

    @Transactional(readOnly = true)
    public int rankOf(String code) {
        return repository.findById(new KycTierId(TenantContext.requireTenantId(), code))
                .map(KycTier::getTierRank)
                .orElse(0);
    }

    /**
     * Default tiers for a new institution. Idempotent.
     */
    @Transactional
    public void provisionDefaults() {
        UUID tenantId = TenantContext.requireTenantId();
        if (repository.existsByIdTenantId(tenantId)) {
            return;
        }
        repository.save(tier(tenantId, "TIER_1", "Basic",
                "Identity number and address; for low-value accounts", 1,
                true, false, false, true, false, false, false, false, false));
        repository.save(tier(tenantId, "TIER_2", "Standard",
                "Verified identity document, photo, address and next of kin", 2,
                true, true, true, true, false, true, true, false, true));
        repository.save(tier(tenantId, "TIER_3", "Enhanced",
                "Standard plus proof of address and signature; for high-value relationships", 3,
                true, true, true, true, true, true, true, true, true));
        repository.flush();
    }

    private static KycTier tier(UUID tenantId, String code, String name, String description, int rank,
                                boolean identification, boolean idDocument, boolean selfie, boolean address,
                                boolean proofOfAddress, boolean identityVerification, boolean nextOfKin,
                                boolean signature, boolean employment) {
        KycTier tier = new KycTier(new KycTierId(tenantId, code));
        tier.setName(name);
        tier.setDescription(description);
        tier.setTierRank(rank);
        tier.setRequiresIdentification(identification);
        tier.setRequiresIdDocument(idDocument);
        tier.setRequiresSelfie(selfie);
        tier.setRequiresAddress(address);
        tier.setRequiresProofOfAddress(proofOfAddress);
        tier.setRequiresIdentityVerification(identityVerification);
        tier.setRequiresNextOfKin(nextOfKin);
        tier.setRequiresSignature(signature);
        tier.setRequiresEmploymentInfo(employment);
        tier.setActive(true);
        return tier;
    }
}
