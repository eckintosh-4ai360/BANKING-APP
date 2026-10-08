package com.company.banking.approval.repository;

import com.company.banking.approval.entity.ApprovalPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicy, ApprovalPolicy.Key> {

    @Query("select p from ApprovalPolicy p where p.id.tenantId = :tenantId order by p.id.requestType, p.id.currency")
    List<ApprovalPolicy> findAllOfTenant(@Param("tenantId") UUID tenantId);
}
