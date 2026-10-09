package com.company.banking.ledger.service;

import com.company.banking.ledger.model.AccountClass;
import com.company.banking.ledger.model.NormalSide;
import com.company.banking.ledger.model.SystemAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The chart of accounts every institution starts with, parents before children. Institutions extend and rename it
 * (codes and names are theirs); the system codes stay, because automatic postings find their accounts by them.
 *
 * <p>The base chart fits a deposit-taking microfinance institution. Institution types add what their regulator or
 * structure expects: member shares for credit unions and cooperatives, a statutory reserve for regulated banks.
 */
public final class DefaultChartOfAccounts {

    /**
     * One template account. {@code normalSide} null means the class's usual side.
     */
    public record Entry(String code, String name, AccountClass accountClass, NormalSide normalSide, String parentCode,
                        boolean header, boolean manualPostingAllowed, SystemAccount systemAccount) {

        NormalSide side() {
            return normalSide == null ? accountClass.defaultSide() : normalSide;
        }
    }

    private static final Set<String> MEMBER_OWNED = Set.of("CREDIT_UNION", "COOPERATIVE");
    private static final Set<String> STATUTORY_RESERVE = Set.of("RURAL_COMMUNITY_BANK", "SAVINGS_AND_LOANS");

    private DefaultChartOfAccounts() {
    }

