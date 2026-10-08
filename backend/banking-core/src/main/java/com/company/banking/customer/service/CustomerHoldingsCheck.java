package com.company.banking.customer.service;

import java.util.UUID;

/**
 * Implemented by modules that hold products for customers (accounts, later loans), so a customer with open holdings
 * cannot be closed without the customer module depending on those modules. Called with the customer row locked.
 */
public interface CustomerHoldingsCheck {

    boolean hasOpenHoldings(UUID customerId);
}
