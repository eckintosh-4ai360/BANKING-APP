package com.company.banking.channel.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Signing up in the app, after registering: the stages, what has been captured, and the requests that capture it.
 */
public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    /**
     * Where the person is in signing up.
     *
     * @param nextStage    the stage to do next; null once everything is done or the sign-up was not approved
     * @param requirements the evidence the institution's KYC tier asks for, and whether it is captured and accepted
     * @param review       the KYC review, once submitted
     * @param account      the first account, once opened
     */
    public record Progress(String customerNumber, String status, String kycStatus, String nextStage,
                           List<Stage> stages, List<Requirement> requirements, Review review, Profile profile,
                           Address address, NextOfKin nextOfKin, IdentificationView identification,
                           List<DocumentView> documents, RiskProfile riskProfile, OpenedAccount account) {
    }

    /**
     * @param required whether the institution asks for it (signature, for example, only where its KYC tier needs one)
     * @param state    DONE, TO_DO, IN_REVIEW, ACTION_NEEDED or NOT_APPROVED
     */
    public record Stage(String code, String title, boolean required, String state) {
    }

    public record Requirement(String code, String description, boolean captured, boolean accepted) {
    }

    /**
     * @param note what the reviewer asked to correct (only while the details are returned for correction)
     */
    public record Review(String status, Instant submittedAt, String note) {
    }

    public record Profile(String title, String firstName, String middleName, String lastName, LocalDate dateOfBirth,
                          String gender, String nationality, String maritalStatus, String email,
                          String preferredLanguage, String employmentStatus, String occupation, String employerName,
                          String monthlyIncomeBand) {
    }

    public record IdentificationView(String idTypeCode, String idNumberMasked, String issuingCountry,
                                     LocalDate issueDate, LocalDate expiryDate, String verificationStatus) {
    }

    /**
     * @param reviewStatus PENDING until staff review it, then ACCEPTED or REJECTED (a rejected one is uploaded again)
     */
    public record DocumentView(UUID id, String documentType, String reviewStatus, Instant uploadedAt) {
    }

    public record OpenedAccount(UUID id, String accountNumber, String productName, String currency, String status) {
    }

    public record IdType(String code, String name, String formatHint, boolean requiresExpiry) {
    }

    // ----------------------------------------------------------------------------------------------- requests

    public record Personal(
            @Size(max = 20) String title,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "^(MALE|FEMALE|OTHER|UNDISCLOSED)$") String gender,
            @NotBlank @Pattern(regexp = ValidationPatterns.COUNTRY_CODE) String nationality,
            @Pattern(regexp = "^(SINGLE|MARRIED|DIVORCED|WIDOWED|SEPARATED|UNDISCLOSED)$") String maritalStatus,
            @Email @Size(max = 254) String email,
            @Size(max = 10) String preferredLanguage) {

        @Override
        public String toString() {
            return "Personal[***]";
        }
    }

    public record Employment(
            @NotBlank @Pattern(regexp = "^(EMPLOYED|SELF_EMPLOYED|UNEMPLOYED|STUDENT|RETIRED|OTHER)$")
            String employmentStatus,
            @Size(max = 100) String occupation,
            @Size(max = 150) String employerName,
            @Size(max = 30) String monthlyIncomeBand) {
    }

    /**
     * The identity document; a new one replaces the one entered before.
     */
    public record Identification(
            @NotBlank @Size(max = 30) String idTypeCode,
            @NotBlank @Size(max = 50) String idNumber,
            @Pattern(regexp = ValidationPatterns.COUNTRY_CODE) String issuingCountry,
            @PastOrPresent LocalDate issueDate,
            LocalDate expiryDate) {

        @Override
        public String toString() {
            return "Identification[idTypeCode=" + idTypeCode + ", idNumber=***]";
        }
    }

    /**
     * Where the person lives, in the institution's country.
     */
    public record Address(
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 100) String region,
            @Pattern(regexp = ValidationPatterns.DIGITAL_ADDRESS) String digitalAddress,
            @Size(max = 200) String landmark) {
    }

    public record NextOfKin(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 50) String relationship,
            @Pattern(regexp = ValidationPatterns.PHONE) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 300) String address) {

        @Override
        public String toString() {
            return "NextOfKin[relationship=" + relationship + ", ***]";
        }
    }

    /**
     * What the account is for and where the money comes from, for the reviewer's risk rating.
     *
     * @param politicallyExposed the person holds (or recently held) a prominent public position, or is family or a
     *                           close associate of someone who does
     */
    public record RiskProfile(
            @NotBlank @Pattern(regexp = "^(SALARY|BUSINESS|FARMING|TRADING|REMITTANCES|PENSION|FAMILY_SUPPORT"
                    + "|SAVINGS|OTHER)$") String sourceOfFunds,
            @NotBlank @Pattern(regexp = "^(SAVINGS|RECEIVING_SALARY|BUSINESS_PAYMENTS|SUSU|LOANS|REMITTANCES|OTHER)$")
            String accountPurpose,
            @NotBlank @Pattern(regexp = "^(UP_TO_1000|UP_TO_5000|UP_TO_20000|UP_TO_100000|ABOVE_100000)$")
            String expectedMonthlyTurnover,
            @NotNull Boolean politicallyExposed) {
    }

    // ---------------------------------------------------------------------------------------------------- staff

    /**
     * A customer's sign-up in the app, for staff reviewing them.
     */
    public record SignUpRecord(UUID customerId, Instant startedAt, String sourceOfFunds, String accountPurpose,
                               String expectedMonthlyTurnover, Boolean politicallyExposed, Instant riskAnsweredAt,
                               UUID kycCaseId, Instant submittedAt, UUID accountId, Instant completedAt) {
    }
}
