package com.company.banking.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request bodies for deposit products, shared by the product and account tests.
 */
public final class ProductRequests {

    private ProductRequests() {
    }

    public static Map<String, Object> product(String code, String type, Map<String, Object> terms) {
        return Map.of("code", code, "name", code + " account", "productType", type, "terms", terms);
    }

    /**
     * Mutable GHS terms with a 5.25% rate; tests add or change fields as needed.
     */
    public static Map<String, Object> terms(String minOpeningBalance) {
        Map<String, Object> terms = new LinkedHashMap<>();
        terms.put("currency", "GHS");
        terms.put("minOpeningBalance", minOpeningBalance);
        terms.put("interestRate", "5.25");
        terms.put("allowOverdraft", false);
        return terms;
    }
}
