package com.company.banking.common.validation;

/**
 * Shared, country-neutral input formats. Country-specific rules (ID formats, phone plans) belong in tenant
 * configuration, not here.
 */
public final class ValidationPatterns {

    /** E.164-style phone number: optional '+', 7 to 15 digits. */
    public static final String PHONE = "^\\+?[0-9]{7,15}$";
    /** Hex color, e.g. #0B3B60. */
    public static final String HEX_COLOR = "^#[0-9A-Fa-f]{6}$";
    /** Digital/postal address codes such as GhanaPost GPS (GA-123-4567). */
    public static final String DIGITAL_ADDRESS = "^[A-Za-z0-9-]{4,20}$";
    /** Login usernames: lower-case letters, digits and . _ @ - */
    public static final String USERNAME = "^[a-z0-9][a-z0-9._@-]{2,99}$";
    /** Upper-case business codes for branches. */
    public static final String BRANCH_CODE = "^[A-Z0-9][A-Z0-9-]{0,19}$";
    /** Upper-case snake codes for roles. */
    public static final String ROLE_CODE = "^[A-Z][A-Z0-9_]{1,49}$";
    /** Tenant slug used at login and in URLs. */
    public static final String TENANT_CODE = "^[a-z0-9][a-z0-9-]{1,31}$";
    /** ISO 3166-1 alpha-2. */
    public static final String COUNTRY_CODE = "^[A-Z]{2}$";
    /** ISO 4217. */
    public static final String CURRENCY_CODE = "^[A-Z]{3}$";
    /** Alphanumeric SMS sender id (max 11 characters). */
    public static final String SMS_SENDER_ID = "^[A-Za-z0-9][A-Za-z0-9 ]{0,10}$";

    private ValidationPatterns() {
    }
}
