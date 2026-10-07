package com.company.banking.platform.seed;

import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.dto.CreateBranchRequest;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.AddressRequest;
import com.company.banking.customer.dto.CreateCustomerRequest;
import com.company.banking.customer.dto.CustomerResponse;
import com.company.banking.customer.dto.IdentificationRequest;
import com.company.banking.customer.dto.IndividualDetails;
import com.company.banking.customer.service.CustomerContactService;
import com.company.banking.customer.service.CustomerIdentityService;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.iam.dto.RoleResponse;
import com.company.banking.iam.service.DefaultRoleCatalog;
import com.company.banking.iam.service.PlatformUserService;
import com.company.banking.iam.service.RoleService;
import com.company.banking.iam.service.StaffCredentialService;
import com.company.banking.platform.dto.OnboardTenantRequest;
import com.company.banking.platform.dto.OnboardTenantResponse;
import com.company.banking.platform.service.PlatformTenantService;
import com.company.banking.staff.dto.CreateStaffRequest;
import com.company.banking.staff.dto.StaffCreatedResponse;
import com.company.banking.staff.service.StaffService;
import com.company.banking.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates a demo institution with branches and one user per default role. Runs only with the {@code local}
 * profile and only once (skipped when the demo institution exists). The demo passwords are documented in the
 * README and must never be used anywhere else.
 */
