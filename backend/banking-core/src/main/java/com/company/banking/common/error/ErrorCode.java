package com.company.banking.common.error;

import org.springframework.http.HttpStatus;

/**
 * A stable, machine-readable business error. Modules define their own enums implementing this interface.
 */
public interface ErrorCode {

    String code();

    HttpStatus status();

    String defaultMessage();
}
