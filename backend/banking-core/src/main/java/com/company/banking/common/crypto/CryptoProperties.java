package com.company.banking.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * {@code banking.crypto.*}: application-level encryption keys, supplied by a secret manager in deployed
 * environments.
 *
 * @param activeKeyVersion version used for new encryptions
 * @param dataKeys         base64-encoded 256-bit AES keys by version; old versions stay configured until all data
 *                         encrypted with them has been re-encrypted
 * @param blindIndexKey    base64-encoded HMAC-SHA256 key for searchable blind indexes; rotating it requires
 *                         recomputing every blind index
 */
@ConfigurationProperties("banking.crypto")
public record CryptoProperties(int activeKeyVersion, Map<Integer, String> dataKeys, String blindIndexKey) {
}
