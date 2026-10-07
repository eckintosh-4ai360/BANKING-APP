package com.company.banking.platform.seed;

import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles(value = {"test", "local"}, inheritProfiles = false)
class LocalDemoDataSeederIT extends IntegrationTest {

    @Autowired
    private LocalDemoDataSeeder seeder;

    @Test
    void seedsADemoInstitutionWhoseUsersCanSignInWithTheirRoles() {
        Map<String, String> expectedRoles = Map.of(
                "admin", "INSTITUTION_ADMIN",
                "manager", "BRANCH_MANAGER",
                "teller", "TELLER",
                "loanofficer", "LOAN_OFFICER",
                "fieldofficer", "FIELD_OFFICER",
                "compliance", "COMPLIANCE_OFFICER",
                "auditor", "AUDITOR");

        expectedRoles.forEach((username, role) -> {
            String token = fixtures.login(LocalDemoDataSeeder.DEMO_TENANT, username,
                    LocalDemoDataSeeder.DEMO_STAFF_PASSWORD).accessToken();
            JsonNode me = api.get("/api/v1/me", token).expect(200).data();
            assertThat(me.at("/roles/0/code").asString()).as(username).isEqualTo(role);
            assertThat(me.get("passwordChangeRequired").asBoolean()).isFalse();
        });

        String admin = fixtures.login(LocalDemoDataSeeder.DEMO_TENANT, "admin",
                LocalDemoDataSeeder.DEMO_STAFF_PASSWORD).accessToken();
        assertThat(api.get("/api/v1/branches", admin).expect(200).data().get("totalItems").asLong()).isEqualTo(3);

        JsonNode customers = api.get("/api/v1/customers?kycStatus=NOT_STARTED", admin).expect(200).data();
        assertThat(customers.get("totalItems").asLong()).isEqualTo(3);
        String tellerToken = fixtures.login(LocalDemoDataSeeder.DEMO_TENANT, "teller",
                LocalDemoDataSeeder.DEMO_STAFF_PASSWORD).accessToken();
        assertThat(api.get("/api/v1/customers", tellerToken).expect(200).data().get("totalItems").asLong())
                .as("a head-office teller sees only head-office customers").isEqualTo(1);
    }

    @Test
    void runningTheSeederAgainChangesNothing() {
        String admin = fixtures.login(LocalDemoDataSeeder.DEMO_TENANT, "admin",
                LocalDemoDataSeeder.DEMO_STAFF_PASSWORD).accessToken();
        long staffBefore = api.get("/api/v1/staff", admin).expect(200).data().get("totalItems").asLong();

        seeder.run(new DefaultApplicationArguments());

        long staffAfter = api.get("/api/v1/staff", admin).expect(200).data().get("totalItems").asLong();
        assertThat(staffAfter).isEqualTo(staffBefore).isEqualTo(List.of(1, 2, 3, 4, 5, 6, 7).size());
    }
}
