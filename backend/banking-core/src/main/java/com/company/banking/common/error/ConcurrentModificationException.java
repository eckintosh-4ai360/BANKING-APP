package com.company.banking.common.error;

import java.util.Objects;

public class ConcurrentModificationException extends BankingException {

    public ConcurrentModificationException() {
        super(CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    /**
     * Optimistic-lock check for update requests that carry the version the client last read.
     */
    public static void assertVersion(Long expected, Long actual) {
        if (!Objects.equals(expected, actual)) {
            throw new ConcurrentModificationException();
        }
    }
}
