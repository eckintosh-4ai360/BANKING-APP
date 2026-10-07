package com.company.banking.audit.service;

import com.company.banking.common.security.AccessDeniedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records refused requests. Failures to audit are logged and never mask the original 403.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventAuditListener {

    private final AuditService auditService;

    @EventListener
    public void onAccessDenied(AccessDeniedEvent event) {
        try {
            auditService.recordIndependently(AuditEvent.builder("ACCESS_DENIED", "HTTP_ENDPOINT")
                    .resourceId(event.method() + " " + event.path())
                    .outcome(AuditOutcome.DENIED)
                    .metadata("reason", event.reason())
                    .build());
        } catch (RuntimeException ex) {
            log.error("Could not record ACCESS_DENIED audit event", ex);
        }
    }
}
