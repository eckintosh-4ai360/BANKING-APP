package com.company.banking.architecture;

import com.company.banking.common.tenant.TenantContext;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.function.Supplier;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the modular-monolith rules from docs/architecture/01-system-architecture.md §2.2.
 */
class ArchitectureTest {

    private static final String ROOT = "com.company.banking";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void modulesHaveNoCyclicDependencies() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"audit", "tenant", "branch", "iam", "staff", "platform", "customer", "kyc", "document",
            "ledger", "product", "account", "transaction", "approval", "manualjournal", "operations"})
    void entitiesAndRepositoriesArePrivateToTheirModule(String module) {
        noClasses().that().resideOutsideOfPackage(ROOT + "." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + "." + module + ".entity..", ROOT + "." + module + ".repository..")
                .because("other modules must use the module's service API")
                .check(classes);
    }

    @Test
    void commonDependsOnNoBusinessModule() {
        noClasses().that().resideInAPackage(ROOT + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".audit..", ROOT + ".tenant..",
                        ROOT + ".branch..", ROOT + ".iam..", ROOT + ".staff..", ROOT + ".platform..",
                        ROOT + ".customer..", ROOT + ".kyc..", ROOT + ".document..", ROOT + ".ledger..",
                        ROOT + ".product..", ROOT + ".account..", ROOT + ".transaction..", ROOT + ".approval..",
                        ROOT + ".manualjournal..", ROOT + ".operations..")
                .check(classes);
    }

    @Test
    void controllersNeverTouchRepositoriesOrEntities() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..entity..")
                .check(classes);
    }

    @Test
    void noFieldInjection() {
        fields().should().notBeAnnotatedWith(Autowired.class).check(classes);
    }

    @Test
    void onlyAuthenticationAndProvisioningFlowsMaySwitchTenant() {
        noClasses().that().resideOutsideOfPackages(ROOT + ".platform..", ROOT + ".iam.service..",
                        ROOT + ".tenant.service..", ROOT + ".common.tenant..")
                .should().callMethod(TenantContext.class, "callAs", UUID.class,
                        Supplier.class)
                .orShould().callMethod(TenantContext.class, "runAs", UUID.class, Runnable.class)
                .check(classes);
    }

    @Test
    void onlyTheAuthenticationFilterBindsTheRequestTenant() {
        noClasses().that().resideOutsideOfPackages(ROOT + ".iam.security..", ROOT + ".common.tenant..")
                .should().callMethod(TenantContext.class, "bind", UUID.class)
                .check(classes);
    }
}
