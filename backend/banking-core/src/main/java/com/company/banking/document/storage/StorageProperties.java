package com.company.banking.document.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code banking.storage.*}
 *
 * @param backend          {@code filesystem} (local disk or a mounted volume); an S3-compatible adapter is added when
 *                         the hosting decision is made
 * @param basePath         root directory for the filesystem backend
 * @param maxDocumentSize  largest accepted upload
 */
@ConfigurationProperties("banking.storage")
public record StorageProperties(String backend, String basePath, DataSize maxDocumentSize) {
}
