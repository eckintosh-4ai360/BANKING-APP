package com.company.banking.customer;

import com.company.banking.support.Fixtures;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1B exit gate: identity and tax numbers never appear in application logs, the audit trail or plain
 * database columns, and documents are encrypted at rest.
 */
@ExtendWith(OutputCaptureExtension.class)
class PiiProtectionIT extends IntegrationTest {

    private static final String ID_DIGITS = "987654321";
    private static final String GHANA_CARD = "GHA-" + ID_DIGITS + "-0";
    private static final String TAX_ID = "P0071234567";
    private static final String UNIQUE_SURNAME = "Quarshie-Zyxwv";

    @Autowired
    private DataSource dataSource;

    @Value("${banking.storage.base-path}")
    private String storagePath;

    @Test
    void sensitiveIdentifiersStayOutOfLogsAuditAndPlainColumns(CapturedOutput output) throws SQLException {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        StaffHandle compliance = fixtures.createStaff(tenant, "compliance", tenant.headOfficeId(), true,
                "COMPLIANCE_OFFICER");

        Map<String, Object> individual = new LinkedHashMap<>();
        individual.put("firstName", "Efua");
        individual.put("lastName", UNIQUE_SURNAME);
        individual.put("dateOfBirth", "1985-02-03");
        individual.put("taxId", TAX_ID);
        JsonNode customer = api.post("/api/v1/customers", officer.token(), Map.of("customerType", "INDIVIDUAL",
                "homeBranchId", tenant.headOfficeId().toString(), "primaryPhone", "+233244987654",
                "individual", individual)).expect(201).data();
        String customerId = customer.get("id").asString();
        assertThat(customer.at("/individual/taxIdMasked").asString()).isEqualTo("*******4567");

        JsonNode identification = fixtures.addGhanaCard(officer.token(), customerId, GHANA_CARD);
        api.post("/api/v1/customers/" + customerId + "/identifications", officer.token(), Map.of("idTypeCode",
                "GHANA_CARD", "idNumber", GHANA_CARD, "expiryDate", "2031-01-01")).expect(409);
        api.get("/api/v1/customers?q=" + UNIQUE_SURNAME.substring(0, 8), officer.token()).expect(200);
        api.get("/api/v1/customers/" + customerId + "/identifications/" + identification.get("id").asString()
                + "/number", compliance.token()).expect(200);
        String caseId = api.post("/api/v1/customers/" + customerId + "/kyc-cases", officer.token(), Map.of(
                "caseType", "ONBOARDING", "targetTierCode", "TIER_1")).expect(201).data().get("id").asString();
        api.post("/api/v1/kyc/cases/" + caseId + "/identity-check", officer.token(), null).expect(200);

        assertThat(output.getAll())
                .doesNotContain(ID_DIGITS)
                .doesNotContain(TAX_ID)
                .doesNotContain(UNIQUE_SURNAME)
                .doesNotContain("+233244987654");

        String auditText = String.join("\n", tenantRows(tenant.id(), """
                SELECT coalesce(before_state::text, '') || coalesce(after_state::text, '')
                       || coalesce(metadata::text, '') FROM core.audit_log""", 1));
        assertThat(auditText).isNotBlank().doesNotContain(ID_DIGITS).doesNotContain(TAX_ID);

        String storedIdentity = String.join("\n", tenantRows(tenant.id(), """
                SELECT id_number_encrypted || ' ' || id_number_masked FROM core.customer_identification""", 1));
        assertThat(storedIdentity).doesNotContain(ID_DIGITS).contains("v1:");
        String storedTax = String.join("\n", tenantRows(tenant.id(), """
                SELECT tax_id_encrypted || ' ' || tax_id_masked FROM core.individual_profile""", 1));
        assertThat(storedTax).doesNotContain(TAX_ID).contains("v1:");
    }

    @Test
    void documentsAreTypeCheckedAndEncryptedAtRest() throws IOException {
        TenantHandle tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        StaffHandle compliance = fixtures.createStaff(tenant, "compliance", tenant.headOfficeId(), true,
                "COMPLIANCE_OFFICER");
        String customerId = fixtures.createIndividual(officer.token(), tenant.headOfficeId(), "Ama", "Mensah",
                "+233244111222").get("id").asString();
        String documents = "/api/v1/customers/" + customerId + "/documents";

        api.upload(documents, officer.token(), "id.png", "<script>alert(1)</script>".getBytes(),
                "documentType", "ID_FRONT").expectError(415, "UNSUPPORTED_DOCUMENT_TYPE");
        api.upload(documents, officer.token(), "id.png", Fixtures.PNG, "documentType", "PASSPORT_SCAN")
                .expectError(400, "VALIDATION_FAILED");

        JsonNode uploaded = api.upload(documents, officer.token(), "../../my id.exe", Fixtures.PNG,
                "documentType", "ID_FRONT").expect(201).data();
        assertThat(uploaded.get("contentType").asString()).isEqualTo("image/png");
        assertThat(uploaded.get("fileName").asString()).isEqualTo("my id.png");
        assertThat(uploaded.get("reviewStatus").asString()).isEqualTo("PENDING_REVIEW");

        List<Path> stored;
        try (Stream<Path> files = Files.walk(Path.of(storagePath, tenant.id().toString()))) {
            stored = files.filter(Files::isRegularFile).toList();
        }
        assertThat(stored).hasSize(1);
        byte[] atRest = Files.readAllBytes(stored.getFirst());
        assertThat(Arrays.equals(Arrays.copyOf(atRest, 4), Arrays.copyOf(Fixtures.PNG, 4))).isFalse();
        assertThat(atRest.length).isGreaterThan(Fixtures.PNG.length);

        byte[] downloaded = api.download(documents + "/" + uploaded.get("id").asString() + "/content",
                compliance.token());
        assertThat(downloaded).isEqualTo(Fixtures.PNG);
        assertThat(api.get("/api/v1/audit-logs?action=CUSTOMER_DOCUMENT_VIEWED", tenant.adminToken()).expect(200)
                .data().get("totalItems").asLong()).isEqualTo(1);
    }

    private List<String> tenantRows(UUID tenantId, String sql, int column) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.tenant_id', ?, true)")) {
                    setTenant.setString(1, tenantId.toString());
                    setTenant.execute();
                }
                List<String> rows = new ArrayList<>();
                try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rs = query.executeQuery()) {
                    while (rs.next()) {
                        rows.add(rs.getString(column));
                    }
                }
                return rows;
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }
}
