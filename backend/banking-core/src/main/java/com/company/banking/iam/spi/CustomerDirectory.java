package com.company.banking.iam.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * What the identity module needs to know about customers to keep their sessions going. Implemented by the customer
 * channel module.
 */
public interface CustomerDirectory {

    /**
     * The customer may keep a session on this device: their digital banking credential is enabled, the device is
     * still trusted and the customer may bank digitally. Runs in the tenant's context.
     */
    Optional<CustomerSignIn> findSignIn(UUID customerId, UUID deviceId);

    /**
     * @param username the customer's sign-in name (their phone number), carried in tokens for audit
     */
    record CustomerSignIn(UUID customerId, String username) {
    }
}
