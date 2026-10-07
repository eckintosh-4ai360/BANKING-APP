package com.company.banking.document.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypeSnifferTest {

    @Test
    void recognisesAllowedTypesByTheirSignatures() {
        assertThat(ContentTypeSniffer.sniff(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .hasValue("image/jpeg");
        assertThat(ContentTypeSniffer.sniff(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00}))
                .hasValue("image/png");
        assertThat(ContentTypeSniffer.sniff("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .hasValue("application/pdf");
    }

    @Test
    void rejectsEverythingElseWhateverItsName() {
        assertThat(ContentTypeSniffer.sniff("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ContentTypeSniffer.sniff(new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00})).isEmpty();
        assertThat(ContentTypeSniffer.sniff(new byte[0])).isEmpty();
    }

    @Test
    void fileNamesAreSanitisedAndGetTheSniffedExtension() {
        assertThat(DocumentStorageService.sanitizeFileName("../../etc/passwd", "application/pdf"))
                .isEqualTo("passwd.pdf");
        assertThat(DocumentStorageService.sanitizeFileName("C:\\Users\\me\\ID card<1>.exe", "image/png"))
                .isEqualTo("ID card_1_.png");
        assertThat(DocumentStorageService.sanitizeFileName(null, "image/jpeg")).isEqualTo("document.jpg");
    }
}
