package com.company.banking.common.eod;

/**
 * Observes end-of-day checkpoints. Production registers none; tests register one that throws at a chosen point to
 * prove that every step can be stopped anywhere and resumed with the same result.
 */
public interface EndOfDayProbe {

    void reached(String step, String point);
}
