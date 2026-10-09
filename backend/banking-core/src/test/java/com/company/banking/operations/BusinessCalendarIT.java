package com.company.banking.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.common.error.BankingException;
import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.operations.service.BusinessCalendarService;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

class BusinessCalendarIT extends LedgerIntegrationTest {

    private static final LocalDate FRIDAY = LocalDate.of(2030, 1, 4);
    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Autowired
    private BusinessCalendarService calendar;

    @Autowired
    private BusinessDateService businessDates;

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
    void anInstitutionStartsOnTodaysDateWithAMondayToFridayWeek() {
        JsonNode date = api.get("/api/v1/operations/business-date", admin).expect(200).data();

        assertThat(date.get("businessDate").asString()).isEqualTo(LocalDate.now(ZoneId.of("Africa/Accra")).toString());
        assertThat(date.get("previousBusinessDate").isNull()).isTrue();
        assertThat(date.get("workingDays")).hasSize(5);
        StaffHandle teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        api.get("/api/v1/operations/business-date", teller.token()).expect(200);
    }

    @Test
    void theNextBusinessDateSkipsTheWeekendAndHolidays() {
        assertThat(inTenant(tenant.id(), () -> calendar.nextBusinessDate(FRIDAY))).isEqualTo(MONDAY);

        api.post("/api/v1/operations/holidays", admin, Map.of("date", MONDAY.toString(), "name", "Constitution Day"))
                .expect(201);
        assertThat(inTenant(tenant.id(), () -> calendar.nextBusinessDate(FRIDAY))).isEqualTo(MONDAY.plusDays(1));
        assertThat(api.get("/api/v1/operations/holidays?year=2030", admin).expect(200).data()).hasSize(1);

        long version = api.get("/api/v1/operations/business-date", admin).expect(200).data().get("calendarVersion")
                .asLong();
        api.put("/api/v1/operations/working-week", admin, Map.of("workingDays",
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"), "version", version))
                .expect(200);
        assertThat(inTenant(tenant.id(), () -> calendar.nextBusinessDate(FRIDAY))).isEqualTo(FRIDAY.plusDays(1));
        api.put("/api/v1/operations/working-week", admin, Map.of("workingDays", List.of("MONDAY"), "version", version))
                .expectError(409, "CONCURRENT_MODIFICATION");

        api.delete("/api/v1/operations/holidays/" + MONDAY, admin).expect(204);
        assertThat(api.get("/api/v1/operations/holidays?year=2030", admin).expect(200).data()).isEmpty();
    }

    @Test
    void onlyFutureDaysCanBecomeHolidaysAndOnlyOperationsManagersChangeTheCalendar() {
        String today = LocalDate.now(ZoneId.of("Africa/Accra")).toString();
        api.post("/api/v1/operations/holidays", admin, Map.of("date", today, "name", "Too late"))
                .expectError(422, "HOLIDAY_NOT_IN_FUTURE");
        api.post("/api/v1/operations/holidays", admin, Map.of("date", "2030-03-06", "name", "Independence Day"))
                .expect(201);
        api.post("/api/v1/operations/holidays", admin, Map.of("date", "2030-03-06", "name", "Again"))
                .expectError(409, "HOLIDAY_EXISTS");
        StaffHandle manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        api.post("/api/v1/operations/holidays", manager.token(), Map.of("date", "2030-05-01", "name", "May Day"))
                .expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/operations/holidays?year=2030", manager.token()).expect(200);
    }

    @Test
    void theBusinessDateOnlyMovesForwardAndOnlyFromTheExpectedDate() {
        LocalDate today = inTenant(tenant.id(), () -> businessDates.today());

        assertThatThrownBy(() -> inTenant(tenant.id(), () -> businessDates.roll(today.minusDays(1), today.plusDays(1))))
                .isInstanceOfSatisfying(BankingException.class,
                        failure -> assertThat(failure.getErrorCode().code()).isEqualTo("CONCURRENT_MODIFICATION"));
        inTenant(tenant.id(), () -> businessDates.roll(today, today.plusDays(1)));
        assertThat(inTenant(tenant.id(), () -> businessDates.today())).isEqualTo(today.plusDays(1));
        assertThat(inTenant(tenant.id(), () -> businessDates.previous())).isEqualTo(today);

        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.business_day SET business_date = business_date - 1").update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("only move forward");
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "INSERT INTO core.holiday (tenant_id, holiday_date, name, created_at)"
                                + " VALUES (:tenantId, :date, 'Past', now())")
                .param("tenantId", tenant.id())
                .param("date", today)
                .update()))
                .isInstanceOf(DataAccessException.class);
    }
}
