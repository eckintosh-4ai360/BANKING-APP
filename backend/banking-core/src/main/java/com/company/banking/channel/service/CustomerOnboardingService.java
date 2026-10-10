package com.company.banking.channel.service;

import com.company.banking.account.dto.AccountResponse;
import com.company.banking.account.dto.OpenAccountRequest;
import com.company.banking.account.service.AccountService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.channel.dto.OnboardingDtos;
import com.company.banking.channel.entity.ChannelSettings;
import com.company.banking.channel.entity.CustomerNotification;
import com.company.banking.channel.entity.CustomerOnboarding;
import com.company.banking.channel.exception.ChannelErrorCode;
import com.company.banking.channel.repository.CustomerOnboardingRepository;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.AddressRequest;
import com.company.banking.customer.dto.AddressResponse;
import com.company.banking.customer.dto.CreateCustomerRequest;
import com.company.banking.customer.dto.CustomerKycSnapshot;
import com.company.banking.customer.dto.CustomerResponse;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.dto.IdentificationRequest;
import com.company.banking.customer.dto.IdentificationResponse;
import com.company.banking.customer.dto.IndividualDetails;
import com.company.banking.customer.dto.IndividualProfileResponse;
import com.company.banking.customer.dto.NextOfKinRequest;
import com.company.banking.customer.dto.NextOfKinResponse;
import com.company.banking.customer.dto.UpdateCustomerRequest;
import com.company.banking.customer.exception.CustomerErrorCode;
import com.company.banking.customer.service.CustomerContactService;
import com.company.banking.customer.service.CustomerDocumentService;
import com.company.banking.customer.service.CustomerIdentityService;
import com.company.banking.customer.service.CustomerKycService;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.customer.service.IdentificationTypeService;
import com.company.banking.kyc.dto.KycCaseResponse;
import com.company.banking.kyc.dto.OpenKycCaseRequest;
import com.company.banking.kyc.dto.RequirementStatus;
import com.company.banking.kyc.exception.KycErrorCode;
import com.company.banking.kyc.service.KycCaseService;
import com.company.banking.tenant.service.TenantService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Signing up in the app, once registered: the person (a PENDING customer) captures their details and documents stage
 * by stage, submits them for KYC and, once a reviewer approves, opens their first account.
 *
 * <p>The twelve stages are phone verification and registration (both done on registering), personal details,
 * identity document, selfie, address, employment, next of kin, signature (where the institution's KYC tier needs
 * one), risk profile, compliance verification (the KYC review by staff) and account activation.
 *
 * <p>What they capture goes through the customer and KYC modules exactly like data captured at a branch, under the
 * same rules: they reach only their own record, change it only while onboarding and never while it is under review.
 * The reviewer is always a member of staff, never the person who submitted.
 */
@Service
@RequiredArgsConstructor
public class CustomerOnboardingService {

    private static final String RESOURCE = "CUSTOMER_SIGN_UP";
    private static final String REJECTED = "REJECTED";

    private final ChannelSettingsService settingsService;
    private final CustomerOnboardingRepository onboardings;
    private final CustomerService customerService;
    private final CustomerContactService contacts;
    private final CustomerIdentityService identities;
    private final CustomerDocumentService documents;
    private final CustomerKycService customerKyc;
    private final IdentificationTypeService idTypes;
    private final KycCaseService kycCases;
    private final AccountService accountService;
    private final TenantService tenantService;
    private final CustomerInbox inbox;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // ------------------------------------------------------------------------------------------- registering

