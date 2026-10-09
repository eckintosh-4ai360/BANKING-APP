package com.company.banking.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The institution's working week, stored Monday to Sunday as seven 0/1 flags.
 */
@Getter
@Entity
@Table(name = "business_calendar")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessCalendar {

    public static final String MONDAY_TO_FRIDAY = "1111100";

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "working_week", nullable = false, length = 7)
    private String workingWeek;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public BusinessCalendar(UUID tenantId, String workingWeek, Instant now) {
        this.tenantId = tenantId;
        this.workingWeek = workingWeek;
        this.updatedAt = now;
    }

    public Set<DayOfWeek> workingDays() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (workingWeek.charAt(day.getValue() - 1) == '1') {
                days.add(day);
            }
        }
        return days;
    }

    public void changeWorkingDays(Set<DayOfWeek> days, Instant now, UUID by) {
        StringBuilder mask = new StringBuilder(7);
        for (DayOfWeek day : DayOfWeek.values()) {
            mask.append(days.contains(day) ? '1' : '0');
        }
        this.workingWeek = mask.toString();
        this.updatedAt = now;
        this.updatedBy = by;
    }
}
