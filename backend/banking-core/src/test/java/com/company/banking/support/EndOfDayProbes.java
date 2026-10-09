package com.company.banking.support;

import com.company.banking.common.eod.EndOfDayCheck;
import com.company.banking.common.eod.EndOfDayProbe;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test hooks into end-of-day: a probe that stops a run at a checkpoint (as a crash would) and a check that can be
 * made to fail. Tests importing this share one application context.
 */
@TestConfiguration
public class EndOfDayProbes {

    /** Stops a run (as a crash would) the first time the armed checkpoint is reached. */
    public static class StopProbe implements EndOfDayProbe {
        public volatile String armedStep;
        public volatile String armedPoint;

        @Override
        public void reached(String step, String point) {
            if (step.equals(armedStep) && point.equals(armedPoint)) {
                armedStep = null;
                throw new IllegalStateException("Simulated crash at " + step + "/" + point);
            }
        }
    }

    public static class SwitchableCheck implements EndOfDayCheck {
        public volatile String problem;

        @Override
        public List<String> problems(LocalDate businessDate) {
            return problem == null ? List.of() : List.of(problem);
        }
    }

    @Bean
    StopProbe stopProbe() {
        return new StopProbe();
    }

    @Bean
    SwitchableCheck switchableCheck() {
        return new SwitchableCheck();
    }
}
