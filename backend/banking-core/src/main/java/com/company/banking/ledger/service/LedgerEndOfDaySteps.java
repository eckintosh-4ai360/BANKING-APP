package com.company.banking.ledger.service;

import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.eod.EndOfDayStep;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.ReconciliationReport;
import com.company.banking.ledger.repository.LedgerReportRepository;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The ledger's end-of-day steps, after every step that posts journals for the closed date:
 * <ul>
 *   <li>{@code GL_SNAPSHOT}: the closing position of every GL account, branch and currency for the date;</li>
 *   <li>{@code LEDGER_RECONCILIATION}: every balance equals its entries and every journal balances; a break fails
 *   the run, so a day with broken books is never reported as closed.</li>
 * </ul>
 */
@Configuration
class LedgerEndOfDaySteps {

    static final int SNAPSHOT_ORDER = 800;
    static final int RECONCILIATION_ORDER = 900;

    @Bean
    EndOfDayStep glSnapshotStep(LedgerReportRepository reports) {
        return new EndOfDayStep() {
            @Override
            public String code() {
                return "GL_SNAPSHOT";
            }

            @Override
            public int order() {
                return SNAPSHOT_ORDER;
            }

            @Override
            public Map<String, Object> run(EndOfDayContext context) {
                int written = context.inTransaction(() -> reports.snapshotGlBalances(TenantContext.requireTenantId(),
                        context.businessDate()));
                context.checkpoint(code(), "written");
                return Map.of("rowsWritten", written);
            }
        };
    }

    @Bean
    EndOfDayStep ledgerReconciliationStep(LedgerQueryService queries) {
        return new EndOfDayStep() {
            @Override
            public String code() {
                return "LEDGER_RECONCILIATION";
            }

            @Override
            public int order() {
                return RECONCILIATION_ORDER;
            }

            @Override
            public Map<String, Object> run(EndOfDayContext context) {
                ReconciliationReport report = context.inTransaction(queries::reconcile);
                if (!report.intact()) {
                    throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                            "Ledger reconciliation found " + report.balanceBreaks().size() + " balance breaks and "
                                    + report.unbalancedJournals().size() + " unbalanced journals.");
                }
                return Map.of("ledgerAccountsChecked", report.ledgerAccountsChecked(),
                        "journalsChecked", report.journalsChecked());
            }
        };
    }
}
