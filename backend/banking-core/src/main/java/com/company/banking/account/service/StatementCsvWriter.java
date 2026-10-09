package com.company.banking.account.service;

import com.company.banking.account.dto.AccountStatement;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * CSV rendering of a statement (RFC 4180, UTF-8 with a byte order mark so spreadsheet programs read accents
 * correctly). Text cells that a spreadsheet would treat as a formula are prefixed with an apostrophe.
 */
final class StatementCsvWriter {

    static final String BYTE_ORDER_MARK = String.valueOf((char) 0xFEFF);
    private static final String LINE_END = "\r\n";

    private StatementCsvWriter() {
    }

    static byte[] write(AccountStatement statement) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK);
        row(csv, "Account", statement.accountNumber(), statement.accountTitle());
        row(csv, "Currency", statement.currency());
        row(csv, "Period", statement.from().toString(), statement.to().toString());
        csv.append(LINE_END);
        row(csv, "Date", "Value date", "Reference", "Description", "Debit", "Credit", "Balance");
        row(csv, statement.from().toString(), "", "", "Opening balance", "", "", amount(statement.openingBalance()));
        for (AccountStatement.Line line : statement.lines()) {
            row(csv, line.date().toString(), line.valueDate().toString(), line.reference(), line.description(),
                    amount(line.debit()), amount(line.credit()), amount(line.balance()));
        }
        row(csv, statement.to().toString(), "", "", "Closing balance", amount(statement.totalDebits()),
                amount(statement.totalCredits()), amount(statement.closingBalance()));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(cell(cells[i]));
        }
        csv.append(LINE_END);
    }

    /**
     * Quotes every non-empty cell. Text starting with a formula character gets an apostrophe in front; plain
     * decimals (including negative balances) are left as they are.
     */
    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = value;
        if (!isAmount(value) && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            safe = "'" + value;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static boolean isAmount(String value) {
        return value.matches("^-?[0-9]+(\\.[0-9]+)?$");
    }

    private static String amount(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