    public static List<Entry> forInstitutionType(String institutionType) {
        List<Entry> chart = new ArrayList<>();
        // Assets
        header(chart, "1000", "Assets", AccountClass.ASSET, null);
        header(chart, "1100", "Cash and cash equivalents", AccountClass.ASSET, "1000");
        posting(chart, "1110", "Cash at branches", AccountClass.ASSET, "1100", false, SystemAccount.CASH_AT_BRANCH);
        posting(chart, "1120", "Vault cash", AccountClass.ASSET, "1100", false, SystemAccount.VAULT_CASH);
        posting(chart, "1130", "Cash with collectors", AccountClass.ASSET, "1100", false, SystemAccount.COLLECTOR_CASH);
        posting(chart, "1140", "Balances with banks", AccountClass.ASSET, "1100", true, SystemAccount.BANK_BALANCES);
        posting(chart, "1150", "Mobile money settlement", AccountClass.ASSET, "1100", false,
                SystemAccount.MOBILE_MONEY_SETTLEMENT);
        posting(chart, "1160", "Cash in transit", AccountClass.ASSET, "1100", false, SystemAccount.CASH_IN_TRANSIT);
        header(chart, "1200", "Loans and advances", AccountClass.ASSET, "1000");
        posting(chart, "1210", "Loan principal", AccountClass.ASSET, "1200", false, SystemAccount.LOAN_PRINCIPAL);
        posting(chart, "1220", "Interest receivable", AccountClass.ASSET, "1200", false,
                SystemAccount.INTEREST_RECEIVABLE);
        chart.add(new Entry("1290", "Allowance for loan losses", AccountClass.ASSET, NormalSide.CREDIT, "1200", false,
                false, SystemAccount.LOAN_LOSS_PROVISION));
        header(chart, "1900", "Clearing and suspense", AccountClass.ASSET, "1000");
        posting(chart, "1910", "Inter-branch due from", AccountClass.ASSET, "1900", false,
                SystemAccount.INTER_BRANCH_DUE_FROM);
        posting(chart, "1920", "Teller shortages", AccountClass.ASSET, "1900", false, SystemAccount.TELLER_SHORTAGES);
        posting(chart, "1990", "Suspense (debit)", AccountClass.ASSET, "1900", true, SystemAccount.SUSPENSE_DEBIT);

        // Liabilities
        header(chart, "2000", "Liabilities", AccountClass.LIABILITY, null);
        header(chart, "2100", "Customer deposits", AccountClass.LIABILITY, "2000");
        posting(chart, "2110", "Savings deposits", AccountClass.LIABILITY, "2100", false, SystemAccount.SAVINGS_DEPOSITS);
        posting(chart, "2120", "Current deposits", AccountClass.LIABILITY, "2100", false, SystemAccount.CURRENT_DEPOSITS);
        posting(chart, "2130", "Susu deposits", AccountClass.LIABILITY, "2100", false, SystemAccount.SUSU_DEPOSITS);
        posting(chart, "2140", "Fixed deposits", AccountClass.LIABILITY, "2100", false, SystemAccount.FIXED_DEPOSITS);
        posting(chart, "2150", "Target savings deposits", AccountClass.LIABILITY, "2100", false,
                SystemAccount.TARGET_SAVINGS_DEPOSITS);
        header(chart, "2200", "Other liabilities", AccountClass.LIABILITY, "2000");
        posting(chart, "2210", "Interest payable", AccountClass.LIABILITY, "2200", false, SystemAccount.INTEREST_PAYABLE);
        header(chart, "2900", "Clearing and suspense", AccountClass.LIABILITY, "2000");
        posting(chart, "2910", "Inter-branch due to", AccountClass.LIABILITY, "2900", false,
                SystemAccount.INTER_BRANCH_DUE_TO);
        posting(chart, "2990", "Suspense (credit)", AccountClass.LIABILITY, "2900", true, SystemAccount.SUSPENSE_CREDIT);

        // Equity
        header(chart, "3000", "Equity", AccountClass.EQUITY, null);
        posting(chart, "3100", "Share capital", AccountClass.EQUITY, "3000", true, SystemAccount.SHARE_CAPITAL);
        if (MEMBER_OWNED.contains(institutionType)) {
            posting(chart, "3110", "Member shares", AccountClass.EQUITY, "3000", false, SystemAccount.MEMBER_SHARES);
        }
        if (STATUTORY_RESERVE.contains(institutionType)) {
            posting(chart, "3150", "Statutory reserve", AccountClass.EQUITY, "3000", true,
                    SystemAccount.STATUTORY_RESERVE);
        }
        posting(chart, "3200", "Retained earnings", AccountClass.EQUITY, "3000", true, SystemAccount.RETAINED_EARNINGS);
        posting(chart, "3300", "Current year earnings", AccountClass.EQUITY, "3000", false,
                SystemAccount.CURRENT_YEAR_EARNINGS);

        // Income
        header(chart, "4000", "Income", AccountClass.INCOME, null);
        posting(chart, "4100", "Interest income on loans", AccountClass.INCOME, "4000", false,
                SystemAccount.LOAN_INTEREST_INCOME);
        header(chart, "4200", "Fee and commission income", AccountClass.INCOME, "4000");
        posting(chart, "4210", "Account maintenance fees", AccountClass.INCOME, "4200", false,
                SystemAccount.ACCOUNT_FEE_INCOME);
        posting(chart, "4220", "Transaction fees", AccountClass.INCOME, "4200", false,
                SystemAccount.TRANSACTION_FEE_INCOME);
        posting(chart, "4900", "Other income", AccountClass.INCOME, "4000", true, SystemAccount.OTHER_INCOME);

        // Expenses
        header(chart, "5000", "Expenses", AccountClass.EXPENSE, null);
        posting(chart, "5100", "Interest expense on deposits", AccountClass.EXPENSE, "5000", false,
                SystemAccount.DEPOSIT_INTEREST_EXPENSE);
        posting(chart, "5200", "Loan loss provision expense", AccountClass.EXPENSE, "5000", false,
                SystemAccount.PROVISION_EXPENSE);
        posting(chart, "5900", "Other operating expenses", AccountClass.EXPENSE, "5000", true,
                SystemAccount.OPERATING_EXPENSE);
        return List.copyOf(chart);
    }

    private static void header(List<Entry> chart, String code, String name, AccountClass accountClass,
                               String parentCode) {
        chart.add(new Entry(code, name, accountClass, null, parentCode, true, false, null));
    }

    private static void posting(List<Entry> chart, String code, String name, AccountClass accountClass,
                                String parentCode, boolean manualPostingAllowed, SystemAccount systemAccount) {
        chart.add(new Entry(code, name, accountClass, null, parentCode, false, manualPostingAllowed, systemAccount));
    }
}
