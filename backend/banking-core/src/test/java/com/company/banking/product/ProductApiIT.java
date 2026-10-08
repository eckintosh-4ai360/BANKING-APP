package com.company.banking.product;

import static com.company.banking.support.ProductRequests.product;
import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Deposit products: versioned terms, validation against the chart of accounts, and immutability of published
 * terms in the application and in the database.
 */
class ProductApiIT extends LedgerIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;
    private String admin;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        admin = tenant.adminToken();
    }

    @Test
    void aProductIsOfferedOnlyOncePublishedAndNewVersionsLeaveOldTermsIntact() {
        JsonNode created = api.post("/api/v1/products", admin, product("SAVE01", "SAVINGS", terms("50.00")))
                .expect(201).data();
        assertThat(created.get("currentVersion").isNull()).isTrue();
        JsonNode draft = created.at("/versions/0");
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("minOpeningBalance").asString()).isEqualTo("50.00");
        assertThat(draft.get("interestRate").asString()).isEqualTo("5.25");
        assertThat(draft.get("interestPostingFrequency").asString()).isEqualTo("MONTHLY");
        assertThat(draft.get("dormancyDays").asInt()).isEqualTo(365);
        UUID savingsGl = inTenant(tenant.id(),
                () -> chartOfAccounts.requireSystem(SystemAccount.SAVINGS_DEPOSITS).id());
        assertThat(draft.get("depositGlId").asString()).isEqualTo(savingsGl.toString());

        String productId = created.get("id").asString();
        String v1 = draft.get("id").asString();
        JsonNode published = api.post(version(productId, v1) + "/publish", admin, null).expect(200).data();
        assertThat(published.at("/currentVersion/id").asString()).isEqualTo(v1);
        assertThat(published.at("/currentVersion/status").asString()).isEqualTo("PUBLISHED");
        assertThat(published.at("/currentVersion/publishedAt").isNull()).isFalse();

        api.put(version(productId, v1), admin, terms("10.00")).expectError(422, "VERSION_NOT_EDITABLE");
        api.post(version(productId, v1) + "/publish", admin, null).expectError(422, "VERSION_NOT_EDITABLE");

        JsonNode drafted = api.post("/api/v1/products/" + productId + "/versions", admin, null).expect(201).data();
        api.post("/api/v1/products/" + productId + "/versions", admin, null).expectError(409, "DRAFT_ALREADY_EXISTS");
        String v2 = drafted.at("/versions/0/id").asString();
        assertThat(drafted.at("/versions/0/versionNo").asInt()).isEqualTo(2);
        assertThat(drafted.at("/versions/0/minOpeningBalance").asString()).as("copied from v1").isEqualTo("50.00");
        assertThat(drafted.at("/currentVersion/id").asString()).as("still v1 until published").isEqualTo(v1);

        api.put(version(productId, v2), admin, terms("100.00")).expect(200);
        JsonNode republished = api.post(version(productId, v2) + "/publish", admin, null).expect(200).data();
        assertThat(republished.at("/currentVersion/minOpeningBalance").asString()).isEqualTo("100.00");
        assertThat(republished.at("/versions/1/status").asString()).isEqualTo("RETIRED");
        assertThat(republished.at("/versions/1/minOpeningBalance").asString()).isEqualTo("50.00");
    }

    @Test
    void termsAreCheckedAgainstTheChartOfAccountsCurrencyAndKycTiers() {
        UUID incomeGl = inTenant(tenant.id(),
                () -> chartOfAccounts.requireSystem(SystemAccount.ACCOUNT_FEE_INCOME).id());
        Map<String, Object> wrongGl = terms("0");
        wrongGl.put("depositGlId", incomeGl.toString());
        api.post("/api/v1/products", admin, product("BAD01", "SAVINGS", wrongGl))
                .expectError(422, "INVALID_GL_MAPPING");

        api.post("/api/v1/products", admin, product("BAD02", "SAVINGS", terms("10.005")))
                .expectError(422, "INVALID_AMOUNT");

        Map<String, Object> inconsistent = terms("0");
        inconsistent.put("maxWithdrawalAmount", "500.00");
        inconsistent.put("dailyWithdrawalLimit", "100.00");
        api.post("/api/v1/products", admin, product("BAD03", "SAVINGS", inconsistent))
                .expectError(422, "INVALID_TERMS");

        Map<String, Object> overdraftNotAllowed = terms("0");
        overdraftNotAllowed.put("maxOverdraftLimit", "100.00");
        api.post("/api/v1/products", admin, product("BAD04", "CURRENT", overdraftNotAllowed))
                .expectError(422, "INVALID_TERMS");

        Map<String, Object> unknownTier = terms("0");
        unknownTier.put("requiredKycTier", "TIER_9");
        api.post("/api/v1/products", admin, product("BAD05", "SAVINGS", unknownTier))
                .expectError(422, "KYC_TIER_NOT_AVAILABLE");

        Map<String, Object> unknownCurrency = terms("0");
        unknownCurrency.put("currency", "XYZ");
        api.post("/api/v1/products", admin, product("BAD06", "SAVINGS", unknownCurrency))
                .expectError(422, "CURRENCY_NOT_SUPPORTED");

        api.post("/api/v1/products", admin, product("GOOD1", "SUSU", terms("0"))).expect(201);
        api.post("/api/v1/products", admin, product("GOOD1", "SUSU", terms("0")))
                .expectError(409, "DUPLICATE_RESOURCE");
        assertThat(api.get("/api/v1/products", admin).expect(200).data()).hasSize(1);
    }

    @Test
    void onlyProductManagersChangeProducts() {
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        StaffHandle teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        String productId = api.post("/api/v1/products", admin, product("SAVE01", "SAVINGS", terms("0")))
                .expect(201).data().get("id").asString();

        api.get("/api/v1/products/" + productId, officer.token()).expect(200);
        api.post("/api/v1/products", officer.token(), product("SAVE02", "SAVINGS", terms("0")))
                .expectError(403, "ACCESS_DENIED");
        api.post("/api/v1/products/" + productId + "/versions", officer.token(), null)
                .expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/products", teller.token()).expectError(403, "ACCESS_DENIED");
    }

    @Test
    void theDatabaseRefusesToChangeOrDeletePublishedTerms() {
        JsonNode created = api.post("/api/v1/products", admin, product("SAVE01", "SAVINGS", terms("0")))
                .expect(201).data();
        String productId = created.get("id").asString();
        UUID versionId = UUID.fromString(created.at("/versions/0/id").asString());
        api.post(version(productId, versionId.toString()) + "/publish", admin, null).expect(200);

        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.account_product_version SET min_opening_balance = 1 WHERE id = :id")
                .param("id", versionId).update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("can only be retired");
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.account_product_version SET status = 'DRAFT', published_at = NULL WHERE id = :id")
                .param("id", versionId).update()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "DELETE FROM core.account_product_version WHERE id = :id")
                .param("id", versionId).update()))
                .isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static String version(String productId, String versionId) {
        return "/api/v1/products/" + productId + "/versions/" + versionId;
    }
}
