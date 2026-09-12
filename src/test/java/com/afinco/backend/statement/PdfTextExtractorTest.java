package com.afinco.backend.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.afinco.backend.exception.StatementParsingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsSearchableTextFromAnInMemoryPdf() throws IOException {
        String text = extractor.extract(SyntheticStatementPdf.statementWithSidebarAndContinuation());

        assertThat(text).contains("TD CASH BACK VISA", "CORNER SHOP", "MERCHANT REFUND");
    }

    @Test
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> extractor.extract(new byte[0]))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsNonPdfData() {
        assertThatThrownBy(() -> extractor.extract("ordinary text".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsTruncatedPdfData() {
        byte[] bytes = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> extractor.extract(bytes))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsPdfWithoutSearchableText() throws IOException {
        byte[] bytes = SyntheticStatementPdf.blank(1);

        assertThatThrownBy(() -> extractor.extract(bytes))
                .isInstanceOf(StatementParsingException.class)
                .hasMessageContaining("searchable");
    }

    @Test
    void rejectsPasswordProtectedPdf() throws IOException {
        byte[] bytes = SyntheticStatementPdf.encrypted();

        assertThatThrownBy(() -> extractor.extract(bytes))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsPdfThatDisallowsTextExtraction() throws IOException {
        byte[] bytes = SyntheticStatementPdf.ownerEncrypted(false);

        assertThatThrownBy(() -> extractor.extract(bytes)).isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsDocumentsAboveThePageLimit() throws IOException {
        byte[] bytes = SyntheticStatementPdf.blank(101);

        assertThatThrownBy(() -> extractor.extract(bytes))
                .isInstanceOf(StatementParsingException.class)
                .hasMessageContaining("100 pages");
    }

    @Test
    void rejectsDocumentsAboveTheByteLimit() {
        byte[] bytes = new byte[PdfTextExtractor.MAX_BYTES + 1];

        assertThatThrownBy(() -> extractor.extract(bytes))
                .isInstanceOf(StatementParsingException.class)
                .hasMessageContaining("10 MiB");
    }
}
