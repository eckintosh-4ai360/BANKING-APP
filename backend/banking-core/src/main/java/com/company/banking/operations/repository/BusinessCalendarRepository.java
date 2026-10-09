package com.company.banking.operations.repository;

import com.company.banking.operations.entity.BusinessCalendar;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessCalendarRepository extends JpaRepository<BusinessCalendar, UUID> {
}
