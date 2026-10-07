package com.company.banking.tenant.repository;

import com.company.banking.tenant.entity.TenantFeature;
import com.company.banking.tenant.entity.TenantFeatureId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantFeatureRepository extends JpaRepository<TenantFeature, TenantFeatureId> {

    List<TenantFeature> findByIdTenantId(UUID tenantId);
}
