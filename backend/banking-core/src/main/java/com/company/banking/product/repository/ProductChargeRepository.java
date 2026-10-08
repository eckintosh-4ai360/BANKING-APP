package com.company.banking.product.repository;

import com.company.banking.product.entity.ProductCharge;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductChargeRepository extends JpaRepository<ProductCharge, UUID> {

    List<ProductCharge> findAllByTenantIdAndProductVersionIdOrderByChargeEvent(UUID tenantId, UUID productVersionId);

    List<ProductCharge> findAllByTenantIdAndProductVersionIdIn(UUID tenantId, Collection<UUID> productVersionIds);
}
