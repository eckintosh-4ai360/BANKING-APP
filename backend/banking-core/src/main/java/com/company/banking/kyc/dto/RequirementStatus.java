package com.company.banking.kyc.dto;

/**
 * One evidence requirement of the target tier.
 *
 * @param metForSubmission captured well enough to submit for review
 * @param metForApproval   accepted by a reviewer, as needed to approve
 */
public record RequirementStatus(String code, String description, boolean metForSubmission, boolean metForApproval) {
}
