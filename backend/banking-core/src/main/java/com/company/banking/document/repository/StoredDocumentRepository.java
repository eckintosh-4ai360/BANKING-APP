package com.company.banking.document.repository;

import com.company.banking.document.entity.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    Optional<StoredDocument> findByTenantIdAndId(UUID tenantId, UUID id);
}
