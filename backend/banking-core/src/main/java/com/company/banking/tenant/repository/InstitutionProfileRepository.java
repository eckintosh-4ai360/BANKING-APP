package com.company.banking.tenant.repository;

import com.company.banking.tenant.entity.InstitutionProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InstitutionProfileRepository extends JpaRepository<InstitutionProfile, UUID> {
}
