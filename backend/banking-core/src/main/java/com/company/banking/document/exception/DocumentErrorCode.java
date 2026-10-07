package com.company.banking.document.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DocumentErrorCode implements ErrorCode {

    DOCUMENT_EMPTY(HttpStatus.BAD_REQUEST, "The uploaded file is empty."),
    DOCUMENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The uploaded file is too large."),
    UNSUPPORTED_DOCUMENT_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG and PDF documents are accepted."),
    DOCUMENT_REJECTED_BY_SCANNER(HttpStatus.UNPROCESSABLE_CONTENT, "The document was rejected by the malware scanner."),
    DOCUMENT_INTEGRITY_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "The stored document failed its integrity check.");

    private final HttpStatus status;
    private final String defaultMessage;

    DocumentErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