    @Transactional(propagation = Propagation.MANDATORY)
    void requireSignUpOpen() {
        if (!settingsService.current().isSelfOnboardingEnabled()) {
            throw new BankingException(ChannelErrorCode.SIGN_UP_NOT_AVAILABLE);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void requireOldEnough(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now(clock);
        if (dateOfBirth == null || dateOfBirth.isAfter(today)
                || Period.between(dateOfBirth, today).getYears() < settingsService.current().getOnboardingMinAge()) {
            throw new BankingException(ChannelErrorCode.TOO_YOUNG);
        }
    }

    /**
     * The new customer: PENDING, at the institution's sign-up branch, onboarded through the app. The platform creates
     * the record on the person's behalf (they have no identity yet to act as); the sign-up is audited with them as
     * the actor.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    CustomerSummary register(String phone, String firstName, String lastName, LocalDate dateOfBirth) {
        UUID tenantId = TenantContext.requireTenantId();
        ChannelSettings settings = settingsService.current();
        CustomerResponse created = CurrentActor.callAsSystem(tenantId, () -> customerService.create(
                new CreateCustomerRequest("INDIVIDUAL", settings.getOnboardingBranchId(), "MOBILE", phone, null, null,
                        null, new IndividualDetails(null, firstName.trim(), null, lastName.trim(), dateOfBirth, null,
                        null, null, null, null, null, null, null), null)));
        onboardings.saveAndFlush(new CustomerOnboarding(created.id(), tenantId, clock.instant()));
        return customerService.findForChannel(created.id()).orElseThrow();
    }

    // --------------------------------------------------------------------------------------------- capturing

    @Transactional(readOnly = true)
    public OnboardingDtos.Progress progress() {
        UUID customerId = CustomerAuthService.requireCustomer().id();
        return progressOf(customerId, load(customerId));
    }

    @Transactional(readOnly = true)
    public List<OnboardingDtos.IdType> idTypes() {
        CustomerAuthService.requireCustomer();
        return idTypes.list().stream()
                .filter(type -> type.active() && ("ANY".equals(type.appliesTo())
                        || "INDIVIDUAL".equals(type.appliesTo())))
                .map(type -> new OnboardingDtos.IdType(type.code(), type.name(), type.formatHint(),
                        type.requiresExpiry()))
                .toList();
    }

    @Transactional
    public OnboardingDtos.Progress savePersonal(OnboardingDtos.Personal request) {
        UUID customerId = capturing();
        requireOldEnough(request.dateOfBirth());
        CustomerResponse customer = customerService.get(customerId);
        IndividualProfileResponse current = customer.individual();
        customerService.update(customerId, new UpdateCustomerRequest(customer.primaryPhone(), request.email(),
                request.preferredLanguage(), customer.relationshipOfficerId(), new IndividualDetails(request.title(),
                request.firstName(), request.middleName(), request.lastName(), request.dateOfBirth(),
                request.gender(), request.nationality(), request.maritalStatus(), current.occupation(),
                current.employerName(), current.employmentStatus(), current.monthlyIncomeBand(), null), null,
                customer.version()));
        return progressOf(customerId, load(customerId));
    }

    @Transactional
    public OnboardingDtos.Progress saveEmployment(OnboardingDtos.Employment request) {
        UUID customerId = capturing();
        CustomerResponse customer = customerService.get(customerId);
        IndividualProfileResponse current = customer.individual();
        customerService.update(customerId, new UpdateCustomerRequest(customer.primaryPhone(), customer.email(),
                customer.preferredLanguage(), customer.relationshipOfficerId(), new IndividualDetails(current.title(),
                current.firstName(), current.middleName(), current.lastName(), current.dateOfBirth(),
                current.gender(), current.nationality(), current.maritalStatus(), request.occupation(),
                request.employerName(), request.employmentStatus(), request.monthlyIncomeBand(), null), null,
                customer.version()));
        return progressOf(customerId, load(customerId));
    }

    /**
     * The identity document. A new one replaces the one entered before (until the review, nothing is verified).
     */
    @Transactional
    public OnboardingDtos.Progress saveIdentification(OnboardingDtos.Identification request) {
        UUID customerId = capturing();
        for (IdentificationResponse existing : customerService.get(customerId).identifications()) {
            if (existing.active()) {
                identities.remove(customerId, existing.id());
            }
        }
        identities.add(customerId, new IdentificationRequest(request.idTypeCode(), request.idNumber(),
                request.issuingCountry(), request.issueDate(), request.expiryDate(), true));
        return progressOf(customerId, load(customerId));
    }

    /**
     * A photo or scan: the identity document, a selfie, a signature or a proof of address (the content decides the
     * file type; anything but JPEG, PNG or PDF is refused).
     */
    @Transactional
    public OnboardingDtos.Progress uploadDocument(String documentType, String fileName, byte[] content) {
        UUID customerId = capturing();
        documents.upload(customerId, documentType, fileName, content);
        return progressOf(customerId, load(customerId));
    }

    @Transactional
    public OnboardingDtos.Progress saveAddress(OnboardingDtos.Address request) {
        UUID customerId = capturing();
        Optional<AddressResponse> current = customerService.get(customerId).addresses().stream()
                .filter(address -> address.active() && "RESIDENTIAL".equals(address.addressType()))
                .findFirst();
        AddressRequest address = new AddressRequest("RESIDENTIAL", request.line1(), request.line2(), request.city(),
                request.district(), request.region(), tenantService.getCurrent().countryCode(),
                request.digitalAddress(), request.landmark(), true, current.map(AddressResponse::version).orElse(null));
        if (current.isPresent()) {
            contacts.updateAddress(customerId, current.get().id(), address);
        } else {
            contacts.addAddress(customerId, address);
        }
        return progressOf(customerId, load(customerId));
    }

    @Transactional
    public OnboardingDtos.Progress saveNextOfKin(OnboardingDtos.NextOfKin request) {
        UUID customerId = capturing();
        Optional<NextOfKinResponse> current = customerService.get(customerId).nextOfKin().stream()
                .filter(NextOfKinResponse::active)
                .findFirst();
        NextOfKinRequest nextOfKin = new NextOfKinRequest(request.fullName(), request.relationship(), request.phone(),
                request.email(), request.address(), true, current.map(NextOfKinResponse::version).orElse(null));
        if (current.isPresent()) {
            contacts.updateNextOfKin(customerId, current.get().id(), nextOfKin);
        } else {
            contacts.addNextOfKin(customerId, nextOfKin);
        }
        return progressOf(customerId, load(customerId));
    }

    @Transactional
    public OnboardingDtos.Progress saveRiskProfile(OnboardingDtos.RiskProfile request) {
        UUID customerId = capturing();
        CustomerKycSnapshot snapshot = customerKyc.snapshot(customerId);
        if (!"PENDING".equals(snapshot.status())) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                    "Your details can be changed at a branch once you are a customer.");
        }
        if ("PENDING_REVIEW".equals(snapshot.kycStatus())) {
            throw new BankingException(CustomerErrorCode.KYC_UNDER_REVIEW);
        }
        CustomerOnboarding onboarding = lock(customerId);
        onboarding.answerRiskProfile(request.sourceOfFunds(), request.accountPurpose(),
                request.expectedMonthlyTurnover(), request.politicallyExposed(), clock.instant());
        onboardings.saveAndFlush(onboarding);
        auditService.record(AuditEvent.builder("SIGN_UP_RISK_PROFILE_ANSWERED", RESOURCE)
                .resourceId(customerId)
                .after(riskProfileOf(onboarding))
                .build());
        return progressOf(customerId, onboarding);
    }

