package com.company.banking.susu.repository;

import com.company.banking.susu.entity.SusuFrequency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SusuFrequencyRepository extends JpaRepository<SusuFrequency, SusuFrequency.Key> {

    List<SusuFrequency> findAllByIdTenantIdOrderByIdCode(UUID tenantId);
}
