package com.company.banking.platform.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code banking.platform.bootstrap.*}: creates the first platform owner on an empty installation. Supply the
 * password from a secret store; the owner must change it at first login.
 */
@ConfigurationProperties("banking.platform.bootstrap")
public record PlatformBootstrapProperties(String username, String email, String fullName, String password) {

    public boolean isConfigured() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
