package com.company.banking.kyc.service;

import com.company.banking.customer.dto.CustomerKycSnapshot;
import com.company.banking.kyc.dto.RequirementStatus;
import com.company.banking.kyc.entity.KycCheck;
import com.company.banking.kyc.entity.KycTier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a tier's configuration into concrete requirements and checks them against the customer's data.
 *
 * <p>Submission needs the evidence captured (documents uploaded and not rejected). Approval needs it accepted
 * (documents accepted by a reviewer, identity verification passed).
 */
@Component
public class KycRequirementEvaluator {

    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";

    public List<RequirementStatus> evaluate(KycTier tier, CustomerKycSnapshot customer, List<KycCheck> checks) {
        boolean individual = "INDIVIDUAL".equals(customer.customerType());
        List<RequirementStatus> requirements = new ArrayList<>();

        if (tier.isRequiresIdentification()) {
            boolean valid = customer.hasActiveIdentification() && customer.primaryIdentification() != null
                    && !customer.primaryIdentification().expired();
            requirements.add(new RequirementStatus("IDENTIFICATION", "A valid, unexpired identity document number",
                    valid, valid));
            if (!individual) {
                requirements.add(new RequirementStatus("RELATED_PARTIES",
                        "At least one director, owner or signatory", customer.hasRelatedParties(),
                        customer.hasRelatedParties()));
            }
        }
        if (tier.isRequiresIdDocument()) {
            String type = individual ? "ID_FRONT" : "BUSINESS_REGISTRATION";
            requirements.add(document(customer, type, individual
                    ? "Image of the identity document"
                    : "Business registration certificate"));
        }
        if (tier.isRequiresSelfie() && individual) {
            requirements.add(document(customer, "SELFIE", "Photograph of the customer"));
        }
        if (tier.isRequiresAddress()) {
            requirements.add(new RequirementStatus("ADDRESS", "A current address", customer.hasActiveAddress(),
                    customer.hasActiveAddress()));
        }
        if (tier.isRequiresProofOfAddress()) {
            requirements.add(document(customer, "PROOF_OF_ADDRESS", "Proof of address"));
        }
        if (tier.isRequiresIdentityVerification()) {
            boolean verified = checks.stream().anyMatch(check ->
                    KycCheck.IDENTITY_VERIFICATION.equals(check.getCheckType()) && check.passed());
            requirements.add(new RequirementStatus("IDENTITY_VERIFICATION",
                    "Identity verified electronically or by a reviewer", true, verified));
        }
        if (tier.isRequiresNextOfKin() && individual) {
            requirements.add(new RequirementStatus("NEXT_OF_KIN", "A next of kin", customer.hasNextOfKin(),
                    customer.hasNextOfKin()));
        }
        if (tier.isRequiresSignature()) {
            requirements.add(document(customer, "SIGNATURE", "Specimen signature"));
        }
        if (tier.isRequiresEmploymentInfo()) {
            requirements.add(new RequirementStatus("EMPLOYMENT_INFO", individual
                    ? "Employment or occupation details"
                    : "Business sector", customer.hasEmploymentInfo(), customer.hasEmploymentInfo()));
        }
        return requirements;
    }

    public static List<String> unmetForSubmission(List<RequirementStatus> requirements) {
        return requirements.stream().filter(requirement -> !requirement.metForSubmission())
                .map(RequirementStatus::code).toList();
    }

    public static List<String> unmetForApproval(List<RequirementStatus> requirements) {
        return requirements.stream().filter(requirement -> !requirement.metForApproval())
                .map(RequirementStatus::code).toList();
    }

    private static RequirementStatus document(CustomerKycSnapshot customer, String type, String description) {
        List<String> statuses = customer.documentReviewStatuses().getOrDefault(type, List.of());
        boolean captured = statuses.stream().anyMatch(status -> !REJECTED.equals(status));
        boolean accepted = statuses.contains(ACCEPTED);
        return new RequirementStatus(type, description, captured, accepted);
    }
}
