package com.company.banking.account.service;

import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.eod.EndOfDayStep;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The account module's end-of-day steps, before the ledger snapshots the day: interest accrual (100), interest
 * payout at period ends (200) and dormancy (300).
 */
@Configuration
class AccountEndOfDaySteps {

    @Bean
    EndOfDayStep depositInterestAccrualStep(DepositInterestService interest) {
        return step(DepositInterestService.ACCRUAL_STEP, 100, interest::accrue);
    }

    @Bean
    EndOfDayStep depositInterestPayoutStep(DepositInterestService interest) {
        return step(DepositInterestService.PAYOUT_STEP, 200, interest::payOut);
    }

    @Bean
    EndOfDayStep dormancyStep(DormancyService dormancy) {
        return step(DormancyService.STEP, 300, dormancy::run);
    }

    private static EndOfDayStep step(String code, int order, Function<EndOfDayContext, Map<String, Object>> work) {
        return new EndOfDayStep() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public Map<String, Object> run(EndOfDayContext context) {
                return work.apply(context);
            }
        };
    }
}