@Slf4j
@Order(10)
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_TENANT = "demo-mfi";
    static final String DEMO_STAFF_PASSWORD = "Demo@Pass2026";
    static final String DEMO_PLATFORM_PASSWORD = "Platform@Owner2026";

    private final TenantService tenantService;
    private final PlatformUserService platformUserService;
    private final PlatformTenantService platformTenantService;
    private final BranchService branchService;
    private final RoleService roleService;
    private final StaffService staffService;
    private final StaffCredentialService credentialService;
    private final CustomerService customerService;
    private final CustomerContactService contactService;
    private final CustomerIdentityService identityService;

    @Override
    public void run(ApplicationArguments args) {
        if (tenantService.findByCode(DEMO_TENANT).isPresent()) {
            log.info("Demo data already present; skipping seeding");
            return;
        }
        CurrentActor.callAsSystem(null, () -> {
            platformUserService.bootstrapOwnerIfAbsent("superadmin", "superadmin@platform.local",
                    "Platform Owner", DEMO_PLATFORM_PASSWORD, false);
            OnboardTenantResponse onboarded = platformTenantService.onboard(demoInstitution());
            UUID tenantId = onboarded.tenant().id();
            TenantContext.runAs(tenantId, () -> CurrentActor.callAsSystem(tenantId,
                    () -> seedInstitution(onboarded)));
            return null;
        });
        log.info("Demo institution '{}' created. Demo credentials are listed in README.md (local use only).",
                DEMO_TENANT);
    }

    private Void seedInstitution(OnboardTenantResponse onboarded) {
        credentialService.setPasswordForSeeding(onboarded.administratorId(), DEMO_STAFF_PASSWORD);
        UUID headOffice = onboarded.headOffice().id();
        BranchResponse tema = branchService.create(new CreateBranchRequest("TEMA", "Tema Branch", "BRANCH",
                "+233303000001", "tema@demo-mfi.local", "Community 1", null, "Tema", "Greater Accra",
                "GT-001-0001", LocalDate.of(2024, 3, 1)));
        BranchResponse kumasi = branchService.create(new CreateBranchRequest("KUMASI", "Kumasi Branch", "BRANCH",
                "+233322000001", "kumasi@demo-mfi.local", "Adum", null, "Kumasi", "Ashanti", "AK-039-5028",
                LocalDate.of(2024, 6, 1)));
        Map<String, UUID> roles = roleService.list().stream()
                .collect(Collectors.toMap(RoleResponse::code, RoleResponse::id));

        createStaff("EMP-0002", "Ama", "Owusu", "manager", "Branch Manager", headOffice, false,
                roles.get(DefaultRoleCatalog.BRANCH_MANAGER));
        createStaff("EMP-0003", "Kwame", "Asante", "teller", "Teller", headOffice, false,
                roles.get(DefaultRoleCatalog.TELLER));
        createStaff("EMP-0004", "Efua", "Mensah", "loanofficer", "Loan Officer", tema.id(), false,
                roles.get(DefaultRoleCatalog.LOAN_OFFICER));
        createStaff("EMP-0005", "Yaw", "Boateng", "fieldofficer", "Field Officer", kumasi.id(), false,
                roles.get(DefaultRoleCatalog.FIELD_OFFICER));
        createStaff("EMP-0006", "Akosua", "Darko", "compliance", "Compliance Officer", headOffice, true,
                roles.get(DefaultRoleCatalog.COMPLIANCE_OFFICER));
        createStaff("EMP-0007", "Kofi", "Ansah", "auditor", "Internal Auditor", headOffice, true,
                roles.get(DefaultRoleCatalog.AUDITOR));

        seedCustomer(headOffice, "Akua", "Asantewaa", LocalDate.of(1988, 5, 14), "FEMALE", "+233244100200",
                "GHA-700100200-1");
        seedCustomer(tema.id(), "Kwabena", "Ofori", LocalDate.of(1979, 11, 2), "MALE", "+233205300400",
                "GHA-700300400-2");
        seedCustomer(kumasi.id(), "Adjoa", "Frimpong", LocalDate.of(1995, 1, 23), "FEMALE", "+233277500600",
                "GHA-700500600-3");
        return null;
    }

    /**
     * Customers awaiting KYC, so the review screens have data. Approving them needs two different people, which is
     * the point of the four-eyes rule.
     */
    private void seedCustomer(UUID branchId, String firstName, String lastName, LocalDate dateOfBirth, String gender,
                              String phone, String ghanaCard) {
        CustomerResponse customer = customerService.create(new CreateCustomerRequest("INDIVIDUAL", branchId, "BRANCH",
                phone, null, "en", null,
                new IndividualDetails(null, firstName, null, lastName, dateOfBirth, gender, "GH", null, "Trader",
                        null, "SELF_EMPLOYED", null, null),
                null));
        contactService.addAddress(customer.id(), new AddressRequest("RESIDENTIAL", "House 12, Market Road", null,
                "Accra", null, "Greater Accra", "GH", "GA-123-4567", "Near the lorry station", true, null));
        identityService.add(customer.id(), new IdentificationRequest("GHANA_CARD", ghanaCard, "GH", null,
                LocalDate.of(2032, 12, 31), true));
    }

    private void createStaff(String employeeNumber, String firstName, String lastName, String username,
                             String jobTitle, UUID branchId, boolean allBranches, UUID roleId) {
        StaffCreatedResponse created = staffService.create(new CreateStaffRequest(employeeNumber, firstName,
                lastName, username + "@demo-mfi.local", null, jobTitle, branchId, allBranches, username,
                Set.of(roleId)));
        credentialService.setPasswordForSeeding(created.staff().id(), DEMO_STAFF_PASSWORD);
    }

    private static OnboardTenantRequest demoInstitution() {
        return new OnboardTenantRequest(DEMO_TENANT, "Demo Microfinance Company Limited", "Demo Microfinance",
                "MICROFINANCE", "GH", "GHS", "Africa/Accra", "en-GH", "DEMO-LICENCE-0001",
                "info@demo-mfi.local", "+233302000000",
                new OnboardTenantRequest.HeadOffice("HQ", "Accra Central (Head Office)", "Accra", "Greater Accra",
                        "GA-123-4567"),
                new OnboardTenantRequest.Administrator("Abena", "Adjei", "admin@demo-mfi.local", null, "admin"),
                Set.of("SAVINGS", "LOANS", "SUSU", "FIXED_DEPOSIT", "FIELD_COLLECTIONS", "MOBILE_MONEY",
                        "CUSTOMER_MOBILE_APP"));
    }
}
