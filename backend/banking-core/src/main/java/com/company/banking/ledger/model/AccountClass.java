package com.company.banking.ledger.model;

/**
 * The five classes of the accounting equation. Assets and expenses normally carry debit balances; liabilities,
 * equity and income carry credit balances.
 */
public enum AccountClass {
    ASSET(NormalSide.DEBIT),
    LIABILITY(NormalSide.CREDIT),
    EQUITY(NormalSide.CREDIT),
    INCOME(NormalSide.CREDIT),
    EXPENSE(NormalSide.DEBIT);

    private final NormalSide defaultSide;

    AccountClass(NormalSide defaultSide) {
        this.defaultSide = defaultSide;
    }

    public NormalSide defaultSide() {
        return defaultSide;
    }
}
