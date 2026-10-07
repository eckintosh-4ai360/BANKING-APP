package com.company.banking.document.storage;

/**
 * Malware scanning port. A ClamAV (or cloud scanning) adapter must be configured before production go-live; until
 * then uploads are marked {@code SKIPPED} so the gap is visible in the data.
 */
public interface DocumentScanner {

    ScanResult scan(byte[] content);

    enum ScanResult { CLEAN, INFECTED, SKIPPED }
}