    // ---------------------------------------------------------------------------------------------- submitting

    /**
     * Submits the details for KYC review: opens the case at the institution's sign-up tier (or reuses the one returned
     * for correction), records a declared politically exposed person as a screening match, runs electronic identity
     * verification where the document supports it, then submits. Submitting again while in review changes nothing.
     */
    public OnboardingDtos.Progress submit() {
        UUID customerId = CustomerAuthService.requireCustomer().id();
        Optional<UUID> caseToSubmit = transactionTemplate.execute(status -> prepareCase(customerId));
        if (caseToSubmit.isPresent()) {
            UUID caseId = caseToSubmit.get();
            verifyIdentity(caseId);
            transactionTemplate.executeWithoutResult(status -> {
                KycCaseResponse submitted = kycCases.submit(caseId);
                CustomerOnboarding onboarding = lock(customerId);
                onboarding.submitted(clock.instant());
                onboardings.saveAndFlush(onboarding);
                auditService.record(AuditEvent.builder("SIGN_UP_SUBMITTED", RESOURCE)
                        .resourceId(customerId)
                        .resourceReference(submitted.customerNumber())
                        .metadata("kycCaseId", caseId)
                        .build());
            });
        }
        return transactionTemplate.execute(status -> progressOf(customerId, load(customerId)));
    }

