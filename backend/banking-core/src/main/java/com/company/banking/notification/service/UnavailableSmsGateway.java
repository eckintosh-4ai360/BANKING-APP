package com.company.banking.notification.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.notification.exception.NotificationErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No SMS provider is configured ({@code provider: none}): anything that needs a text message fails openly instead of
 * pretending to have sent it.
 */
@Component
@ConditionalOnProperty(prefix = "banking.notification.sms", name = "provider", havingValue = "none",
        matchIfMissing = true)
public class UnavailableSmsGateway implements SmsGateway {

    @Override
    public void send(String phoneNumber, String message) {
        throw new BankingException(NotificationErrorCode.SMS_UNAVAILABLE);
    }
}
