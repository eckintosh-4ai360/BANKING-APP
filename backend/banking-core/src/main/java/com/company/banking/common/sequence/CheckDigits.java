package com.company.banking.common.sequence;

/**
 * Check digits for human-entered reference numbers (customer and account numbers), so typos are caught before a
 * lookup.
 */
public final class CheckDigits {

    private CheckDigits() {
    }

    /**
     * Luhn (mod 10) check digit for a string of digits.
     */
    public static int luhn(String digits) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                throw new IllegalArgumentException("Only digits are allowed");
            }
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static String appendLuhn(String digits) {
        return digits + luhn(digits);
    }

    public static boolean isValidLuhn(String number) {
        if (number == null || number.length() < 2 || !number.chars().allMatch(Character::isDigit)) {
            return false;
        }
        String body = number.substring(0, number.length() - 1);
        return luhn(body) == number.charAt(number.length() - 1) - '0';
    }
}
