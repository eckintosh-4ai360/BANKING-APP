package com.company.banking.product.repository;

import com.company.banking.product.entity.AccountProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountProductRepository extends JpaRepository<AccountProduct, UUID> {

    Optional<AccountProduct> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<AccountProduct> findAllByTenantIdOrderByCode(UUID tenantId);
}
