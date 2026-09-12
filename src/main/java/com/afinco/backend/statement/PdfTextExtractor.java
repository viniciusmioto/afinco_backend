package com.afinco.backend.statement;

import com.afinco.backend.exception.StatementParsingException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PAGES = 100;

    public String extract(byte[] bytes) {
        try (PDDocument document = open(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text.isBlank()) {
                throw new StatementParsingException("A searchable PDF is required; scanned documents need OCR");
            }
            return text;
        } catch (IOException | IllegalArgumentException exception) {
            throw new StatementParsingException("The PDF cannot be read");
        }
    }

    PDDocument open(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new StatementParsingException("PDF size must be between 1 byte and 10 MiB");
        }
        PDDocument document = Loader.loadPDF(bytes);
        // Bank PDFs may be owner-encrypted yet open without a password and allow extraction.
        if (!document.getCurrentAccessPermission().canExtractContent() || document.getNumberOfPages() > MAX_PAGES) {
            document.close();
            throw new StatementParsingException("PDF must allow text extraction and contain at most 100 pages");
        }
        return document;
    }
}
