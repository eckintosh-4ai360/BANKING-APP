package com.company.banking.iam.security;

import com.company.banking.common.error.ApiErrorWriter;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.security.AccessDeniedEvent;
import com.company.banking.common.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * JSON responses for authentication and authorization failures raised by the security filter chain.
 */
@Component
@RequiredArgsConstructor
public class RestSecurityHandlers {

    private final ApiErrorWriter errorWriter;
    private final ApplicationEventPublisher eventPublisher;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> errorWriter.write(response, CommonErrorCode.UNAUTHENTICATED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            CommonErrorCode code = CurrentActor.current()
                    .map(actor -> actor.restrictionCode())
                    .map(CommonErrorCode::valueOf)
                    .orElse(CommonErrorCode.ACCESS_DENIED);
            eventPublisher.publishEvent(new AccessDeniedEvent(request.getMethod(), request.getRequestURI(),
                    code.code()));
            errorWriter.write(response, code);
        };
    }
}
