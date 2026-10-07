package com.company.banking.audit.service;

import com.company.banking.audit.dto.AuditLogResponse;
import com.company.banking.audit.dto.AuditLogSearchCriteria;
import com.company.banking.audit.repository.AuditLogRepository;
import com.company.banking.audit.repository.AuditLogRow;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Reads the audit trail of the current context: the caller's tenant, or platform events when no tenant is bound.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditLogSearchCriteria criteria, PageRequest page) {
        UUID tenantId = TenantContext.currentTenantId().orElse(null);
        long total = repository.count(tenantId, criteria);
        var items = repository.search(tenantId, criteria, (int) page.getOffset(), page.getPageSize()).stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(items, page.getPageNumber(), page.getPageSize(), total);
    }

    private AuditLogResponse toResponse(AuditLogRow row) {
        return new AuditLogResponse(row.id(), row.occurredAt(), row.actorType(), row.actorId(), row.actorName(),
                row.action(), row.outcome(), row.resourceType(), row.resourceId(), row.resourceReference(),
                row.branchId(), parse(row.beforeState()), parse(row.afterState()), parse(row.metadata()),
                row.ipAddress(), row.userAgent(), row.deviceId(), row.correlationId());
    }

    private JsonNode parse(String json) {
        return json == null ? null : jsonMapper.readTree(json);
    }
}
