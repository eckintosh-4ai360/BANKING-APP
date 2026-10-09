package com.company.banking.operations.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.operations.dto.BusinessDateResponse;
import com.company.banking.operations.dto.HolidayRequest;
import com.company.banking.operations.dto.HolidayResponse;
import com.company.banking.operations.dto.WorkingWeekRequest;
import com.company.banking.operations.entity.BusinessCalendar;
import com.company.banking.operations.entity.Holiday;
import com.company.banking.operations.exception.OperationsErrorCode;
import com.company.banking.operations.repository.BusinessCalendarRepository;
import com.company.banking.operations.repository.HolidayRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The institution's business calendar: its working week and holidays, and from them the next business date that
 * end-of-day moves to.
 */
@Service
@RequiredArgsConstructor
public class BusinessCalendarService {

    private static final String RESOURCE = "BUSINESS_CALENDAR";
    private static final int MAX_DAYS_AHEAD = 366;

    private final BusinessCalendarRepository calendars;
    private final HolidayRepository holidays;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Monday to Friday, no holidays. Idempotent.
     */
    @Transactional
    public void provisionDefaults() {
        UUID tenantId = TenantContext.requireTenantId();
        if (!calendars.existsById(tenantId)) {
            calendars.saveAndFlush(new BusinessCalendar(tenantId, BusinessCalendar.MONDAY_TO_FRIDAY, clock.instant()));
        }
    }

    @Transactional(readOnly = true)
    public BusinessDateResponse businessDate() {
        BusinessCalendar calendar = calendar();
        LocalDate today = businessDates.today();
        return new BusinessDateResponse(today, businessDates.previous(), nextBusinessDate(today),
                calendar.workingDays().stream().map(DayOfWeek::name).toList(), calendar.getVersion());
    }

    /**
     * The first working day after {@code date} that is not a holiday.
     */
    @Transactional(readOnly = true)
    public LocalDate nextBusinessDate(LocalDate date) {
        Set<DayOfWeek> working = calendar().workingDays();
        Set<LocalDate> closed = holidays.findBetween(TenantContext.requireTenantId(), date.plusDays(1),
                        date.plusDays(MAX_DAYS_AHEAD)).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());
        for (int ahead = 1; ahead <= MAX_DAYS_AHEAD; ahead++) {
            LocalDate candidate = date.plusDays(ahead);
            if (working.contains(candidate.getDayOfWeek()) && !closed.contains(candidate)) {
                return candidate;
            }
        }
        throw new BankingException(OperationsErrorCode.NO_BUSINESS_DAY_AHEAD);
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> holidays(int year) {
        return holidays.findBetween(TenantContext.requireTenantId(), LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31)).stream()
                .map(BusinessCalendarService::toResponse)
                .toList();
    }

    @Transactional
    public HolidayResponse addHoliday(HolidayRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        requireFuture(request.date());
        Holiday.Key key = new Holiday.Key(tenantId, request.date());
        if (holidays.existsById(key)) {
            throw new BankingException(OperationsErrorCode.HOLIDAY_EXISTS);
        }
        Holiday holiday = holidays.saveAndFlush(new Holiday(tenantId, request.date(), request.name().trim(),
                clock.instant(), CurrentActor.currentActorId().orElse(null)));
        HolidayResponse response = toResponse(holiday);
        auditService.record(AuditEvent.builder("HOLIDAY_ADDED", RESOURCE)
                .resourceId(request.date())
                .after(response)
                .build());
        return response;
    }

    @Transactional
    public void removeHoliday(LocalDate date) {
        requireFuture(date);
        Holiday holiday = holidays.findById(new Holiday.Key(TenantContext.requireTenantId(), date))
                .orElseThrow(() -> new ResourceNotFoundException("Holiday"));
        holidays.delete(holiday);
        holidays.flush();
        auditService.record(AuditEvent.builder("HOLIDAY_REMOVED", RESOURCE)
                .resourceId(date)
                .before(toResponse(holiday))
                .build());
    }

    @Transactional
    public BusinessDateResponse changeWorkingWeek(WorkingWeekRequest request) {
        BusinessCalendar calendar = calendar();
        ConcurrentModificationException.assertVersion(request.version(), calendar.getVersion());
        Set<DayOfWeek> before = calendar.workingDays();
        Set<DayOfWeek> days = request.workingDays().stream().map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
        calendar.changeWorkingDays(days, clock.instant(), CurrentActor.currentActorId().orElse(null));
        calendars.saveAndFlush(calendar);
        auditService.record(AuditEvent.builder("WORKING_WEEK_CHANGED", RESOURCE)
                .resourceId(calendar.getTenantId())
                .before(Map.of("workingDays", before))
                .after(Map.of("workingDays", days))
                .build());
        return businessDate();
    }

    private void requireFuture(LocalDate date) {
        if (!date.isAfter(businessDates.today())) {
            throw new BankingException(OperationsErrorCode.HOLIDAY_NOT_IN_FUTURE);
        }
    }

    private BusinessCalendar calendar() {
        UUID tenantId = TenantContext.requireTenantId();
        return calendars.findById(tenantId)
                .orElseGet(() -> new BusinessCalendar(tenantId, BusinessCalendar.MONDAY_TO_FRIDAY, clock.instant()));
    }

    private static HolidayResponse toResponse(Holiday holiday) {
        return new HolidayResponse(holiday.getDate(), holiday.getName(), holiday.getCreatedAt(), holiday.getCreatedBy());
    }
}
