package com.company.banking.iam.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.iam.exception.IamErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Hashing, verification and policy for passwords. Follows NIST SP 800-63B: length over composition rules, a
 * blocklist of common passwords, and no periodic forced rotation.
 */
@Service
public class PasswordService {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    private static final int MIN_DISTINCT_CHARACTERS = 6;
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;
    private static final char[] TEMPORARY_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final Set<String> BLOCKLIST = Set.of(
            "password", "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd",
            "123456789012", "1234567890123", "qwertyuiop", "qwertyuiop12", "qwerty123456", "letmein", "welcome",
            "welcome123", "welcome@123", "admin", "admin@123", "administrator", "changeme", "changeme123",
            "iloveyou", "monkey", "dragon", "football", "baseball", "sunshine", "princess", "abc123456789",
            "ghana123", "ghana@123", "accra123", "bank123", "banking123", "mobilemoney", "momo1234");

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return passwordEncoder.matches(rawPassword, hash);
    }

    /**
     * Spends the same time as a real verification so unknown usernames can't be detected by response timing.
     */
    public void burnDummyCheck(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyHash);
    }

    /**
     * @param currentHash hash of the password being replaced, or {@code null}
     */
    public void validateNewPassword(String newPassword, String username, String currentHash) {
        int length = newPassword == null ? 0 : newPassword.codePointCount(0, newPassword.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw violation("The password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters.");
        }
        String lower = newPassword.toLowerCase(Locale.ROOT);
        if (BLOCKLIST.contains(lower) || BLOCKLIST.stream().anyMatch(word -> word.length() >= 8 && lower.contains(word))) {
            throw violation("The password is too common.");
        }
        if (newPassword.codePoints().distinct().count() < MIN_DISTINCT_CHARACTERS) {
            throw violation("The password must contain at least " + MIN_DISTINCT_CHARACTERS
                    + " different characters.");
        }
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            throw violation("The password must not contain the username.");
        }
        if (currentHash != null && passwordEncoder.matches(newPassword, currentHash)) {
            throw violation("The new password must differ from the current password.");
        }
    }

    public String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            builder.append(TEMPORARY_ALPHABET[random.nextInt(TEMPORARY_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private static BankingException violation(String message) {
        return new BankingException(IamErrorCode.PASSWORD_POLICY_VIOLATION, message);
    }
}
