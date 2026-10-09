package com.company.banking.common.eod;

import java.util.Map;

/**
 * One step of end-of-day processing, contributed by the module that owns the work (interest by accounts, GL
 * snapshots by the ledger, ...). Steps run in ascending {@link #order()} after the business date has rolled.
 *
 * <p>A step must be <b>idempotent at every point</b>: it commits its work in batches through
 * {@link EndOfDayContext#inTransaction}, and running it again after a failure or crash anywhere must give exactly the
 * same result as one uninterrupted run (already-done work is detected and skipped, never repeated).
 */
public interface EndOfDayStep {

    /** Stable code, e.g. {@code GL_SNAPSHOT}; at most 40 characters. */
    String code();

    int order();

    /**
     * @return a small summary of what was done (counts, totals), stored with the run
     */
    Map<String, Object> run(EndOfDayContext context);
}
