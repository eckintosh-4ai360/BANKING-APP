package com.company.banking.kyc.service;

import com.company.banking.customer.dto.CustomerKycSnapshot;
import com.company.banking.kyc.dto.RequirementStatus;
import com.company.banking.kyc.entity.KycCheck;
import com.company.banking.kyc.entity.KycTier;
import com.company.banking.kyc.entity.KycTierId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KycRequirementEvaluatorTest {

    private final KycRequirementEvaluator evaluator = new KycRequirementEvaluator();

    @Test
    void uploadedDocumentsAreEnoughToSubmitButMustBeAcceptedToApprove() {
        KycTier tier = standardTier();
        CustomerKycSnapshot customer = individual(Map.of(
                "ID_FRONT", List.of("PENDING_REVIEW"),
                "SELFIE", List.of("REJECTED", "PENDING_REVIEW")));

        List<RequirementStatus> requirements = evaluator.evaluate(tier, customer, List.of());

        assertThat(KycRequirementEvaluator.unmetForSubmission(requirements)).isEmpty();
        assertThat(KycRequirementEvaluator.unmetForApproval(requirements))
                .containsExactly("ID_FRONT", "SELFIE", "IDENTITY_VERIFICATION");
    }

    @Test
    void approvalNeedsAcceptedDocumentsAndAPassedIdentityCheck() {
        KycTier tier = standardTier();
        CustomerKycSnapshot customer = individual(Map.of(
                "ID_FRONT", List.of("ACCEPTED"),
                "SELFIE", List.of("ACCEPTED")));
        KycCheck passed = new KycCheck(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                KycCheck.IDENTITY_VERIFICATION, "ELECTRONIC", "STUB", KycCheck.PASS, null, null, null, null,
                Instant.now());

        List<RequirementStatus> requirements = evaluator.evaluate(tier, customer, List.of(passed));

        assertThat(KycRequirementEvaluator.unmetForApproval(requirements)).isEmpty();
    }

    @Test
    void onlyRejectedDocumentsDoNotCountAsCaptured() {
        CustomerKycSnapshot customer = individual(Map.of("ID_FRONT", List.of("REJECTED")));
        List<RequirementStatus> requirements = evaluator.evaluate(standardTier(), customer, List.of());
        assertThat(KycRequirementEvaluator.unmetForSubmission(requirements)).contains("ID_FRONT", "SELFIE");
    }

    @Test
    void businessesNeedRegistrationDocumentsAndRelatedPartiesButNoSelfieOrNextOfKin() {
        CustomerKycSnapshot business = new CustomerKycSnapshot(UUID.randomUUID(), "0000000018", "BUSINESS", "PENDING",
                "IN_PROGRESS", null, UUID.randomUUID(), "Acme Ltd", true,
                new CustomerKycSnapshot.PrimaryIdentification(UUID.randomUUID(), "BUSINESS_REGISTRATION", false,
                        "UNVERIFIED", false),
                true, Map.of(), false, true, false);

        List<String> codes = evaluator.evaluate(standardTier(), business, List.of()).stream()
                .map(RequirementStatus::code).toList();

        assertThat(codes).contains("RELATED_PARTIES", "BUSINESS_REGISTRATION").doesNotContain("SELFIE",
                "NEXT_OF_KIN", "ID_FRONT");
    }

    @Test
    void anExpiredIdentityDocumentDoesNotSatisfyIdentification() {
        CustomerKycSnapshot customer = new CustomerKycSnapshot(UUID.randomUUID(), "0000000018", "INDIVIDUAL",
                "PENDING", "IN_PROGRESS", null, UUID.randomUUID(), "Ama Mensah", true,
                new CustomerKycSnapshot.PrimaryIdentification(UUID.randomUUID(), "PASSPORT", false, "UNVERIFIED",
                        true),
                true, Map.of(), true, true, false);
        assertThat(KycRequirementEvaluator.unmetForSubmission(
                evaluator.evaluate(standardTier(), customer, List.of()))).contains("IDENTIFICATION");
    }

    private static CustomerKycSnapshot individual(Map<String, List<String>> documents) {
        return new CustomerKycSnapshot(UUID.randomUUID(), "0000000018", "INDIVIDUAL", "PENDING", "IN_PROGRESS",
                null, UUID.randomUUID(), "Ama Mensah", true,
                new CustomerKycSnapshot.PrimaryIdentification(UUID.randomUUID(), "GHANA_CARD", true, "UNVERIFIED",
                        false),
                true, documents, true, true, false);
    }

    private static KycTier standardTier() {
        KycTier tier = new KycTier(new KycTierId(UUID.randomUUID(), "TIER_2"));
        tier.setTierRank(2);
        tier.setRequiresIdentification(true);
        tier.setRequiresIdDocument(true);
        tier.setRequiresSelfie(true);
        tier.setRequiresAddress(true);
        tier.setRequiresIdentityVerification(true);
        tier.setRequiresNextOfKin(true);
        tier.setRequiresEmploymentInfo(true);
        return tier;
    }
}
