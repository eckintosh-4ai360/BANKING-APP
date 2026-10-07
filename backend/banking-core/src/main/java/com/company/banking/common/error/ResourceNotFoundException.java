package com.company.banking.common.error;

/**
 * Also used for resources that exist in another tenant or outside the caller's branch scope, so that
 * the API never reveals their existence.
 */
public class ResourceNotFoundException extends BankingException {

    public ResourceNotFoundException(String resourceName) {
        super(CommonErrorCode.RESOURCE_NOT_FOUND, resourceName + " not found.");
    }
}
