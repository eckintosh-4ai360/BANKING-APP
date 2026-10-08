package com.company.banking.product.repository;

import com.company.banking.product.entity.AccountProductVersion;
import com.company.banking.product.entity.ProductVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountProductVersionRepository extends JpaRepository<AccountProductVersion, UUID> {

    Optional<AccountProductVersion> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AccountProductVersion> findAllByTenantIdAndProductIdOrderByVersionNoDesc(UUID tenantId, UUID productId);

    List<AccountProductVersion> findAllByTenantIdAndProductIdAndStatus(UUID tenantId, UUID productId,
                                                                       ProductVersionStatus status);
}
