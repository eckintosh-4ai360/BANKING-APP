package com.company.banking.common.crypto;

/**
 * Display masking for identifiers (national ID, tax ID, account numbers).
 */
public final class Masking {

    private static final int VISIBLE = 4;

    private Masking() {
    }

    /**
     * Masks every letter and digit except the last four, keeping separators so the format stays recognisable:
     * {@code GHA-123456789-0} becomes {@code ***-******789-0}.
     */
    public static String maskIdentifier(String value) {
        if (value == null) {
            return null;
        }
        int alphanumerics = (int) value.chars().filter(Character::isLetterOrDigit).count();
        int toMask = Math.max(0, alphanumerics - VISIBLE);
        StringBuilder masked = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c) && toMask > 0) {
                masked.append('*');
                toMask--;
            } else {
                masked.append(c);
            }
        }
        return masked.toString();
    }
}
