package com.company.banking.platform.service;

import com.company.banking.common.security.CurrentActor;
import com.company.banking.iam.service.PlatformUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the first platform owner from configuration if the platform has no administrators yet.
 */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class PlatformBootstrap implements ApplicationRunner {

    private final PlatformBootstrapProperties properties;
    private final PlatformUserService platformUserService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        boolean created = CurrentActor.callAsSystem(null, () -> platformUserService.bootstrapOwnerIfAbsent(
                properties.username(), properties.email(), properties.fullName(), properties.password(), true));
        if (created) {
            log.info("Bootstrapped platform owner '{}' (password change required at first login)",
                    properties.username());
        }
    }
}
