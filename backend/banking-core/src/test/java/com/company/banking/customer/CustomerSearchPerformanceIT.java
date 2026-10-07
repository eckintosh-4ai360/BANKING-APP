package com.company.banking.customer;

import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import com.company.banking.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1B exit gate: customer search over 100,000 customers of one institution answers in under 300 ms through
 * the full API (security, row-level security, count and page queries).
 */
class CustomerSearchPerformanceIT extends IntegrationTest {

    private static final int CUSTOMERS = 100_000;
    private static final long BUDGET_MS = 300;

    @Autowired
    private DataSource dataSource;

    @Test
    void searchStaysWithinBudgetAt100kCustomers() throws SQLException {
        TenantHandle tenant = fixtures.onboardTenant();
        seed(tenant);

        List<String> queries = List.of("mensah", "osei 4242", "0012345", "9000012345");
        for (String query : queries) {
            long median = medianMillis(tenant.adminToken(), query);
            assertThat(median).as("median latency of q=%s", query).isLessThan(BUDGET_MS);
        }
        long matches = api.get("/api/v1/customers?q=mensah", tenant.adminToken()).expect(200).data()
                .get("totalItems").asLong();
        assertThat(matches).isGreaterThan(1_000);
    }

    private long medianMillis(String token, String query) {
        String path = "/api/v1/customers?size=20&q=" + query.replace(" ", "%20");
        for (int warmUp = 0; warmUp < 3; warmUp++) {
            api.get(path, token).expect(200);
        }
        List<Long> timings = new ArrayList<>();
        for (int run = 0; run < 7; run++) {
            long start = System.nanoTime();
            api.get(path, token).expect(200);
            timings.add((System.nanoTime() - start) / 1_000_000);
        }
        return timings.stream().sorted().toList().get(timings.size() / 2);
    }

    /**
     * Bulk-inserts customers as the application role inside the tenant (so row-level security applies), then
     * refreshes planner statistics as the superuser, as autovacuum would in production.
     */
    private void seed(TenantHandle tenant) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement setTenant = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', ?, true)")) {
                setTenant.setString(1, tenant.id().toString());
                setTenant.execute();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO core.customer (id, tenant_id, customer_number, customer_type, status, kyc_status,
                        risk_level, display_name, primary_phone, home_branch_id, onboarding_channel,
                        profile_updated_at, created_at, updated_at, version)
                    SELECT gen_random_uuid(), ?, '9' || lpad(g::text, 9, '0'), 'INDIVIDUAL', 'PENDING', 'NOT_STARTED',
                        'UNASSESSED',
                        (ARRAY['Kwame','Ama','Kofi','Akosua','Yaw','Efua','Kojo','Abena','Kwesi','Adwoa'])[1 + g % 10]
                          || ' ' ||
                        (ARRAY['Mensah','Owusu','Boateng','Asante','Osei','Darko','Agyeman','Appiah','Ansah','Addo',
                               'Quaye','Tetteh','Nkrumah','Badu','Frimpong'])[1 + (g / 10) % 15]
                          || ' ' || g,
                        '+23324' || lpad(g::text, 7, '0'), ?, 'BRANCH', now(), now(), now(), 0
                    FROM generate_series(1, ?) AS g""")) {
                insert.setObject(1, tenant.id());
                insert.setObject(2, tenant.headOfficeId());
                insert.setInt(3, CUSTOMERS);
                assertThat(insert.executeUpdate()).isEqualTo(CUSTOMERS);
            }
            connection.commit();
        }
        TestDatabase.Server server = TestDatabase.get();
        try (Connection admin = DriverManager.getConnection(server.bankingUrl(), server.superUser(),
                server.superPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("ANALYZE core.customer");
        }
    }
}
