package com.company.banking.document.storage;

/**
 * Binary object store (port). Objects are already encrypted by the caller; keys are generated internally and never
 * derived from user input.
 */
public interface ObjectStorage {

    void put(String key, byte[] content);

    byte[] get(String key);
}
