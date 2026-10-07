package com.company.banking.common.error;

import com.company.banking.common.api.ApiErrorResponse;
import com.company.banking.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Writes the standard error envelope from servlet filters and security handlers, which run outside Spring MVC.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        write(response, errorCode, errorCode.defaultMessage());
    }

    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiErrorResponse body = ApiErrorResponse.of(
                errorCode.code(), message, CorrelationIdFilter.currentCorrelationId());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
