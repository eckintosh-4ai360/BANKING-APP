package com.company.banking.common.eod;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What a step works with. The tenant and the system actor are already bound to the thread.
 */
public interface EndOfDayContext {

    UUID runId();

    /** The business date being closed. */
    LocalDate businessDate();

    /** The business date branches are already working on. */
    LocalDate nextBusinessDate();

    /** Runs one batch in its own database transaction and commits it. */
    <T> T inTransaction(Supplier<T> work);

    /**
     * Marks a point where the step's committed work is consistent (after a batch). Probes registered for testing
     * may stop the run here to simulate a crash.
     */
    void checkpoint(String step, String point);
}
