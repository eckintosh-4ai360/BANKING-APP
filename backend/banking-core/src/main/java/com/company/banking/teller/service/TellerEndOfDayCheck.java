package com.company.banking.teller.service;

import com.company.banking.common.eod.EndOfDayCheck;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A business date closes only when every teller has counted and closed their drawer (and any difference has been
 * accepted), so the day's cash is fully accounted for.
 */
@Component
@RequiredArgsConstructor
class TellerEndOfDayCheck implements EndOfDayCheck {

    private final TellerSessionService sessions;

    @Override
    public List<String> problems(LocalDate businessDate) {
        return sessions.unclosedSessions();
    }
}
