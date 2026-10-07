package com.company.banking.common.error;

import com.company.banking.common.api.ApiErrorResponse;
import com.company.banking.common.api.ApiErrorResponse.FieldViolation;
import com.company.banking.common.security.AccessDeniedEvent;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;

/**
 * Maps every exception to the standard error envelope. Unexpected exceptions are logged with the correlation id
 * and returned as {@code INTERNAL_ERROR} with no internal detail.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String UNIQUE_VIOLATION = "23505";

    private final ApplicationEventPublisher eventPublisher;

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ApiErrorResponse> handleBanking(BankingException ex) {
        if (ex.getErrorCode().status().is5xxServerError()) {
            log.error("Business failure {}", ex.getErrorCode().code(), ex);
        }
        return respond(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(CommonErrorCode.VALIDATION_FAILED, CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                violations);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidParameters(HandlerMethodValidationException ex) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(CommonErrorCode.VALIDATION_FAILED, CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(CommonErrorCode.VALIDATION_FAILED, CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformed(Exception ex) {
        log.debug("Malformed request: {}", ex.getMessage());
        return respond(CommonErrorCode.MALFORMED_REQUEST, CommonErrorCode.MALFORMED_REQUEST.defaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(CommonErrorCode.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return respond(CommonErrorCode.RESOURCE_NOT_FOUND, CommonErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode code = CurrentActor.current()
                .map(actor -> actor.restrictionCode())
                .map(restriction -> (ErrorCode) CommonErrorCode.valueOf(restriction))
                .orElse(CommonErrorCode.ACCESS_DENIED);
        eventPublisher.publishEvent(new AccessDeniedEvent(request.getMethod(), request.getRequestURI(), code.code()));
        return respond(code, code.defaultMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return respond(CommonErrorCode.CONCURRENT_MODIFICATION,
                CommonErrorCode.CONCURRENT_MODIFICATION.defaultMessage());
    }

    /**
     * Logs only the constraint name: database messages quote the offending values, which can be personal data.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        String constraint = constraintName(ex);
        if (UNIQUE_VIOLATION.equals(sqlState(ex))) {
            log.info("Unique constraint violation on {}", constraint);
            return respond(CommonErrorCode.DUPLICATE_RESOURCE, CommonErrorCode.DUPLICATE_RESOURCE.defaultMessage());
        }
        log.warn("Data integrity violation on {} (SQL state {})", constraint, sqlState(ex));
        return respond(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                CommonErrorCode.BUSINESS_RULE_VIOLATION.defaultMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return respond(CommonErrorCode.PAYLOAD_TOO_LARGE, CommonErrorCode.PAYLOAD_TOO_LARGE.defaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private static ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String message) {
        return respond(code, message, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> respond(ErrorCode code, String message,
                                                            List<FieldViolation> violations) {
        ApiErrorResponse body = ApiErrorResponse.of(
                code.code(), message, CorrelationIdFilter.currentCorrelationId(), violations);
        return ResponseEntity.status(code.status()).body(body);
    }

    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        return "unknown constraint";
    }

    private static String sqlState(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
