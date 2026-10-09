package com.company.banking.loan;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Loans end to end: a product, an application through its workflow (separation of duties, guarantors, collateral,
 * a second approval for large loans), disbursement, repayments split over the schedule, interest recognised as it
 * is earned, and settlement with the unearned interest waived. The ledger stays balanced and the loan's accounts end
 * at zero.
 */
class LoanIT extends LedgerIntegrationTest {

    /** A Monday. */
    private static final LocalDate START = LocalDate.of(2027, 3, 1);

    @Autowired
    private BusinessDateService businessDates;

    @Autowired
    private JdbcTemplate jdbc;

    private TenantHandle tenant;
    private StaffHandle lender;
    private StaffHandle reviewer;
    private StaffHandle manager;
    private StaffHandle secondManager;
    private StaffHandle thirdManager;
    private StaffHandle teller;
    private String customerId;
    private String accountId;
    private String productId;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        lender = fixtures.createStaff(tenant, "lender", tenant.headOfficeId(), false, "LOAN_OFFICER");
        reviewer = fixtures.createStaff(tenant, "reviewer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        secondManager = fixtures.createStaff(tenant, "manager2", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        thirdManager = fixtures.createStaff(tenant, "manager3", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        customerId = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Akosua").toString();
        accountId = fixtures.openAccount(manager.token(), UUID.fromString(customerId),
                fixtures.publishedProduct(tenant, "SAV01", "SAVINGS", terms("0"))).get("id").asString();
        inTenant(tenant.id(), () -> businessDates.roll(businessDates.today(), START));
        productId = publishedLoanProduct();
    }

    @Test
    void aLoanGoesFromApplicationThroughRepaymentsToSettlement() {
        // 1,200 flat at 24% a year, 6 monthly installments on 30/360: 200 principal + 24 interest a month.
        JsonNode preview = api.get("/api/v1/loan-products/" + productId + "/schedule-preview?amount=1200"
                + "&installments=6", lender.token()).expect(200).data();
        assertThat(preview.get("processingFee").asString()).isEqualTo("29.00");
        assertThat(preview.get("netDisbursed").asString()).isEqualTo("1171.00");
        assertThat(preview.get("totalInterest").asString()).isEqualTo("144.00");
        assertThat(preview.get("maturityDate").asString()).isEqualTo("2027-09-01");
        assertThat(preview.get("lines")).allSatisfy(line -> {
            assertThat(line.get("principal").asString()).isEqualTo("200.00");
            assertThat(line.get("interest").asString()).isEqualTo("24.00");
        });

        String applicationId = apply("1200.00", 6);
        securedBy(applicationId, "800.00");
        approved(applicationId, "1200.00", 6, manager);

        api.postIdempotent(disburse(applicationId), manager.token(), "disburse-" + applicationId, Map.of())
                .expectError(403, "SEPARATION_OF_DUTIES");
        String key = "disburse-" + applicationId;
        var first = api.postIdempotent(disburse(applicationId), secondManager.token(), key, Map.of()).expect(201);
        var replay = api.postIdempotent(disburse(applicationId), secondManager.token(), key, Map.of()).expect(201);
        assertThat(replay.header("Idempotency-Replayed")).isEqualTo("true");
        String loanId = first.data().at("/loan/id").asString();
        assertThat(replay.data().at("/loan/id").asString()).isEqualTo(loanId);
        assertThat(balance()).as("principal less the processing fee, once").isEqualTo("1171.00");

        JsonNode loan = loan(loanId);
        assertThat(loan.at("/loan/principalOutstanding").asString()).isEqualTo("1200.00");
        assertThat(loan.at("/loan/interestReceivable").asString()).isEqualTo("0.00");
        assertThat(loan.at("/loan/firstDueDate").asString()).isEqualTo("2027-04-01");
        assertThat(loan.at("/loan/maturityDate").asString()).isEqualTo("2027-09-01");
        assertThat(loan.get("schedule")).hasSize(6);
        assertThat(loan.at("/payoff/total").asString()).as("no interest earned yet").isEqualTo("1200.00");
        assertThat(loan.at("/payoff/interestWaived").asString()).isEqualTo("144.00");
        JsonNode application = api.get("/api/v1/loan-applications/" + applicationId, lender.token()).expect(200)
                .data();
        assertThat(application.at("/application/status").asString()).isEqualTo("DISBURSED");
        assertThat(application.at("/application/loanId").asString()).isEqualTo(loanId);
        String collateralId = application.at("/collateral/0/id").asString();
        api.post("/api/v1/loan-applications/" + applicationId + "/collateral/" + collateralId + "/release",
                manager.token(), null).expectError(409, "INVALID_STEP");

        // On the first due date the first installment's interest has been earned in full.
        jumpTo(START, LocalDate.of(2027, 4, 1));
        JsonNode receipt = repay(loanId, "224.00", "ACCOUNT", "repayment-1").expect(201).data();
        assertThat(receipt.at("/repayment/interest").asString()).isEqualTo("24.00");
        assertThat(receipt.at("/repayment/principal").asString()).isEqualTo("200.00");
        assertThat(receipt.get("settled").asBoolean()).isFalse();
        assertThat(repay(loanId, "224.00", "ACCOUNT", "repayment-1").expect(201).header("Idempotency-Replayed"))
                .isEqualTo("true");
        assertThat(balance()).isEqualTo("947.00");
        loan = loan(loanId);
        assertThat(loan.at("/loan/principalOutstanding").asString()).isEqualTo("1000.00");
        assertThat(loan.at("/loan/interestReceivable").asString()).isEqualTo("0.00");
        assertThat(statuses(loan)).containsExactly("PAID", "UPCOMING", "UPCOMING", "UPCOMING", "UPCOMING",
                "UPCOMING");

        // Half-way through the second period: 12.00 of its 24.00 interest is earned.
        jumpTo(LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 16));
        JsonNode payoff = api.get("/api/v1/loans/" + loanId + "/payoff", lender.token()).expect(200).data();
        assertThat(payoff.get("principal").asString()).isEqualTo("1000.00");
        assertThat(payoff.get("interest").asString()).isEqualTo("12.00");
        assertThat(payoff.get("total").asString()).isEqualTo("1012.00");
        assertThat(payoff.get("interestWaived").asString()).isEqualTo("108.00");
        repay(loanId, "1012.01", "ACCOUNT", "repay-too-much").expectError(422, "OVERPAYMENT");

        // Nothing is due: a prepayment reduces the next installments' principal, never interest not yet due.
        receipt = repay(loanId, "300.00", "ACCOUNT", "repayment-2").expect(201).data();
        assertThat(receipt.at("/repayment/principal").asString()).isEqualTo("300.00");
        assertThat(receipt.at("/repayment/interest").asString()).isEqualTo("0.00");
        loan = loan(loanId);
        assertThat(loan.at("/loan/principalOutstanding").asString()).isEqualTo("700.00");
        assertThat(loan.at("/loan/interestReceivable").asString()).as("earned so far").isEqualTo("12.00");
        assertThat(statuses(loan)).containsExactly("PAID", "PARTLY_PAID", "PARTLY_PAID", "UPCOMING", "UPCOMING",
                "UPCOMING");
        repay(loanId, "705.00", "ACCOUNT", "repay-almost").expectError(422, "SETTLE_WITH_PAYOFF");

        // Settle in cash at a till: the earned 12.00 is collected, the unearned interest waived.
        fixtures.openTill(manager, teller, tenant.headOfficeId(), "GHS");
        receipt = repay(loanId, "712.00", "CASH", "repayment-3").expect(201).data();
        assertThat(receipt.get("settled").asBoolean()).isTrue();
        assertThat(receipt.at("/repayment/source").asString()).isEqualTo("CASH");
        loan = loan(loanId);
        assertThat(loan.at("/loan/status").asString()).isEqualTo("CLOSED");
        assertThat(loan.at("/loan/closedOn").asString()).isEqualTo("2027-04-16");
        assertThat(loan.at("/loan/principalOutstanding").asString()).isEqualTo("0.00");
        assertThat(loan.at("/loan/interestReceivable").asString()).isEqualTo("0.00");
        assertThat(loan.at("/loan/penaltyReceivable").asString()).isEqualTo("0.00");
        assertThat(loan.get("payoff").isNull()).isTrue();
        assertThat(statuses(loan)).containsOnly("PAID");
        assertThat(loan.at("/schedule/1/interestPaid").asString()).isEqualTo("12.00");
        assertThat(loan.at("/schedule/1/interestWaived").asString()).isEqualTo("12.00");
        assertThat(loan.get("repayments")).hasSize(3);
        assertThat(balance()).isEqualTo("647.00");
        assertThat(api.get("/api/v1/loan-applications/" + applicationId, lender.token()).expect(200).data()
                .at("/collateral/0/status").asString()).as("handed back").isEqualTo("RELEASED");
        repay(loanId, "1.00", "ACCOUNT", "repay-closed").expectError(422, "LOAN_NOT_ACTIVE");

        // Interest income 24 + 12, fees 29; the loan GLs are back at zero.
        StaffHandle accountant = fixtures.createStaff(tenant, "accountant", tenant.headOfficeId(), true,
                "ACCOUNTANT");
        JsonNode trial = api.get("/api/v1/ledger/trial-balance", accountant.token()).expect(200).data();
        assertThat(trial.get("balanced").asBoolean()).isTrue();
        assertThat(glCredit(trial, "4100")).isEqualTo("36.00");
        assertThat(glCredit(trial, "4230")).isEqualTo("29.00");
        assertThat(glDebit(trial, "1210")).isEqualTo("0.00");
        assertThat(glDebit(trial, "1220")).isEqualTo("0.00");

        // The schedule records what each repayment settled, so a repayment is not reversed as a plain transaction.
        String accountRepayment = loan.at("/repayments/2/transactionId").asString();
        api.post("/api/v1/transactions/" + accountRepayment + "/reversal", manager.token(),
                Map.of("reason", "Mistake")).expectError(422, "REVERSAL_NOT_SUPPORTED");
    }

    @Test
    void largeLoansNeedSecurityAndASecondApproverAndEndOfDayStillReconciles() {
        String applicationId = apply("6000.00", 12);
        submitAndRecommend(applicationId);
        Map<String, Object> approval = approval("6000.00", 12, version(applicationId));
        api.post(path(applicationId, "approve"), manager.token(), approval).expectError(422, "GUARANTORS_REQUIRED");
        securedBy(applicationId, "2000.00");
        approval.put("version", version(applicationId));
        api.post(path(applicationId, "approve"), manager.token(), approval).expectError(422, "COLLATERAL_REQUIRED");
        String extra = api.post(path(applicationId, "collateral"), lender.token(), collateral("1000.00")).expect(201)
                .data().at("/collateral/1/id").asString();
        api.post(path(applicationId, "collateral/" + extra + "/verify"), reviewer.token(), null).expect(200);
        approval.put("version", version(applicationId));
        JsonNode approved = api.post(path(applicationId, "approve"), manager.token(), approval).expect(200).data();
        assertThat(approved.at("/application/status").asString()).as("waits for a second approver")
                .isEqualTo("RECOMMENDED");
        assertThat(approved.at("/application/secondApprovalRequired").asBoolean()).isTrue();
        api.postIdempotent(disburse(applicationId), thirdManager.token(), "early-" + applicationId, Map.of())
                .expectError(409, "INVALID_STEP");

        api.post(path(applicationId, "second-approval"), manager.token(), step(applicationId))
                .expectError(403, "SEPARATION_OF_DUTIES");
        api.post(path(applicationId, "second-approval"), secondManager.token(), step(applicationId)).expect(200);
        api.postIdempotent(disburse(applicationId), secondManager.token(), "d2-" + applicationId, Map.of())
                .expectError(403, "SEPARATION_OF_DUTIES");
        String loanId = api.postIdempotent(disburse(applicationId), thirdManager.token(), "d3-" + applicationId,
                Map.of()).expect(201).data().at("/loan/id").asString();
        assertThat(balance()).isEqualTo("5875.00");

        // The database refuses the loan officer taking a decision even if the service were bypassed.
        UUID application = UUID.fromString(applicationId);
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbc.update("INSERT INTO core.loan_application_step"
                        + " (id, tenant_id, application_id, step_type, actor_id, occurred_at)"
                        + " VALUES (gen_random_uuid(), ?, ?, 'SECOND_APPROVE', ?, now())", tenant.id(), application,
                lender.id())))
                .isInstanceOf(DataIntegrityViolationException.class);

        endOfDay("2027-03-01");
        endOfDay("2027-03-02");
        assertThat(loan(loanId).at("/loan/principalOutstanding").asString()).isEqualTo("6000.00");
    }

    @Test
    void onlyEligibleCustomersBorrowWithinTheProductsLimits() {
        Map<String, Object> request = application("20000.00", 6);
        api.post("/api/v1/loan-applications", lender.token(), request).expectError(422, "OUTSIDE_PRODUCT_LIMITS");
        request = application("1000.00", 13);
        api.post("/api/v1/loan-applications", lender.token(), request).expectError(422, "OUTSIDE_PRODUCT_LIMITS");
        request = application("1000.00", 6);
        api.post("/api/v1/loan-applications", teller.token(), request).expectError(403, "ACCESS_DENIED");

        String unverified = fixtures.createIndividual(lender.token(), tenant.headOfficeId(), "Kojo", "Asante",
                "+233240000001").get("id").asString();
        request.put("customerId", unverified);
        api.post("/api/v1/loan-applications", lender.token(), request).expectError(422, "CUSTOMER_NOT_ELIGIBLE");

        String other = fixtures.verifiedIndividual(lender, manager, tenant.headOfficeId(), "Esi").toString();
        request = application("1000.00", 6);
        request.put("customerId", other);
        api.post("/api/v1/loan-applications", lender.token(), request)
                .expectError(422, "INVALID_DISBURSEMENT_ACCOUNT");

        JsonNode product = api.get("/api/v1/loan-products/" + productId, lender.token()).expect(200).data();
        api.put("/api/v1/loan-products/" + productId + "/versions/" + product.at("/currentVersion/id").asString(),
                tenant.adminToken(), loanTerms()).expectError(422, "VERSION_NOT_EDITABLE");
        String draftOnly = api.post("/api/v1/loan-products", tenant.adminToken(), Map.of("code", "DRAFTY",
                "name", "Draft only", "terms", loanTerms())).expect(201).data().get("id").asString();
        request = application("1000.00", 6);
        request.put("productId", draftOnly);
        api.post("/api/v1/loan-applications", lender.token(), request).expectError(422, "PRODUCT_NOT_AVAILABLE");

        // A rejected application gives its collateral back.
        String applicationId = apply("1000.00", 6);
        String collateralId = api.post(path(applicationId, "collateral"), lender.token(), collateral("500.00"))
                .expect(201).data().at("/collateral/0/id").asString();
        api.post(path(applicationId, "submit"), lender.token(), step(applicationId)).expect(200);
        api.post(path(applicationId, "reject"), manager.token(), Map.of("note", "Income too low",
                "version", version(applicationId))).expect(200);
        JsonNode released = api.post(path(applicationId, "collateral/" + collateralId + "/release"),
                manager.token(), null).expect(200).data();
        assertThat(released.at("/application/status").asString()).isEqualTo("REJECTED");
        assertThat(released.at("/collateral/0/status").asString()).isEqualTo("RELEASED");
        assertThat(released.get("steps")).extracting(s -> s.get("type").asString())
                .containsExactly("SUBMIT", "REJECT");
    }

    // ---------------------------------------------------------------------------------------------------------

    private String publishedLoanProduct() {
        JsonNode created = api.post("/api/v1/loan-products", tenant.adminToken(), Map.of("code", "BIZ01",
                "name", "Business loan", "terms", loanTerms())).expect(201).data();
        String id = created.get("id").asString();
        api.post("/api/v1/loan-products/" + id + "/versions/" + created.at("/versions/0/id").asString()
                + "/publish", tenant.adminToken(), null).expect(200);
        return id;
    }

    private static Map<String, Object> loanTerms() {
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
        terms.put("requiredGuarantors", 1);
        terms.put("collateralCoverage", "50");
        terms.put("secondApprovalAbove", "5000.00");
        return terms;
    }

    private Map<String, Object> application(String amount, int installments) {
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

    private String apply(String amount, int installments) {
        return api.post("/api/v1/loan-applications", lender.token(), application(amount, installments)).expect(201)
                .data().at("/application/id").asString();
    }

    /**
     * One verified guarantor and verified collateral worth {@code forcedSaleValue}; the loan officer may not verify.
     */
    private void securedBy(String applicationId, String forcedSaleValue) {
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

    private static Map<String, Object> collateral(String forcedSaleValue) {
        return Map.of("category", "INVENTORY", "description", "Shop stock", "estimatedValue", forcedSaleValue,
                "forcedSaleValue", forcedSaleValue, "valuationDate", "2027-02-20");
    }

    private void submitAndRecommend(String applicationId) {
        api.post(path(applicationId, "submit"), lender.token(), step(applicationId)).expect(200);
        api.post(path(applicationId, "assess"), lender.token(), Map.of("riskRating", "LOW",
                "note", "Steady trade", "version", version(applicationId))).expect(200);
        api.post(path(applicationId, "recommend"), lender.token(), step(applicationId))
                .expectError(403, "SEPARATION_OF_DUTIES");
        api.post(path(applicationId, "recommend"), reviewer.token(), step(applicationId)).expect(200);
    }

    private void approved(String applicationId, String amount, int installments, StaffHandle approver) {
        submitAndRecommend(applicationId);
        api.post(path(applicationId, "approve"), reviewer.token(), approval(amount, installments,
                version(applicationId))).expectError(403, "ACCESS_DENIED");
        JsonNode approved = api.post(path(applicationId, "approve"), approver.token(), approval(amount, installments,
                version(applicationId))).expect(200).data();
        assertThat(approved.at("/application/status").asString()).isEqualTo("APPROVED");
        assertThat(approved.get("steps")).extracting(s -> s.get("type").asString())
                .containsExactly("SUBMIT", "ASSESS", "RECOMMEND", "APPROVE");
    }

    private static Map<String, Object> approval(String amount, int installments, long version) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("approvedAmount", amount);
        request.put("approvedInstallments", installments);
        request.put("note", "Within policy");
        request.put("version", version);
        return request;
    }

    private Map<String, Object> step(String applicationId) {
        return Map.of("note", "OK", "version", version(applicationId));
    }

    private long version(String applicationId) {
        return api.get("/api/v1/loan-applications/" + applicationId, lender.token()).expect(200).data()
                .at("/application/version").asLong();
    }

    private static String path(String applicationId, String action) {
        return "/api/v1/loan-applications/" + applicationId + "/" + action;
    }

    private static String disburse(String applicationId) {
        return path(applicationId, "disburse");
    }

    private com.company.banking.support.Api.Response repay(String loanId, String amount, String source, String key) {
        return api.postIdempotent("/api/v1/loans/" + loanId + "/repayments", teller.token(), key, Map.of(
                "amount", amount, "source", source));
    }

    private JsonNode loan(String loanId) {
        return api.get("/api/v1/loans/" + loanId, lender.token()).expect(200).data();
    }

    private static List<String> statuses(JsonNode loan) {
        List<String> statuses = new ArrayList<>();
        loan.get("schedule").forEach(installment -> statuses.add(installment.get("status").asString()));
        return statuses;
    }

    private void jumpTo(LocalDate from, LocalDate to) {
        inTenant(tenant.id(), () -> businessDates.roll(from, to));
    }

    private void endOfDay(String expectedDate) {
        JsonNode run = api.post("/api/v1/operations/eod", tenant.adminToken(), null).expect(202).data();
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(run.get("businessDate").asString()).isEqualTo(expectedDate);
    }

    private String balance() {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }

    private static String glDebit(JsonNode trial, String code) {
        return row(trial, code).get("debitBalance").asString();
    }

    private static String glCredit(JsonNode trial, String code) {
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
