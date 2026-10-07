package com.company.banking.iam.repository;

import com.company.banking.iam.entity.Permission;
import com.company.banking.iam.entity.PermissionScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    List<Permission> findByScopeOrderByCode(PermissionScope scope);

    List<Permission> findByCodeIn(Collection<String> codes);
}
