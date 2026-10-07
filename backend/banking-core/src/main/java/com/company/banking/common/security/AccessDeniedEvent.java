package com.company.banking.common.security;

/**
 * Published whenever an authenticated caller is refused, so the audit module can record it without the web layer
 * depending on audit.
 */
public record AccessDeniedEvent(String method, String path, String reason) {
}
