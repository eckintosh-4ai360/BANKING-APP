package com.company.banking.channel.service;

import java.util.regex.Pattern;

/**
 * What a transaction PIN may be: 4 to 6 digits, not one digit repeated and not a run up or down (1234, 987654).
 * Pure.
 */
public final class PinPolicy {

    private static final Pattern DIGITS = Pattern.compile("^[0-9]{4,6}$");

    private PinPolicy() {
    }

    public static boolean isAcceptable(String pin) {
        if (pin == null || !DIGITS.matcher(pin).matches()) {
            return false;
        }
        boolean repeated = true;
        boolean ascending = true;
        boolean descending = true;
        for (int index = 1; index < pin.length(); index++) {
            int step = pin.charAt(index) - pin.charAt(index - 1);
            repeated &= step == 0;
            ascending &= step == 1;
            descending &= step == -1;
        }
        return !repeated && !ascending && !descending;
    }
}
