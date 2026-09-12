package com.afinco.backend.statement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Creates only invented statement content, entirely in memory. */
final class SyntheticStatementPdf {

    private SyntheticStatementPdf() {
    }

    static byte[] statementWithSidebarAndContinuation() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream content = new PDPageContentStream(document, first)) {
                text(content, 36, 740, "TD CASH BACK VISA");
                text(content, 36, 720, "STATEMENT DATE: January 15, 2026");
                text(content, 36, 700, "STATEMENT PERIOD: December 16, 2025 to January 15, 2026");
                tableHeader(content, 660);
                text(content, 36, 620, "DEC 29 JAN 2 CORNER SHOP");
                text(content, 330, 620, "$12.30");
                text(content, 400, 620, "AVAILABLE CREDIT $9,999.99");
                text(content, 36, 600, "JAN 3 JAN 4 ONLINE SERVICE");
                text(content, 330, 600, "$24.80");
                text(content, 400, 600, "MINIMUM PAYMENT $50.00");
                text(content, 36, 40, "1 OF 2");
            }
            PDPage second = new PDPage();
            document.addPage(second);
            try (PDPageContentStream content = new PDPageContentStream(document, second)) {
                text(content, 36, 740, "TD CASH BACK VISA");
                tableHeader(content, 700);
                text(content, 36, 660, "JAN 5 JAN 6 MERCHANT REFUND");
                text(content, 330, 660, "$12.30 CR");
                text(content, 36, 630, "TOTAL NEW BALANCE");
                text(content, 330, 630, "$24.80");
                text(content, 36, 40, "2 OF 2");
            }
            return save(document);
        }
    }

    static byte[] blank(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int page = 0; page < pageCount; page++) {
                document.addPage(new PDPage());
            }
            return save(document);
        }
    }

    static byte[] encrypted() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                text(content, 36, 740, "TD CASH BACK VISA");
                text(content, 36, 720, "STATEMENT DATE: January 15, 2026");
            }
            document.protect(new StandardProtectionPolicy(
                    "synthetic-owner-password", "synthetic-user-password", new AccessPermission()));
            return save(document);
        }
    }

    static byte[] ownerEncrypted(boolean allowExtraction) throws IOException {
        try (PDDocument document = Loader.loadPDF(statementWithSidebarAndContinuation())) {
            AccessPermission permission = new AccessPermission();
            permission.setCanExtractContent(allowExtraction);
            document.protect(new StandardProtectionPolicy("invented-owner-password", "", permission));
            return save(document);
        }
    }

    private static void tableHeader(PDPageContentStream content, float y) throws IOException {
        text(content, 36, y, "TRANSACTION POSTING");
        text(content, 36, y - 16, "DATE DATE ACTIVITY DESCRIPTION");
        text(content, 330, y - 16, "AMOUNT($)");
    }

    private static void text(PDPageContentStream content, float x, float y, String value)
            throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
        content.newLineAtOffset(x, y);
        content.showText(value);
        content.endText();
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }
}
