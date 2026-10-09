package com.company.banking.common.eod;

import java.time.LocalDate;
import java.util.List;

/**
 * A condition that must hold before end-of-day may close a business date (e.g. every teller session closed).
 * Checked before the date rolls; any problem stops the run from starting.
 */
public interface EndOfDayCheck {

    /**
     * @return human-readable problems; empty when the date may close
     */
    List<String> problems(LocalDate businessDate);
}
