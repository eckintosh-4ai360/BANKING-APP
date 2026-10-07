package com.company.banking.iam.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.web.RequestMetadata;
import com.company.banking.iam.security.BankingSecurityProperties;
import com.company.banking.iam.security.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Throttles credential attempts per client IP and per account. Account keys are hashed so usernames don't appear
 * in the cache.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final RateLimiter rateLimiter;
    private final BankingSecurityProperties properties;

    public void check(String accountKey) {
        BankingSecurityProperties.RateLimit limits = properties.rateLimit();
        String ip = RequestMetadata.current().ipAddress();
        if (ip != null && !rateLimiter.tryAcquire("login-ip:" + ip,
                limits.loginPerIp().limit(), limits.loginPerIp().window())) {
            throw new BankingException(CommonErrorCode.RATE_LIMITED);
        }
        if (!rateLimiter.tryAcquire("login-account:" + sha256(accountKey),
                limits.loginPerAccount().limit(), limits.loginPerAccount().window())) {
            throw new BankingException(CommonErrorCode.RATE_LIMITED);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
