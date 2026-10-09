package com.company.banking.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.account.dto.AccountStatement;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class StatementWritersTest {

    // Neither is in the WinAnsi encoding of the standard PDF fonts.
    private static final String CEDI_SIGN = String.valueOf((char) 0x20B5);
    private static final String OPEN_O = String.valueOf((char) 0x0186);

    @Test
    void csvQuotesTextAndNeutralisesFormulas() {
        String csv = new String(StatementCsvWriter.write(statement(List.of(
                line("=HYPERLINK(\"http://evil\")", "10.00", null, "90.00"),
                line("Paid \"cash\", thanks", null, "5.00", "95.00")))), StandardCharsets.UTF_8);

        assertThat(csv).startsWith(StatementCsvWriter.BYTE_ORDER_MARK);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(csv).contains("\"Paid \"\"cash\"\", thanks\"");
        assertThat(csv).contains("\"Opening balance\",,,\"100.00\"");
        assertThat(csv).contains("\"Closing balance\",\"10.00\",\"5.00\",\"95.00\"");
        assertThat(csv).contains("\r\n");
    }

    @Test
    void negativeAmountsStayNumbers() {
        assertThat(StatementCsvWriter.cell("-12.50")).isEqualTo("\"-12.50\"");
        assertThat(StatementCsvWriter.cell("-SUM(A1)")).isEqualTo("\"'-SUM(A1)\"");
        assertThat(StatementCsvWriter.cell("@cmd")).isEqualTo("\"'@cmd\"");
        assertThat(StatementCsvWriter.cell(null)).isEmpty();
    }

    @Test
    void pdfHasEveryEntryAcrossPagesAndSurvivesCharactersTheFontLacks() throws IOException {
        List<AccountStatement.Line> lines = new ArrayList<>();
        for (int i = 0; i < 130; i++) {
            lines.add(line("Deposit " + i + " " + CEDI_SIGN + " " + OPEN_O, null, "1.00", "100.00"));
        }
        byte[] pdf = StatementPdfWriter.write(statement(lines));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Account statement", "1000000013", "Deposit 0 ? ?", "Deposit 129",
                    "Opening balance", "Closing balance", "Page 3 of 3");
        }
    }

    private static AccountStatement statement(List<AccountStatement.Line> lines) {
        return new AccountStatement("Test Savings and Loans", UUID.randomUUID(), "1000000013", "Ama Mensah",
                List.of("Ama Mensah"), "Current account", "Head Office", "GHS", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 8), money("100.00"), money("10.00"), money("5.00"), money("95.00"),
                Instant.parse("2026-10-08T12:00:00Z"), lines);
    }

    private static AccountStatement.Line line(String description, String debit, String credit, String balance) {
        return new AccountStatement.Line(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2), "TXN-1",
                description, debit == null ? null : money(debit), credit == null ? null : money(credit),
                money(balance));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
