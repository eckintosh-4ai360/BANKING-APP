package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanGuarantor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanGuarantorRepository extends JpaRepository<LoanGuarantor, UUID> {

    Optional<LoanGuarantor> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LoanGuarantor> findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(UUID tenantId, UUID applicationId);
}
