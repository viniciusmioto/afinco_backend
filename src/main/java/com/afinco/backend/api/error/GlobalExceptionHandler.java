package com.afinco.backend.api.error;

import com.afinco.backend.exception.ConflictException;
import com.afinco.backend.exception.AuthenticationFailedException;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.exception.LoginRateLimitException;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.exception.StatementParsingException;
import com.afinco.backend.exception.UnsupportedStatementException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiErrorResponse> handleAuthenticationFailed(
            AuthenticationFailedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(LoginRateLimitException.class)
    ResponseEntity<ApiErrorResponse> handleLoginRateLimit(
            LoginRateLimitException exception, HttpServletRequest request) {
        long retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        ApiErrorResponse error = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                Map.of());
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .cacheControl(CacheControl.noStore())
                .body(error);
    }

    @ExceptionHandler(UnsupportedStatementException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedStatement(
            UnsupportedStatementException exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY,
                "The statement format is not supported; only TD credit-card statements are supported",
                request, Map.of());
    }

    @ExceptionHandler(StatementParsingException.class)
    ResponseEntity<ApiErrorResponse> handleStatementParsing(
            StatementParsingException exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY,
                "The PDF could not be parsed; provide a searchable TD credit-card statement that opens without a password", request, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleUploadSize(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE,
                "The PDF upload exceeds the 10 MiB limit", request, Map.of());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                "A PDF file must be supplied in the file form field", request, Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        String message = "statementType".equals(exception.getParameterName())
                ? "A statementType of CREDIT_CARD or CHECKING_ACCOUNT must be supplied"
                : "A required request parameter is missing";
        return response(HttpStatus.BAD_REQUEST, message, request, Map.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiErrorResponse> handleMultipart(
            MultipartException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Malformed multipart upload", request, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "The request Content-Type is not supported", request, Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiErrorResponse> handleUnknownRoute(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "The requested API route does not exist", request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED,
                "The HTTP method is not supported for this route", request, Map.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            InvalidRequestException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiErrorResponse> handleConflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof ConflictException
                ? exception.getMessage()
                : "The request conflicts with existing data";
        return response(HttpStatus.CONFLICT, message, request, Map.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiErrorResponse> handleBindingValidation(
            BindException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : error.getObjectName();
            errors.putIfAbsent(field, error.getDefaultMessage());
        });
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                Map.of("request", "One or more request parameters are invalid"));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        ConversionFailedException.class
    })
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Malformed request payload or parameter", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected request failure: {}", exception.getClass().getSimpleName());
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request,
                Map.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {
        ApiErrorResponse error = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                validationErrors);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(error);
    }
}
