package com.company.banking.common.security;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permission catalog. Must match the rows seeded in {@code core.permission} (verified by PermissionCatalogIT).
 * Codes are used verbatim in {@code @PreAuthorize("hasAuthority('...')")}.
 */
public final class Permissions {

    public static final String INSTITUTION_VIEW = "institution.view";
    public static final String INSTITUTION_MANAGE = "institution.manage";
    public static final String SETTINGS_VIEW = "settings.view";
    public static final String SETTINGS_MANAGE = "settings.manage";
    public static final String BRANCH_VIEW = "branch.view";
    public static final String BRANCH_MANAGE = "branch.manage";
    public static final String STAFF_VIEW = "staff.view";
    public static final String STAFF_CREATE = "staff.create";
    public static final String STAFF_EDIT = "staff.edit";
    public static final String STAFF_DISABLE = "staff.disable";
    public static final String STAFF_UNLOCK = "staff.unlock";
    public static final String ROLE_VIEW = "role.view";
    public static final String ROLE_MANAGE = "role.manage";
    public static final String ROLE_ASSIGN = "role.assign";
    public static final String PERMISSION_VIEW = "permission.view";
    public static final String AUDIT_VIEW = "audit.view";
    public static final String CUSTOMER_VIEW = "customer.view";
    public static final String CUSTOMER_CREATE = "customer.create";
    public static final String CUSTOMER_EDIT = "customer.edit";
    public static final String CUSTOMER_FREEZE = "customer.freeze";
    public static final String KYC_VIEW = "kyc.view";
    public static final String KYC_REVIEW = "kyc.review";
    public static final String KYC_APPROVE = "kyc.approve";
    public static final String PRODUCT_VIEW = "product.view";
    public static final String PRODUCT_MANAGE = "product.manage";
    public static final String ACCOUNT_VIEW = "account.view";
    public static final String ACCOUNT_CREATE = "account.create";
    public static final String ACCOUNT_FREEZE = "account.freeze";
    public static final String ACCOUNT_CLOSE = "account.close";
    public static final String TRANSACTION_VIEW = "transaction.view";
    public static final String TRANSACTION_CREATE = "transaction.create";
    public static final String TRANSACTION_REVERSE = "transaction.reverse";
    public static final String LEDGER_VIEW = "ledger.view";
    public static final String LEDGER_POST = "ledger.post";
    public static final String APPROVAL_VIEW = "approval.view";
    public static final String APPROVAL_ACT = "approval.act";
    public static final String TELLER_OPERATE = "teller.operate";
    public static final String TELLER_SUPERVISE = "teller.supervise";
    public static final String CASH_VIEW = "cash.view";
    public static final String CASH_MANAGE = "cash.manage";
    public static final String COLLECTION_VIEW = "collection.view";
    public static final String COLLECTION_CREATE = "collection.create";
    public static final String FIELD_MANAGE = "field.manage";
    public static final String SUSU_VIEW = "susu.view";
    public static final String SUSU_MANAGE = "susu.manage";
    public static final String LOAN_VIEW = "loan.view";
    public static final String LOAN_CREATE = "loan.create";
    public static final String LOAN_ASSESS = "loan.assess";
    public static final String LOAN_RECOMMEND = "loan.recommend";
    public static final String LOAN_APPROVE = "loan.approve";
    public static final String LOAN_DISBURSE = "loan.disburse";
    public static final String LOAN_WRITEOFF = "loan.writeoff";
    public static final String REPORT_VIEW = "report.view";
    public static final String REPORT_EXPORT = "report.export";
    public static final String COMPLIANCE_VIEW = "compliance.view";
    public static final String COMPLIANCE_MANAGE = "compliance.manage";
    public static final String FRAUD_VIEW = "fraud.view";
    public static final String FRAUD_MANAGE = "fraud.manage";
    public static final String OPERATIONS_VIEW = "operations.view";
    public static final String OPERATIONS_MANAGE = "operations.manage";

    public static final String PLATFORM_TENANT_VIEW = "platform.tenant.view";
    public static final String PLATFORM_TENANT_MANAGE = "platform.tenant.manage";
    public static final String PLATFORM_FEATURE_MANAGE = "platform.feature.manage";
    public static final String PLATFORM_AUDIT_VIEW = "platform.audit.view";
    public static final String PLATFORM_USER_MANAGE = "platform.user.manage";

    private static final Set<String> ALL = Arrays.stream(Permissions.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
            .map(Permissions::valueOf)
            .collect(Collectors.toUnmodifiableSet());

    private Permissions() {
    }

    public static Set<String> all() {
        return ALL;
    }

    public static Set<String> tenantScoped() {
        return ALL.stream().filter(code -> !isPlatformScoped(code)).collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isPlatformScoped(String code) {
        return code.startsWith("platform.");
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
