package com.company.banking.iam;

import com.company.banking.common.security.Permissions;
import com.company.banking.iam.controller.AuthController;
import com.company.banking.iam.controller.PlatformAuthController;
import com.company.banking.iam.service.DefaultRoleCatalog;
import com.company.banking.operations.controller.BusinessDateController;
import com.company.banking.staff.controller.MeController;
import com.company.banking.support.IntegrationTest;
import com.company.banking.tenant.controller.PublicInstitutionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the permission catalog, the code constants, the role templates and the endpoint annotations consistent,
 * and makes sure no endpoint is left without authorization by accident.
 */
class PermissionCatalogIT extends IntegrationTest {

    /**
     * Controllers whose endpoints are intentionally public or available to any authenticated user.
     */
    private static final Set<Class<?>> UNRESTRICTED_CONTROLLERS = Set.of(
            AuthController.class, PlatformAuthController.class, PublicInstitutionController.class,
            MeController.class, BusinessDateController.class);

    /**
     * The customer API: only customer tokens reach it (SecurityConfig), carrying no staff permissions, and every
     * service there serves only the signed-in customer's own data.
     */
    private static final String CUSTOMER_API = "/api/v1/customer/";

    private static final Pattern AUTHORITY = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void databaseCatalogMatchesTheCodeConstants() {
        Map<String, String> catalog = jdbcClient.sql("SELECT code, scope FROM core.permission")
                .query((rs, row) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(catalog.keySet()).containsExactlyInAnyOrderElementsOf(Permissions.all());
        catalog.forEach((code, scope) -> assertThat(scope)
                .as(code)
                .isEqualTo(Permissions.isPlatformScoped(code) ? "PLATFORM" : "TENANT"));
    }

    @Test
    void everyEndpointRequiresACataloguedPermissionUnlessDeliberatelyUnrestricted() {
        List<String> unprotected = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Class<?> controller = handler.getBeanType();
            if (!controller.getPackageName().startsWith("com.company.banking")
                    || UNRESTRICTED_CONTROLLERS.contains(controller)) {
                continue;
            }
            PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),
                    PreAuthorize.class);
            Set<String> paths = entry.getKey().getPatternValues();
            if (!paths.isEmpty() && paths.stream().allMatch(path -> path.startsWith(CUSTOMER_API))) {
                assertThat(preAuthorize).as("customer endpoints use no staff permission: %s", entry.getKey())
                        .isNull();
                continue;
            }
            if (preAuthorize == null) {
                unprotected.add(entry.getKey().toString());
                continue;
            }
            List<String> permissions = permissionsIn(preAuthorize.value());
            assertThat(permissions).as("permission expression on %s", entry.getKey()).isNotEmpty();
            assertThat(Permissions.all()).as("permissions used on %s", entry.getKey()).containsAll(permissions);
        }
        assertThat(unprotected).as("endpoints without @PreAuthorize").isEmpty();
    }

    /**
     * Permissions named in {@code hasAuthority('a')} or {@code hasAnyAuthority('a', 'b')}.
     */
    private static List<String> permissionsIn(String expression) {
        List<String> permissions = new ArrayList<>();
        Matcher call = AUTHORITY.matcher(expression);
        while (call.find()) {
            Matcher literal = QUOTED.matcher(call.group(1));
            while (literal.find()) {
                permissions.add(literal.group(1));
            }
        }
        return permissions;
    }

    @Test
    void defaultRolesUseOnlyInstitutionPermissions() {
        DefaultRoleCatalog.templates().values().forEach(template -> assertThat(Permissions.tenantScoped())
                .as(template.code())
                .containsAll(template.permissions()));
    }

    @Test
    void institutionAdministratorTemplateHoldsNoMoneyOrDecisionPermissions() {
        Set<String> admin = DefaultRoleCatalog.templates().get(DefaultRoleCatalog.INSTITUTION_ADMIN).permissions();
        assertThat(admin).doesNotContain(Permissions.TRANSACTION_CREATE, Permissions.TRANSACTION_REVERSE,
                Permissions.LEDGER_POST, Permissions.LOAN_APPROVE, Permissions.LOAN_DISBURSE,
                Permissions.APPROVAL_ACT, Permissions.CASH_MANAGE, Permissions.TELLER_OPERATE);
    }

    @Test
    void auditorTemplateIsReadOnly() {
        Set<String> auditor = DefaultRoleCatalog.templates().get(DefaultRoleCatalog.AUDITOR).permissions();
        assertThat(auditor).allMatch(code -> code.endsWith(".view") || code.equals(Permissions.REPORT_EXPORT));
    }
}
