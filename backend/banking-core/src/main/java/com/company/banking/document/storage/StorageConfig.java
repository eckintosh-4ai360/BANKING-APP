package com.company.banking.document.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean
    public ObjectStorage objectStorage(StorageProperties properties) {
        if (!"filesystem".equals(properties.backend())) {
            throw new IllegalStateException("Unsupported storage backend: " + properties.backend());
        }
        return new FileSystemObjectStorage(Path.of(properties.basePath()));
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentScanner documentScanner() {
        return content -> DocumentScanner.ScanResult.SKIPPED;
    }
}
