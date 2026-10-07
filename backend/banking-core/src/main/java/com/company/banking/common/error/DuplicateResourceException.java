package com.company.banking.common.error;

public class DuplicateResourceException extends BankingException {

    public DuplicateResourceException(String message) {
        super(CommonErrorCode.DUPLICATE_RESOURCE, message);
    }
}
