package com.company.banking.tenant.repository;

import com.company.banking.tenant.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureRepository extends JpaRepository<Feature, String> {

    List<Feature> findAllByOrderByCodeAsc();
}
