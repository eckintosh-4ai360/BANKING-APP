package com.company.banking.common.text;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Phone numbers in international form ({@code +<country code><number>}, E.164), so that "024 123 4567" typed in Ghana
 * and "+233241234567" on file are the same number. A number already written with {@code +} or {@code 00} keeps its
 * country code; a national number (leading 0) takes the institution's country code. Countries without a known calling
 * code only accept international numbers. No other country rules are assumed.
 */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{6,14}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");
    private static final Map<String, String> CALLING_CODES = Map.ofEntries(
            Map.entry("GH", "233"), Map.entry("NG", "234"), Map.entry("KE", "254"), Map.entry("UG", "256"),
            Map.entry("TZ", "255"), Map.entry("RW", "250"), Map.entry("ZA", "27"), Map.entry("ZM", "260"),
            Map.entry("SL", "232"), Map.entry("LR", "231"), Map.entry("GM", "220"), Map.entry("CI", "225"),
            Map.entry("SN", "221"), Map.entry("CM", "237"), Map.entry("MW", "265"), Map.entry("ET", "251"));

    private PhoneNumbers() {
    }

    /**
     * @param countryCode ISO 3166-1 alpha-2 code of the institution's country, used for national numbers
     * @return the E.164 form, or empty when the input is not a phone number
     */
    public static Optional<String> normalize(String raw, String countryCode) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = SEPARATORS.matcher(raw.trim()).replaceAll("");
        String international;
        if (compact.startsWith("+")) {
            international = compact;
        } else if (compact.startsWith("00")) {
            international = "+" + compact.substring(2);
        } else if (compact.startsWith("0") && countryCode != null && CALLING_CODES.containsKey(countryCode)) {
            international = "+" + CALLING_CODES.get(countryCode) + compact.substring(1);
        } else {
            return Optional.empty();
        }
        return E164.matcher(international).matches() ? Optional.of(international) : Optional.empty();
    }

    /**
     * The number with all but its last 3 digits hidden, e.g. {@code +233*******567}, for messages that confirm where
     * a code was sent without revealing the number.
     */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return "***";
        }
        int keep = 3;
        int prefix = Math.min(4, e164.length() - keep);
        return e164.substring(0, prefix) + "*".repeat(e164.length() - prefix - keep)
                + e164.substring(e164.length() - keep);
    }
}
