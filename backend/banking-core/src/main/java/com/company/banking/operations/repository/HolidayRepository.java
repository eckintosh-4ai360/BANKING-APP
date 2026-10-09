package com.company.banking.operations.repository;

import com.company.banking.operations.entity.Holiday;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, Holiday.Key> {

    @Query("""
            select h from Holiday h
            where h.id.tenantId = :tenantId and h.id.holidayDate between :from and :to
            order by h.id.holidayDate""")
    List<Holiday> findBetween(@Param("tenantId") UUID tenantId, @Param("from") LocalDate from,
                              @Param("to") LocalDate to);
}
