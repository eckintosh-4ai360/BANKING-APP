package com.company.banking.channel.service;

import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.entity.CustomerDevice;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.channel.repository.CustomerDeviceRepository;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.iam.spi.CustomerDirectory;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tells the identity module whether a customer's session may go on: credential enabled, the session's device still
 * trusted, and the customer allowed to bank digitally.
 */
@Component
@RequiredArgsConstructor
class CustomerDirectoryAdapter implements CustomerDirectory {

    private final CustomerCredentialRepository credentials;
    private final CustomerDeviceRepository devices;
    private final CustomerService customerService;

    @Override
    public Optional<CustomerSignIn> findSignIn(UUID customerId, UUID deviceId) {
        UUID tenantId = TenantContext.requireTenantId();
        boolean credentialActive = credentials.findByTenantIdAndCustomerId(tenantId, customerId)
                .map(CustomerCredential::isActive).orElse(false);
        boolean deviceTrusted = devices.findByTenantIdAndId(tenantId, deviceId)
                .filter(device -> device.getCustomerId().equals(customerId))
                .map(CustomerDevice::isActive).orElse(false);
        if (!credentialActive || !deviceTrusted) {
            return Optional.empty();
        }
        return customerService.findForChannel(customerId)
                .filter(customer -> CustomerAuthService.SIGN_IN_STATUSES.contains(customer.status()))
                .map(customer -> new CustomerSignIn(customerId, customer.customerNumber()));
    }
}
