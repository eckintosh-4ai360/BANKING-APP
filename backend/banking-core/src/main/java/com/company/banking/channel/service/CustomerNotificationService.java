package com.company.banking.channel.service;

import com.company.banking.channel.dto.NotificationDtos;
import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.entity.CustomerDevice;
import com.company.banking.channel.entity.CustomerNotification;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.channel.repository.CustomerDeviceRepository;
import com.company.banking.channel.repository.CustomerNotificationRepository;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.AuthenticatedActor;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in customer's inbox, push registration and alert preferences.
 */
@Service
@RequiredArgsConstructor
public class CustomerNotificationService {

    private final CustomerNotificationRepository notifications;
    private final CustomerDeviceRepository devices;
    private final CustomerCredentialRepository credentials;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<NotificationDtos.Notification> inbox(PageRequest page) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        return PageResponse.from(notifications.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(actor.tenantId(),
                actor.id(), page), CustomerNotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationDtos.Unread unread() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        return new NotificationDtos.Unread(notifications.countByTenantIdAndCustomerIdAndReadAtIsNull(
                actor.tenantId(), actor.id()));
    }

    @Transactional
    public NotificationDtos.Notification markRead(UUID notificationId) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        CustomerNotification notification = notifications.findByTenantIdAndId(actor.tenantId(), notificationId)
                .filter(found -> found.getCustomerId().equals(actor.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification"));
        notification.markRead(clock.instant());
        notifications.save(notification);
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        return notifications.markAllRead(actor.tenantId(), actor.id(), clock.instant());
    }

    /**
     * Pushes for this installation go to the given token from now on.
     */
    @Transactional
    public void registerPush(NotificationDtos.PushRegistration request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        CustomerDevice device = currentDevice(actor);
        device.registerPush(request.provider(), request.token().trim(), clock.instant());
        devices.save(device);
    }

    @Transactional
    public void unregisterPush() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        CustomerDevice device = currentDevice(actor);
        device.registerPush(null, null, clock.instant());
        devices.save(device);
    }

    @Transactional(readOnly = true)
    public NotificationDtos.Preferences preferences() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        return new NotificationDtos.Preferences(credential(actor).isSmsAlerts());
    }

    @Transactional
    public NotificationDtos.Preferences updatePreferences(NotificationDtos.Preferences request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        CustomerCredential credential = credentials.lockByCustomerId(actor.tenantId(), actor.id())
                .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
        credential.chooseSmsAlerts(request.smsAlerts(), clock.instant());
        credentials.save(credential);
        return new NotificationDtos.Preferences(credential.isSmsAlerts());
    }

    private CustomerDevice currentDevice(AuthenticatedActor actor) {
        return devices.findTrusted(actor.tenantId(), actor.id(), CustomerAuthService.requireDeviceKey())
                .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
    }

    private CustomerCredential credential(AuthenticatedActor actor) {
        return credentials.findByTenantIdAndCustomerId(actor.tenantId(), actor.id())
                .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
    }

    private static NotificationDtos.Notification toResponse(CustomerNotification notification) {
        return new NotificationDtos.Notification(notification.getId(), notification.getCategory().name(),
                notification.getTitle(), notification.getBody(), notification.getReferenceType(),
                notification.getReferenceId(), notification.getCreatedAt(), notification.getReadAt() != null);
    }
}
