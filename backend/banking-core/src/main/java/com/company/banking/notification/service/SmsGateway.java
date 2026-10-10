package com.company.banking.notification.service;

/**
 * Sends text messages. Providers (an SMS aggregator, a telco) are adapters chosen by
 * {@code banking.notification.sms.provider}; payment and messaging integrations arrive with Phase 7.
 *
 * <p>Messages may carry one-time codes: implementations must never log or store a message body.
 */
public interface SmsGateway {

    /**
     * @param phoneNumber E.164
     * @throws com.company.banking.common.error.BankingException {@code SMS_UNAVAILABLE} when nothing can send
     */
    void send(String phoneNumber, String message);
}
