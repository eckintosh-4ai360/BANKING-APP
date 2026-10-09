package com.company.banking.iam.service;

import com.company.banking.common.security.Permissions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Role templates created for every new institution. Institutions may adjust them afterwards.
 *
 * <p>Segregation of duties: the institution administrator manages configuration and people but holds no
 * money-movement, lending-decision or ledger permissions. Auditors are read-only.
 */
public final class DefaultRoleCatalog {

    public static final String INSTITUTION_ADMIN = "INSTITUTION_ADMIN";
    public static final String BRANCH_MANAGER = "BRANCH_MANAGER";
    public static final String TELLER = "TELLER";
    public static final String LOAN_OFFICER = "LOAN_OFFICER";
    public static final String FIELD_OFFICER = "FIELD_OFFICER";
    public static final String COMPLIANCE_OFFICER = "COMPLIANCE_OFFICER";
    public static final String AUDITOR = "AUDITOR";
    public static final String ACCOUNTANT = "ACCOUNTANT";

    private DefaultRoleCatalog() {
    }

    public record Template(String code, String name, String description, Set<String> permissions) {
    }

    public static Map<String, Template> templates() {
        Map<String, Template> templates = new LinkedHashMap<>();
        add(templates, INSTITUTION_ADMIN, "Institution Administrator",
                "Configures the institution, branches, products, staff and roles",
                Permissions.INSTITUTION_VIEW, Permissions.INSTITUTION_MANAGE, Permissions.SETTINGS_VIEW,
                Permissions.SETTINGS_MANAGE, Permissions.BRANCH_VIEW, Permissions.BRANCH_MANAGE,
                Permissions.STAFF_VIEW, Permissions.STAFF_CREATE, Permissions.STAFF_EDIT, Permissions.STAFF_DISABLE,
                Permissions.STAFF_UNLOCK, Permissions.ROLE_VIEW, Permissions.ROLE_MANAGE, Permissions.ROLE_ASSIGN,
                Permissions.PERMISSION_VIEW, Permissions.AUDIT_VIEW, Permissions.PRODUCT_VIEW,
                Permissions.PRODUCT_MANAGE, Permissions.REPORT_VIEW, Permissions.CUSTOMER_VIEW,
                Permissions.OPERATIONS_VIEW, Permissions.OPERATIONS_MANAGE);
        add(templates, BRANCH_MANAGER, "Branch Manager",
                "Runs branch operations, approves within limits and supervises tellers",
                Permissions.INSTITUTION_VIEW, Permissions.BRANCH_VIEW, Permissions.STAFF_VIEW,
                Permissions.CUSTOMER_VIEW, Permissions.CUSTOMER_CREATE, Permissions.CUSTOMER_EDIT,
                Permissions.CUSTOMER_FREEZE, Permissions.KYC_VIEW, Permissions.KYC_REVIEW, Permissions.KYC_APPROVE,
                Permissions.PRODUCT_VIEW, Permissions.ACCOUNT_VIEW, Permissions.ACCOUNT_CREATE,
                Permissions.ACCOUNT_FREEZE, Permissions.ACCOUNT_CLOSE, Permissions.TRANSACTION_VIEW,
                Permissions.TRANSACTION_REVERSE, Permissions.LOAN_VIEW, Permissions.LOAN_APPROVE,
                Permissions.LOAN_DISBURSE, Permissions.LOAN_REPAY,
                Permissions.APPROVAL_VIEW, Permissions.APPROVAL_ACT, Permissions.TELLER_SUPERVISE,
                Permissions.CASH_VIEW, Permissions.CASH_MANAGE, Permissions.COLLECTION_VIEW, Permissions.FIELD_MANAGE,
                Permissions.SUSU_VIEW, Permissions.SUSU_MANAGE,
                Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT, Permissions.OPERATIONS_VIEW);
        add(templates, TELLER, "Teller",
                "Receives deposits, pays withdrawals and balances the teller drawer",
                Permissions.CUSTOMER_VIEW, Permissions.ACCOUNT_VIEW, Permissions.TRANSACTION_VIEW,
                Permissions.TRANSACTION_CREATE, Permissions.TELLER_OPERATE, Permissions.CASH_VIEW,
                Permissions.LOAN_VIEW, Permissions.LOAN_REPAY);
        add(templates, LOAN_OFFICER, "Loan Officer",
                "Originates, assesses and monitors loans",
                Permissions.CUSTOMER_VIEW, Permissions.CUSTOMER_CREATE, Permissions.CUSTOMER_EDIT,
                Permissions.KYC_VIEW, Permissions.PRODUCT_VIEW, Permissions.ACCOUNT_VIEW, Permissions.LOAN_VIEW,
                Permissions.LOAN_CREATE, Permissions.LOAN_ASSESS, Permissions.LOAN_RECOMMEND,
                Permissions.COLLECTION_VIEW, Permissions.REPORT_VIEW);
        add(templates, FIELD_OFFICER, "Field Officer",
                "Registers customers and records field collections and visits",
                Permissions.CUSTOMER_VIEW, Permissions.CUSTOMER_CREATE, Permissions.KYC_VIEW,
                Permissions.ACCOUNT_VIEW, Permissions.LOAN_VIEW, Permissions.COLLECTION_VIEW,
                Permissions.COLLECTION_CREATE);
        add(templates, COMPLIANCE_OFFICER, "Compliance Officer",
                "Reviews KYC, AML and fraud alerts and manages compliance cases",
                Permissions.CUSTOMER_VIEW, Permissions.CUSTOMER_FREEZE, Permissions.KYC_VIEW, Permissions.KYC_REVIEW,
                Permissions.KYC_APPROVE, Permissions.ACCOUNT_VIEW, Permissions.ACCOUNT_FREEZE,
                Permissions.TRANSACTION_VIEW, Permissions.COMPLIANCE_VIEW, Permissions.COMPLIANCE_MANAGE,
                Permissions.FRAUD_VIEW, Permissions.FRAUD_MANAGE, Permissions.REPORT_VIEW,
                Permissions.REPORT_EXPORT, Permissions.AUDIT_VIEW);
        add(templates, ACCOUNTANT, "Accountant",
                "Keeps the books: journals, trial balance, period close and approval of manual journals",
                Permissions.BRANCH_VIEW, Permissions.PRODUCT_VIEW, Permissions.ACCOUNT_VIEW,
                Permissions.TRANSACTION_VIEW, Permissions.LEDGER_VIEW, Permissions.LEDGER_POST,
                Permissions.APPROVAL_VIEW, Permissions.APPROVAL_ACT, Permissions.REPORT_VIEW,
                Permissions.REPORT_EXPORT, Permissions.OPERATIONS_VIEW);
        add(templates, AUDITOR, "Auditor",
                "Read-only access to records, ledger, approvals and audit trail",
                Permissions.INSTITUTION_VIEW, Permissions.SETTINGS_VIEW, Permissions.BRANCH_VIEW,
                Permissions.STAFF_VIEW, Permissions.ROLE_VIEW, Permissions.PERMISSION_VIEW, Permissions.AUDIT_VIEW,
                Permissions.CUSTOMER_VIEW, Permissions.KYC_VIEW, Permissions.PRODUCT_VIEW,
                Permissions.ACCOUNT_VIEW, Permissions.TRANSACTION_VIEW, Permissions.LEDGER_VIEW,
                Permissions.APPROVAL_VIEW, Permissions.CASH_VIEW, Permissions.COLLECTION_VIEW,
                Permissions.LOAN_VIEW, Permissions.COMPLIANCE_VIEW, Permissions.FRAUD_VIEW,
                Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT, Permissions.OPERATIONS_VIEW);
        return templates;
    }

    private static void add(Map<String, Template> templates, String code, String name, String description,
                            String... permissions) {
        templates.put(code, new Template(code, name, description, Set.of(permissions)));
    }
}
