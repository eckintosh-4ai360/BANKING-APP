package com.company.banking.document.service;

import java.util.Optional;

/**
 * Determines the real file type from its leading bytes. The client-declared content type and file extension are
 * never trusted.
 */
final class ContentTypeSniffer {

    static final String JPEG = "image/jpeg";
    static final String PNG = "image/png";
    static final String PDF = "application/pdf";

    private ContentTypeSniffer() {
    }

    static Optional<String> sniff(byte[] content) {
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
