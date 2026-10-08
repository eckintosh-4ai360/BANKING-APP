package com.company.banking.ledger.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.ledger.dto.CurrencyResponse;
import com.company.banking.ledger.exception.LedgerErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Currencies and their minor units. Amounts are validated against the currency before they reach the ledger;
 * nothing in the ledger rounds.
 */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    /** NUMERIC(19,4): at most 15 digits before the decimal point. */
    private static final int MAX_INTEGER_DIGITS = 15;

    private final JdbcClient jdbcClient;
    private volatile Map<String, CurrencyResponse> currencies;

    public List<CurrencyResponse> list() {
        return all().values().stream().sorted(java.util.Comparator.comparing(CurrencyResponse::code)).toList();
    }

    public CurrencyResponse require(String code) {
        CurrencyResponse currency = code == null ? null : all().get(code);
        if (currency == null) {
            throw new BankingException(LedgerErrorCode.CURRENCY_NOT_SUPPORTED);
        }
        return currency;
    }

    /**
     * Rejects amounts that are not positive, too large, or more precise than the currency allows.
     */
    public void requireValidAmount(BigDecimal amount, String currencyCode) {
        CurrencyResponse currency = require(currencyCode);
        if (amount == null || amount.signum() <= 0) {
            throw new BankingException(LedgerErrorCode.INVALID_AMOUNT, "Amounts must be greater than zero.");
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        if (Math.max(normalized.scale(), 0) > currency.minorUnits()) {
            throw new BankingException(LedgerErrorCode.INVALID_AMOUNT,
                    currency.code() + " amounts can have at most " + currency.minorUnits() + " decimal places.");
        }
        if (normalized.precision() - normalized.scale() > MAX_INTEGER_DIGITS) {
            throw new BankingException(LedgerErrorCode.INVALID_AMOUNT, "The amount is too large.");
        }
    }

    /**
     * Presents an exact ledger amount with the currency's number of decimals (never rounds: ledger amounts are
     * always whole minor units).
     */
    public BigDecimal present(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(require(currencyCode).minorUnits(), RoundingMode.UNNECESSARY);
    }

    private Map<String, CurrencyResponse> all() {
        Map<String, CurrencyResponse> loaded = currencies;
        if (loaded == null) {
            loaded = jdbcClient.sql("SELECT code, name, minor_units FROM core.currency WHERE active")
                    .query((rs, rowNum) -> new CurrencyResponse(rs.getString("code"), rs.getString("name"),
                            rs.getInt("minor_units")))
                    .list()
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(CurrencyResponse::code, Function.identity()));
            currencies = loaded;
        }
        return loaded;
    }
}
