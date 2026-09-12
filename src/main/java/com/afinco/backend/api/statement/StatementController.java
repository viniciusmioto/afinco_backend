package com.afinco.backend.api.statement;

import com.afinco.backend.api.statement.dto.StatementImportRequest;
import com.afinco.backend.api.statement.dto.StatementImportResponse;
import com.afinco.backend.api.statement.dto.StatementResponse;
import com.afinco.backend.api.statement.dto.StatementUploadResponse;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.service.StatementService;
import com.afinco.backend.service.StatementUploadService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementController {

    private final StatementUploadService statementUploadService;
    private final StatementService statementService;

    public StatementController(StatementUploadService statementUploadService, StatementService statementService) {
        this.statementUploadService = statementUploadService;
        this.statementService = statementService;
    }

    @GetMapping({"", "/"})
    public List<StatementResponse> findStatements() {
        return statementService.findStatements();
    }

    /** Persists a reviewed preview: 201 for a new statement, 200 when rows were appended to an existing one. */
    @PostMapping({"", "/"})
    public ResponseEntity<StatementImportResponse> importStatement(
            @Valid @RequestBody StatementImportRequest request) {
        StatementImportResponse response = statementService.importStatement(request);
        if (!response.created()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/api/v1/statements/" + response.statement().id()))
                .body(response);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StatementUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("statementType") StatementType statementType) {
        if (file.isEmpty()) {
            throw new InvalidRequestException("The uploaded PDF must not be empty");
        }
        if (file.getSize() > StatementUploadService.MAX_UPLOAD_BYTES) {
            throw new MaxUploadSizeExceededException(StatementUploadService.MAX_UPLOAD_BYTES);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidRequestException("The uploaded PDF could not be read");
        }
        StatementUploadResponse response = statementUploadService.parse(bytes, statementType);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