    /**
     * @return the case to submit; empty when it is already in review
     */
    private Optional<UUID> prepareCase(UUID customerId) {
        CustomerOnboarding onboarding = lock(customerId);
        if (onboarding.isCompleted()) {
            throw new BankingException(ChannelErrorCode.NOT_SIGNING_UP);
        }
        List<String> missing = new ArrayList<>();
        if (!personalDone(customerService.get(customerId).individual())) {
            missing.add("PERSONAL_DETAILS");
        }
        if (!onboarding.hasRiskProfile()) {
            missing.add("RISK_PROFILE");
        }
        if (!missing.isEmpty()) {
            throw new BankingException(ChannelErrorCode.SIGN_UP_INCOMPLETE,
                    "Finish these steps first: " + String.join(", ", missing) + ".");
        }
        KycCaseResponse kycCase = onboarding.getKycCaseId() == null ? null : kycCases.get(onboarding.getKycCaseId());
        if (kycCase != null) {
            switch (kycCase.status()) {
                case "PENDING_REVIEW", "APPROVED" -> {
                    return Optional.empty();
                }
                case REJECTED -> throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                        "Your sign-up was not approved. Contact us for help.");
                case "CANCELLED" -> kycCase = null;
                default -> {
                    // OPEN or RETURNED: captured again, submitted again.
                }
            }
        }
        if (kycCase == null) {
            String tierCode = settingsService.current().getOnboardingTierCode();
            if (tierCode == null) {
                throw new BankingException(ChannelErrorCode.SIGN_UP_NOT_AVAILABLE);
            }
            kycCase = kycCases.open(customerId, new OpenKycCaseRequest("ONBOARDING", tierCode));
            onboarding.caseOpened(kycCase.id(), clock.instant());
            onboardings.saveAndFlush(onboarding);
        }
        boolean declared = kycCase.checks().stream()
                .anyMatch(check -> "PEP".equals(check.checkType()) && "DECLARED".equals(check.method()));
        if (Boolean.TRUE.equals(onboarding.getPoliticallyExposed()) && !declared) {
            kycCases.recordDeclaration(kycCase.id(), "PEP", "FAIL", "Declared by the customer when signing up in"
                    + " the app: politically exposed, or family or a close associate of someone who is.");
        }
        return Optional.of(kycCase.id());
    }

    /**
     * Electronic identity verification, outside any transaction (the provider may be slow), when the document
     * supports it and it has not passed yet. Without a provider for the document, the reviewer verifies it.
     */
    private void verifyIdentity(UUID caseId) {
        boolean needed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            KycCaseResponse kycCase = kycCases.get(caseId);
            CustomerKycSnapshot.PrimaryIdentification identification =
                    customerKyc.snapshot(kycCase.customerId()).primaryIdentification();
            boolean passed = kycCase.checks().stream().anyMatch(check ->
                    "IDENTITY_VERIFICATION".equals(check.checkType()) && "PASS".equals(check.result()));
            return identification != null && identification.supportsElectronicVerification() && !passed;
        }));
        if (!needed) {
            return;
        }
        try {
            kycCases.runIdentityCheck(caseId);
        } catch (BankingException ex) {
            if (ex.getErrorCode() != KycErrorCode.IDENTITY_VERIFICATION_UNAVAILABLE) {
                throw ex;
            }
        }
    }

    // ------------------------------------------------------------------------------------------ first account

    /**
     * Opens the first account, of the institution's sign-up product, once the details are approved. Opening it
     * again returns the same account.
     */
    @Transactional
    public OnboardingDtos.Progress openAccount() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        UUID tenantId = TenantContext.requireTenantId();
        CustomerOnboarding onboarding = lock(actor.id());
        if (!onboarding.isCompleted()) {
            CustomerSummary customer = customerService.findForChannel(actor.id()).orElseThrow();
            if (!"ACTIVE".equals(customer.status())) {
                throw new BankingException(ChannelErrorCode.NOT_VERIFIED_YET);
            }
            UUID productId = settingsService.current().getOnboardingProductId();
            if (productId == null) {
                throw new BankingException(ChannelErrorCode.SIGN_UP_NOT_AVAILABLE);
            }
            // The institution chose the product; the platform opens it on the customer's behalf at their branch.
            AccountResponse account = CurrentActor.callAsSystem(tenantId, () -> accountService.open(
                    new OpenAccountRequest(actor.id(), productId, null, "SINGLE", null, List.of())));
            onboarding.completed(account.id(), clock.instant());
            onboardings.saveAndFlush(onboarding);
            auditService.record(AuditEvent.builder("SIGN_UP_ACCOUNT_OPENED", RESOURCE)
                    .resourceId(actor.id())
                    .resourceReference(customer.customerNumber())
                    .metadata("accountId", account.id())
                    .metadata("accountNumber", account.accountNumber())
                    .build());
            inbox.post(actor.id(), new CustomerInbox.Message(CustomerNotification.Category.ACCOUNT,
                    "Your account is open", "Your " + account.productName() + " account " + account.accountNumber()
                    + " is open. Welcome!", "ACCOUNT", account.id(), null));
        }
        return progressOf(actor.id(), onboarding);
    }

    // ---------------------------------------------------------------------------------------------------- staff

    /**
     * A customer's sign-up in the app (risk profile answers and progress), for staff reviewing them.
     */
    @Transactional(readOnly = true)
    public OnboardingDtos.SignUpRecord forStaff(UUID customerId) {
        customerService.getSummary(customerId);
        CustomerOnboarding onboarding = onboardings.findByTenantIdAndCustomerId(TenantContext.requireTenantId(),
                customerId).orElseThrow(() -> new ResourceNotFoundException("Sign-up"));
        return new OnboardingDtos.SignUpRecord(customerId, onboarding.getCreatedAt(), onboarding.getSourceOfFunds(),
                onboarding.getAccountPurpose(), onboarding.getExpectedMonthlyTurnover(),
                onboarding.getPoliticallyExposed(), onboarding.getRiskAnsweredAt(), onboarding.getKycCaseId(),
                onboarding.getSubmittedAt(), onboarding.getAccountId(), onboarding.getCompletedAt());
    }

    // ---------------------------------------------------------------------------------------------------------

    private UUID capturing() {
        UUID customerId = CustomerAuthService.requireCustomer().id();
        if (load(customerId).isCompleted()) {
            throw new BankingException(ChannelErrorCode.NOT_SIGNING_UP);
        }
        return customerId;
    }

    private CustomerOnboarding load(UUID customerId) {
        return onboardings.findByTenantIdAndCustomerId(TenantContext.requireTenantId(), customerId)
                .orElseThrow(() -> new BankingException(ChannelErrorCode.NOT_SIGNING_UP));
    }

    private CustomerOnboarding lock(UUID customerId) {
        return onboardings.lock(TenantContext.requireTenantId(), customerId)
                .orElseThrow(() -> new BankingException(ChannelErrorCode.NOT_SIGNING_UP));
    }

    private OnboardingDtos.Progress progressOf(UUID customerId, CustomerOnboarding onboarding) {
        CustomerResponse customer = customerService.get(customerId);
        CustomerKycSnapshot snapshot = customerKyc.snapshot(customerId);
        KycCaseResponse kycCase = onboarding.getKycCaseId() == null ? null : kycCases.get(onboarding.getKycCaseId());
        String tierCode = kycCase != null ? kycCase.targetTierCode() : settingsService.current().getOnboardingTierCode();
        List<RequirementStatus> requirements = kycCase != null ? kycCase.requirements()
                : tierCode == null ? List.of() : kycCases.requirementsFor(customerId, tierCode);
        Set<String> required = requirements.stream().map(RequirementStatus::code).collect(Collectors.toSet());
        IndividualProfileResponse profile = customer.individual();

        List<OnboardingDtos.Stage> stages = new ArrayList<>();
        stages.add(stage("PHONE", "Verify your phone", true, true));
        stages.add(stage("REGISTRATION", "Register", true, true));
        stages.add(stage("PERSONAL_DETAILS", "Personal details", true, personalDone(profile)));
        stages.add(stage("IDENTITY_DOCUMENT", "Identity document",
                required.contains("IDENTIFICATION") || required.contains("ID_FRONT"),
                snapshot.hasActiveIdentification() && captured(snapshot, "ID_FRONT")));
        stages.add(stage("SELFIE", "Selfie", required.contains("SELFIE"), captured(snapshot, "SELFIE")));
        stages.add(stage("ADDRESS", "Address", required.contains("ADDRESS") || required.contains("PROOF_OF_ADDRESS"),
                snapshot.hasActiveAddress()
                        && (!required.contains("PROOF_OF_ADDRESS") || captured(snapshot, "PROOF_OF_ADDRESS"))));
        stages.add(stage("EMPLOYMENT", "Work or business", required.contains("EMPLOYMENT_INFO"),
                snapshot.hasEmploymentInfo()));
        stages.add(stage("NEXT_OF_KIN", "Next of kin", required.contains("NEXT_OF_KIN"), snapshot.hasNextOfKin()));
        stages.add(stage("SIGNATURE", "Signature", required.contains("SIGNATURE"), captured(snapshot, "SIGNATURE")));
        stages.add(stage("RISK_PROFILE", "About your account", true, onboarding.hasRiskProfile()));
        stages.add(new OnboardingDtos.Stage("COMPLIANCE", "Verification", true, reviewState(kycCase)));
        stages.add(stage("ACTIVATION", "Open your account", true, onboarding.isCompleted()));

        AccountResponse account = onboarding.getAccountId() == null ? null
                : accountService.get(onboarding.getAccountId());
        Optional<AddressResponse> address = customer.addresses().stream()
                .filter(found -> found.active() && "RESIDENTIAL".equals(found.addressType())).findFirst();
        Optional<NextOfKinResponse> nextOfKin = customer.nextOfKin().stream()
                .filter(NextOfKinResponse::active).findFirst();
        Optional<IdentificationResponse> identification = customer.identifications().stream()
                .filter(IdentificationResponse::active).findFirst();
        return new OnboardingDtos.Progress(customer.customerNumber(), customer.status(), customer.kycStatus(),
                nextStage(stages), stages,
                requirements.stream().map(requirement -> new OnboardingDtos.Requirement(requirement.code(),
                        requirement.description(), requirement.metForSubmission(), requirement.metForApproval()))
                        .toList(),
                kycCase == null || kycCase.submittedAt() == null ? null : new OnboardingDtos.Review(kycCase.status(),
                        kycCase.submittedAt(), "RETURNED".equals(kycCase.status()) ? kycCase.decisionNote() : null),
                new OnboardingDtos.Profile(profile.title(), profile.firstName(), profile.middleName(),
                        profile.lastName(), profile.dateOfBirth(), profile.gender(), profile.nationality(),
                        profile.maritalStatus(), customer.email(), customer.preferredLanguage(),
                        profile.employmentStatus(), profile.occupation(), profile.employerName(),
                        profile.monthlyIncomeBand()),
                address.map(found -> new OnboardingDtos.Address(found.line1(), found.line2(), found.city(),
                        found.district(), found.region(), found.digitalAddress(), found.landmark())).orElse(null),
                nextOfKin.map(found -> new OnboardingDtos.NextOfKin(found.fullName(), found.relationship(),
                        found.phone(), found.email(), found.address())).orElse(null),
                identification.map(found -> new OnboardingDtos.IdentificationView(found.idTypeCode(),
                        found.idNumberMasked(), found.issuingCountry(), found.issueDate(), found.expiryDate(),
                        found.verificationStatus())).orElse(null),
                customer.documents().stream().map(document -> new OnboardingDtos.DocumentView(document.id(),
                        document.documentType(), document.reviewStatus(), document.uploadedAt())).toList(),
                onboarding.hasRiskProfile() ? riskProfileOf(onboarding) : null,
                account == null ? null : new OnboardingDtos.OpenedAccount(account.id(), account.accountNumber(),
                        account.productName(), account.currency(), account.status()));
    }

    private static OnboardingDtos.Stage stage(String code, String title, boolean required, boolean done) {
        return new OnboardingDtos.Stage(code, title, required, done ? "DONE" : "TO_DO");
    }

    private static String reviewState(KycCaseResponse kycCase) {
        if (kycCase == null) {
            return "TO_DO";
        }
        return switch (kycCase.status()) {
            case "PENDING_REVIEW" -> "IN_REVIEW";
            case "RETURNED" -> "ACTION_NEEDED";
            case "APPROVED" -> "DONE";
            case REJECTED -> "NOT_APPROVED";
            default -> "TO_DO";
        };
    }

    /**
     * The first required stage still to do; while the details are in review, the review; none once not approved.
     */
    private static String nextStage(List<OnboardingDtos.Stage> stages) {
        Function<String, String> stateOf = code -> stages.stream().filter(stage -> stage.code().equals(code))
                .map(OnboardingDtos.Stage::state).findFirst().orElse("TO_DO");
        String review = stateOf.apply("COMPLIANCE");
        if ("NOT_APPROVED".equals(review)) {
            return null;
        }
        if ("IN_REVIEW".equals(review)) {
            return "COMPLIANCE";
        }
        return stages.stream()
                .filter(stage -> stage.required() && !"DONE".equals(stage.state()))
                .map(OnboardingDtos.Stage::code)
                .findFirst().orElse(null);
    }

    private static boolean personalDone(IndividualProfileResponse profile) {
        return profile != null && profile.gender() != null && profile.nationality() != null;
    }

    private static boolean captured(CustomerKycSnapshot snapshot, String documentType) {
        return snapshot.documentReviewStatuses().getOrDefault(documentType, List.of()).stream()
                .anyMatch(status -> !REJECTED.equals(status));
    }

    private static OnboardingDtos.RiskProfile riskProfileOf(CustomerOnboarding onboarding) {
        return new OnboardingDtos.RiskProfile(onboarding.getSourceOfFunds(), onboarding.getAccountPurpose(),
                onboarding.getExpectedMonthlyTurnover(), onboarding.getPoliticallyExposed());
    }
}
