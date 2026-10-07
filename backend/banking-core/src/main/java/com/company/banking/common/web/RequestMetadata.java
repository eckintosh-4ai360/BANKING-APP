package com.company.banking.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client information of the current HTTP request, for audit and session records. All values are optional and
 * truncated to their column sizes. The client IP is the servlet remote address; deployments behind a trusted
 * proxy must enable {@code server.forward-headers-strategy} so it reflects the real client.
 */
public record RequestMetadata(String ipAddress, String userAgent, String deviceId, String correlationId) {

    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private static final RequestMetadata EMPTY = new RequestMetadata(null, null, null, null);

    public static RequestMetadata current() {
        String correlationId = CorrelationIdFilter.currentCorrelationId();
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return correlationId == null ? EMPTY : new RequestMetadata(null, null, null, correlationId);
        }
        HttpServletRequest request = attributes.getRequest();
        return new RequestMetadata(
                truncate(request.getRemoteAddr(), 45),
                truncate(request.getHeader("User-Agent"), 400),
                truncate(request.getHeader(DEVICE_ID_HEADER), 100),
                correlationId);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
