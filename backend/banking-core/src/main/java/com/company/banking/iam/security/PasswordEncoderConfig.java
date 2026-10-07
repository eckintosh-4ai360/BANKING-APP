package com.company.banking.iam.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    private static final String ARGON2 = "argon2";
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    /**
     * Argon2id with the configured cost (OWASP minimum by default). Hashes are prefixed with {@code {argon2}} and
     * embed their parameters, so the cost can be raised later without invalidating stored hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder(BankingSecurityProperties properties) {
        BankingSecurityProperties.PasswordHashing cost = properties.passwordHashing();
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, cost.parallelism(),
                cost.memoryKib(), cost.iterations());
        return new DelegatingPasswordEncoder(ARGON2, Map.of(ARGON2, argon2));
    }
}
