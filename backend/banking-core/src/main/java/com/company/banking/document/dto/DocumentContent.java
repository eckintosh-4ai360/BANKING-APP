package com.company.banking.document.dto;

/**
 * Decrypted, integrity-checked document bytes.
 */
public record DocumentContent(StoredDocumentInfo info, byte[] bytes) {
}
