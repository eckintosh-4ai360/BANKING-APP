package com.company.banking.channel.service;

import com.company.banking.channel.entity.CustomerDevice;
import com.company.banking.channel.entity.CustomerNotification;
import com.company.banking.channel.repository.CustomerDeviceRepository;
import com.company.banking.channel.repository.CustomerNotificationRepository;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.notification.service.PushGateway;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Puts notifications in a customer's in-app inbox and pushes them to their trusted devices once the transaction
 * commits. The inbox is the record: a push that cannot be sent is logged, never an error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerInbox {

    private final CustomerNotificationRepository notifications;
    private final CustomerDeviceRepository devices;
    private final PushGateway push;
    private final OtpService texts;
    private final Clock clock;

    /**
     * A message for the customer's inbox.
     *
     * @param sourceKey what it was made from; a second message from the same source is not added (null: always add)
     */
    public record Message(CustomerNotification.Category category, String title, String body, String referenceType,
                          UUID referenceId, UUID sourceKey) {
    }

    /**
     * @return false when the customer already has a notification from this source
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean post(UUID customerId, Message message) {
        UUID tenantId = TenantContext.requireTenantId();
        if (message.sourceKey() != null && notifications.existsByTenantIdAndCustomerIdAndSourceKey(tenantId,
                customerId, message.sourceKey())) {
            return false;
        }
        CustomerNotification notification = notifications.save(new CustomerNotification(UuidV7.next(), tenantId,
                customerId, message.category(), truncate(message.title(), 100), truncate(message.body(), 500),
                message.referenceType(), message.referenceId(), message.sourceKey(), clock.instant()));
        List<CustomerDevice> targets = devices.findAllByTenantIdAndCustomerIdOrderByBoundAtDesc(tenantId, customerId)
                .stream()
                .filter(device -> device.isActive() && device.getPushToken() != null)
                .toList();
        if (!targets.isEmpty()) {
            Map<String, String> data = Map.of("notificationId", notification.getId().toString(),
                    "category", notification.getCategory().name());
            afterCommit(() -> targets.forEach(device -> {
                try {
                    push.send(device.getPushProvider(), device.getPushToken(), notification.getTitle(),
                            notification.getBody(), data);
                } catch (RuntimeException ex) {
                    log.warn("A push notification could not be sent: {}", ex.getMessage());
                }
            }));
        }
        return true;
    }

    /**
     * A security notice (new device, password or PIN changed, sign-in locked): always texted, and kept in the inbox.
     *
     * @param text a sentence starting in lower case, after the institution's name in the text
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void securityNotice(UUID customerId, String phone, String institution, String title, String text) {
        post(customerId, new Message(CustomerNotification.Category.SECURITY, title,
                Character.toUpperCase(text.charAt(0)) + text.substring(1), null, null, null));
        texts.noticeAfterCommit(phone, institution + ": " + text);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
