package com.company.banking.loan;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.support.Api;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * An institution ready to lend on {@link #START}: loan officers, branch managers, a teller, and a verified customer
 * with a savings account that earns no interest (so its balance moves only with the loan); plus helpers to take an
 * application through its workflow.
 */
abstract class LoanIntegrationTest extends LedgerIntegrationTest {

    /** A Monday. */
    protected static final LocalDate START = LocalDate.of(2027, 3, 1);

    @Autowired
    protected BusinessDateService businessDates;

    protected TenantHandle tenant;
    protected StaffHandle lender;
    protected StaffHandle reviewer;
    protected StaffHandle manager;
    protected StaffHandle secondManager;
    protected StaffHandle thirdManager;
    protected StaffHandle teller;
    protected String customerId;
    protected String accountId;
    protected String productId;

    @BeforeEach
    void setUpInstitution() {
        tenant = fixtures.onboardTenant();
        lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        reviewer = fixtures.createStaff(tenant, "reviewer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        secondManager = fixtures.createStaff(tenant, "manager2", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        thirdManager = fixtures.createStaff(tenant, "manager3", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        customerId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Akosua").toString();
        Map<String, Object> savings = terms("0");
        savings.put("interestRate", "0");
        accountId = fixtures.openAccount(manager.token(), UUID.fromString(customerId),
                fixtures.publishedProduct(tenant, "SAV01", "SAVINGS", savings)).get("id").asString();
        inTenant(tenant.id(), () -> businessDates.roll(businessDates.today(), START));
    }

    /**
     * GHS, 100 to 10,000 over 1 to 12 months, flat 24% a year on 30/360, a 2% + 5.00 processing fee.
     */
    protected static Map<String, Object> loanTerms() {
        Map<String, Object> terms = new LinkedHashMap<>();
        terms.put("currency", "GHS");
        terms.put("minAmount", "100.00");
        terms.put("maxAmount", "10000.00");
        terms.put("minInstallments", 1);
        terms.put("maxInstallments", 12);
        terms.put("interestMethod", "FLAT");
        terms.put("annualRate", "24");
        terms.put("dayCount", "THIRTY_360");
        terms.put("repaymentFrequency", "MONTHLY");
        terms.put("processingFeeRate", "2");
        terms.put("processingFeeFlat", "5.00");
        return terms;
    }

    protected String publishedLoanProduct(String code, Map<String, Object> terms) {
        JsonNode created = api.post("/api/v1/loan-products", tenant.adminToken(), Map.of("code", code,
                "name", code + " loan", "terms", terms)).expect(201).data();
        String id = created.get("id").asString();
        api.post("/api/v1/loan-products/" + id + "/versions/" + created.at("/versions/0/id").asString()
                + "/publish", tenant.adminToken(), null).expect(200);
        return id;
    }

    protected Map<String, Object> application(String amount, int installments) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("customerId", customerId);
        request.put("productId", productId);
        request.put("requestedAmount", amount);
        request.put("requestedInstallments", installments);
        request.put("purpose", "Restock the shop");
        request.put("monthlyIncome", "3000.00");
        request.put("monthlyExpenses", "1200.00");
        request.put("disbursementAccountId", accountId);
        return request;
    }

    protected String apply(String amount, int installments) {
        return api.post("/api/v1/loan-applications", lender.token(), application(amount, installments)).expect(201)
                .data().at("/application/id").asString();
    }

    /**
     * One verified guarantor and verified collateral worth {@code forcedSaleValue}; the loan officer may not verify.
     */
    protected void securedBy(String applicationId, String forcedSaleValue) {
        JsonNode withGuarantor = api.post(path(applicationId, "guarantors"), lender.token(), Map.of(
                "fullName", "Yaw Boateng", "phone", "+233201234567", "relationship", "Brother",
                "guaranteedAmount", "500.00")).expect(201).data();
        String guarantorId = withGuarantor.at("/guarantors/0/id").asString();
        api.post(path(applicationId, "guarantors/" + guarantorId + "/verify"), lender.token(), null)
                .expectError(403, "SEPARATION_OF_DUTIES");
        api.post(path(applicationId, "guarantors/" + guarantorId + "/verify"), reviewer.token(), null).expect(200);
        String collateralId = api.post(path(applicationId, "collateral"), lender.token(),
                collateral(forcedSaleValue)).expect(201).data().at("/collateral/0/id").asString();
        api.post(path(applicationId, "collateral/" + collateralId + "/verify"), reviewer.token(), null).expect(200);
    }

    protected static Map<String, Object> collateral(String forcedSaleValue) {
        return Map.of("category", "INVENTORY", "description", "Shop stock", "estimatedValue", forcedSaleValue,
                "forcedSaleValue", forcedSaleValue, "valuationDate", "2027-02-20");
    }

    protected void submitAndRecommend(String applicationId) {
        api.post(path(applicationId, "submit"), lender.token(), step(applicationId)).expect(200);
        api.post(path(applicationId, "assess"), lender.token(), Map.of("riskRating", "LOW",
                "note", "Steady trade", "version", version(applicationId))).expect(200);
        api.post(path(applicationId, "recommend"), lender.token(), step(applicationId))
                .expectError(403, "SEPARATION_OF_DUTIES");
        api.post(path(applicationId, "recommend"), reviewer.token(), step(applicationId)).expect(200);
    }

    protected void approved(String applicationId, String amount, int installments, StaffHandle approver) {
        submitAndRecommend(applicationId);
        api.post(path(applicationId, "approve"), reviewer.token(), approval(amount, installments,
                version(applicationId))).expectError(403, "ACCESS_DENIED");
        JsonNode approved = api.post(path(applicationId, "approve"), approver.token(), approval(amount, installments,
                version(applicationId))).expect(200).data();
        assertThat(approved.at("/application/status").asString()).isEqualTo("APPROVED");
        assertThat(approved.get("steps")).extracting(s -> s.get("type").asString())
                .containsExactly("SUBMIT", "ASSESS", "RECOMMEND", "APPROVE");
    }

    /**
     * Applies, gets approved by {@link #manager} and disbursed by {@link #secondManager}; returns the loan id.
     */
    protected String disbursedLoan(String amount, int installments) {
        String applicationId = apply(amount, installments);
        approved(applicationId, amount, installments, manager);
        return api.postIdempotent(disburse(applicationId), secondManager.token(), "disburse-" + applicationId,
                Map.of()).expect(201).data().at("/loan/id").asString();
    }

    protected static Map<String, Object> approval(String amount, int installments, long version) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("approvedAmount", amount);
        request.put("approvedInstallments", installments);
        request.put("note", "Within policy");
        request.put("version", version);
        return request;
    }

    protected Map<String, Object> step(String applicationId) {
        return Map.of("note", "OK", "version", version(applicationId));
    }

    protected long version(String applicationId) {
        return api.get("/api/v1/loan-applications/" + applicationId, lender.token()).expect(200).data()
                .at("/application/version").asLong();
    }

    protected static String path(String applicationId, String action) {
        return "/api/v1/loan-applications/" + applicationId + "/" + action;
    }

    protected static String disburse(String applicationId) {
        return path(applicationId, "disburse");
    }

    protected Api.Response repay(String loanId, String amount, String source, String key) {
        return api.postIdempotent("/api/v1/loans/" + loanId + "/repayments", teller.token(), key, Map.of(
                "amount", amount, "source", source));
    }

    protected JsonNode loan(String loanId) {
        return api.get("/api/v1/loans/" + loanId, lender.token()).expect(200).data();
    }

    protected static List<String> statuses(JsonNode loan) {
        List<String> statuses = new ArrayList<>();
        loan.get("schedule").forEach(installment -> statuses.add(installment.get("status").asString()));
        return statuses;
    }

    protected void jumpTo(LocalDate from, LocalDate to) {
        inTenant(tenant.id(), () -> businessDates.roll(from, to));
    }

    protected JsonNode endOfDay(String expectedDate) {
        JsonNode run = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(run.get("businessDate").asString()).isEqualTo(expectedDate);
        return run;
    }

    protected String balance() {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }

    protected JsonNode trialBalance() {
        StaffHandle accountant = fixtures.createStaff(tenant, "acct" + UUID.randomUUID().toString().substring(0, 6),
                tenant.headOfficeId(), true, "ACCOUNTANT");
        return api.get("/api/v1/ledger/trial-balance", accountant.token()).expect(200).data();
    }

    protected static String glDebit(JsonNode trial, String code) {
        return row(trial, code).get("debitBalance").asString();
    }

    protected static String glCredit(JsonNode trial, String code) {
        return row(trial, code).get("creditBalance").asString();
    }

    private static JsonNode row(JsonNode trial, String code) {
        for (JsonNode row : trial.get("rows")) {
            if (code.equals(row.get("code").asString())) {
                return row;
            }
        }
        throw new AssertionError("No trial balance row " + code);
    }
}
