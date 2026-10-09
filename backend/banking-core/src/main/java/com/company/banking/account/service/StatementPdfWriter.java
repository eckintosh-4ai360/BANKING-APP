package com.company.banking.account.service;

import com.company.banking.account.dto.AccountStatement;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * PDF rendering of a statement on A4 pages: a header with the account and period, a table of entries with running
 * balances, and page numbers. Uses the standard Helvetica fonts (no font files to ship); characters they cannot
 * show are replaced with '?'.
 */
final class StatementPdfWriter {

    private static final float MARGIN = 40f;
    private static final float ROW_HEIGHT = 12f;
    private static final float TABLE_FONT_SIZE = 8f;
    private static final float[] COLUMN_WIDTHS = {55f, 95f, 165f, 65f, 65f, 70f};
    private static final String[] COLUMNS = {"Date", "Reference", "Description", "Debit", "Credit", "Balance"};
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private StatementPdfWriter() {
    }

    static byte[] write(AccountStatement statement) {
        return new StatementPdfWriter().render(statement);
    }

    private byte[] render(AccountStatement statement) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{statement.from().toString(), "", "Opening balance", "", "",
                amount(statement.openingBalance())});
        for (AccountStatement.Line line : statement.lines()) {
            rows.add(new String[]{line.date().toString(), line.reference(), line.description(), amount(line.debit()),
                    amount(line.credit()), amount(line.balance())});
        }
        rows.add(new String[]{statement.to().toString(), "", "Closing balance", amount(statement.totalDebits()),
                amount(statement.totalCredits()), amount(statement.closingBalance())});

        float pageHeight = PDRectangle.A4.getHeight();
        int firstPageRows = (int) ((pageHeight - 2 * MARGIN - 150f) / ROW_HEIGHT);
        int otherPageRows = (int) ((pageHeight - 2 * MARGIN - 50f) / ROW_HEIGHT);
        int pages = rows.size() <= firstPageRows ? 1
                : 1 + (int) Math.ceil((rows.size() - firstPageRows) / (double) otherPageRows);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Statement " + statement.accountNumber() + " " + statement.from() + " to " + statement.to());
            info.setCreator(safe(statement.institutionName(), regular));
            int next = 0;
            for (int pageNo = 1; pageNo <= pages; pageNo++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float y = pageHeight - MARGIN;
                    if (pageNo == 1) {
                        y = header(content, statement, y);
                    }
                    y = tableRow(content, COLUMNS, y, bold);
                    int capacity = pageNo == 1 ? firstPageRows : otherPageRows;
                    for (int i = 0; i < capacity && next < rows.size(); i++, next++) {
                        y = tableRow(content, rows.get(next), y, regular);
                    }
                    text(content, regular, 7f, MARGIN, MARGIN - 15f,
                            "Generated " + GENERATED.format(statement.generatedAt()) + " from the ledger of "
                                    + statement.institutionName() + ".   Page " + pageNo + " of " + pages);
                }
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not render the statement PDF", failure);
        }
    }

    private float header(PDPageContentStream content, AccountStatement statement, float top) throws IOException {
        float y = top;
        text(content, bold, 14f, MARGIN, y, statement.institutionName());
        y -= 20f;
        text(content, bold, 11f, MARGIN, y, "Account statement");
        y -= 18f;
        String[][] facts = {
                {"Account", statement.accountNumber() + "  " + statement.accountTitle()},
                {"Holders", String.join(", ", statement.holders())},
                {"Product", statement.productName() + " (" + statement.currency() + ")"},
                {"Branch", statement.branchName()},
                {"Period", statement.from() + " to " + statement.to()},
                {"Opening / closing", amount(statement.openingBalance()) + " / " + amount(statement.closingBalance())}};
        for (String[] fact : facts) {
            text(content, bold, 9f, MARGIN, y, fact[0]);
            text(content, regular, 9f, MARGIN + 95f, y, fit(fact[1], regular, 9f, 420f));
            y -= 13f;
        }
        return y - 12f;
    }

    private float tableRow(PDPageContentStream content, String[] cells, float y, PDType1Font font)
            throws IOException {
        float x = MARGIN;
        for (int column = 0; column < cells.length; column++) {
            String value = fit(cells[column] == null ? "" : cells[column], font, TABLE_FONT_SIZE,
                    COLUMN_WIDTHS[column] - 4f);
            boolean numeric = column >= 3;
            float offset = numeric ? COLUMN_WIDTHS[column] - 4f - width(value, font, TABLE_FONT_SIZE) : 0f;
            text(content, font, TABLE_FONT_SIZE, x + offset, y, value);
            x += COLUMN_WIDTHS[column];
        }
        return y - ROW_HEIGHT;
    }

    private void text(PDPageContentStream content, PDType1Font font, float size, float x, float y, String value)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safe(value, font));
        content.endText();
    }

    /**
     * Shortens text to a width, ending with "..." when cut.
     */
    private String fit(String value, PDType1Font font, float size, float maxWidth) throws IOException {
        String safe = safe(value, font);
        if (width(safe, font, size) <= maxWidth) {
            return safe;
        }
        String cut = safe;
        while (!cut.isEmpty() && width(cut + "...", font, size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static float width(String value, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(value) / 1000f * size;
    }

    /**
     * Keeps the characters the font can encode (WinAnsi for the standard fonts); replaces the rest with '?'.
     */
    static String safe(String value, PDType1Font font) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            if (Character.isISOControl(codePoint)) {
                result.append(' ');
                return;
            }
            try {
                font.encode(character);
                result.append(character);
            } catch (IOException | IllegalArgumentException unsupported) {
                result.append('?');
            }
        });
        return result.toString();
    }

    private static String amount(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
