package com.afinco.backend.api.statement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.afinco.backend.api.statement.dto.ParsedTransactionResponse;
import com.afinco.backend.api.statement.dto.StatementUploadResponse;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.exception.StatementParsingException;
import com.afinco.backend.exception.UnsupportedStatementException;
import com.afinco.backend.service.StatementUploadService;
import com.afinco.backend.statement.StatementType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatementController.class)
class StatementControllerTest {

    private static final String UPLOAD_URL = "/api/v1/statements/upload";
    private static final byte[] PDF_BYTES = "%PDF-1.7 synthetic fixture".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatementUploadService service;

    @Test
    void returnsStructuredPreviewWithDuplicateFlagsAndNoStore() throws Exception {
        ParsedTransactionResponse transaction = new ParsedTransactionResponse(
                LocalDate.of(2026, 1, 15), new BigDecimal("18.50"), TransactionType.CREDIT,
                "Synthetic Market", "TD Bank", "a".repeat(64), TransactionStatus.DUPLICATE_PENDING, true,
                ExpenseType.OCCASIONAL, "Occasional");
        when(service.parse(PDF_BYTES, StatementType.CREDIT_CARD)).thenReturn(
                new StatementUploadResponse("TD Bank", 1, 1, new BigDecimal("18.50"), List.of(transaction)));

        mockMvc.perform(creditCardUpload())
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.bankName").value("TD Bank"))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.duplicateCount").value(1))
                .andExpect(jsonPath("$.total").value(18.50))
                .andExpect(jsonPath("$.transactions[0].date").value("2026-01-15"))
                .andExpect(jsonPath("$.transactions[0].amount").value(18.50))
                .andExpect(jsonPath("$.transactions[0].status").value("DUPLICATE_PENDING"))
                .andExpect(jsonPath("$.transactions[0].duplicate").value(true));
    }

    @Test
    void rejectsMissingFilePart() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL).param("statementType", "CREDIT_CARD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A PDF file must be supplied in the file form field"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingStatementType() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL).file(pdf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("A statementType of CREDIT_CARD or CHECKING_ACCOUNT must be supplied"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnknownStatementType() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL).file(pdf()).param("statementType", "SAVINGS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request payload or parameter"));

        verifyNoInteractions(service);
    }

    @Test
    void delegatesCheckingAccountSelectionForAnExplicitUnsupportedResponse() throws Exception {
        when(service.parse(PDF_BYTES, StatementType.CHECKING_ACCOUNT))
                .thenThrow(new UnsupportedStatementException("Not implemented"));

        mockMvc.perform(multipart(UPLOAD_URL).file(pdf()).param("statementType", "CHECKING_ACCOUNT"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(new MockMultipartFile("file", new byte[0]))
                        .param("statementType", "CREDIT_CARD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The uploaded PDF must not be empty"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsFileExceedingTenMiB() throws Exception {
        MockMultipartFile oversized = new MockMultipartFile("file", PDF_BYTES) {
            @Override
            public long getSize() {
                return StatementUploadService.MAX_UPLOAD_BYTES + 1;
            }
        };

        mockMvc.perform(multipart(UPLOAD_URL).file(oversized).param("statementType", "CREDIT_CARD"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value("The PDF upload exceeds the 10 MiB limit"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonMultipartRequest() throws Exception {
        mockMvc.perform(post(UPLOAD_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonPdfContentDespitePdfContentType() throws Exception {
        when(service.parse(any(), any())).thenThrow(
                new InvalidRequestException("Uploaded file must be a PDF document"));

        mockMvc.perform(creditCardUpload())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uploaded file must be a PDF document"));
    }

    @Test
    void returnsSafeErrorForUnsupportedStatement() throws Exception {
        when(service.parse(any(), any())).thenThrow(
                new UnsupportedStatementException("Sensitive sample content"));

        mockMvc.perform(creditCardUpload())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message")
                        .value("The statement format is not supported; only TD credit-card statements are supported"));
    }

    @Test
    void returnsSafeErrorForUnreadablePdf() throws Exception {
        when(service.parse(any(), any())).thenThrow(new StatementParsingException("Sensitive sample content"));

        mockMvc.perform(creditCardUpload())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The PDF could not be parsed; provide a searchable TD credit-card statement that opens without a password"));
    }

    @Test
    void returnsSafeErrorWhenUploadCannotBeRead() throws Exception {
        MockMultipartFile unreadable = new MockMultipartFile("file", PDF_BYTES) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("Sensitive sample filename");
            }
        };

        mockMvc.perform(multipart(UPLOAD_URL).file(unreadable).param("statementType", "CREDIT_CARD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The uploaded PDF could not be read"));

        verifyNoInteractions(service);
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "synthetic.pdf", MediaType.APPLICATION_PDF_VALUE, PDF_BYTES);
    }

    private org.springframework.test.web.servlet.RequestBuilder creditCardUpload() {
        return multipart(UPLOAD_URL).file(pdf()).param("statementType", "CREDIT_CARD");
    }
}
