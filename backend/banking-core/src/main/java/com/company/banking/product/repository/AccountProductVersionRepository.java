package com.company.banking.product.repository;

import com.company.banking.product.entity.AccountProductVersion;
import com.company.banking.product.entity.ProductVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountProductVersionRepository extends JpaRepository<AccountProductVersion, UUID> {

    Optional<AccountProductVersion> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AccountProductVersion> findAllByTenantIdAndProductIdOrderByVersionNoDesc(UUID tenantId, UUID productId);

    List<AccountProductVersion> findAllByTenantIdAndProductIdAndStatus(UUID tenantId, UUID productId,
                                                                       ProductVersionStatus status);

    /**
     * Published or retired versions that pay interest (a rate above zero and a posting frequency).
     */
    @Query("""
            select v.id from AccountProductVersion v
            where v.tenantId = :tenantId and v.status <> :draft and v.interestRate > 0
              and v.interestPostingFrequency <> 'NONE'""")
    List<UUID> interestBearingIds(@Param("tenantId") UUID tenantId, @Param("draft") ProductVersionStatus draft);
}
