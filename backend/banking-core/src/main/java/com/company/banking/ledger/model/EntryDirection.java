package com.company.banking.ledger.model;

public enum EntryDirection {
    DEBIT("D"),
    CREDIT("C");

    private final String code;

    EntryDirection(String code) {
        this.code = code;
    }

    /**
     * Single-letter form stored in {@code ledger_entry.direction}.
     */
    public String code() {
        return code;
    }

    public EntryDirection opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }

    public static EntryDirection fromCode(String code) {
        return switch (code) {
            case "D" -> DEBIT;
            case "C" -> CREDIT;
            default -> throw new IllegalArgumentException("Unknown entry direction " + code);
        };
    }
}
