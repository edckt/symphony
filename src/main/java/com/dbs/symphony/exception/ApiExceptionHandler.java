package com.dbs.symphony.exception;

import com.dbs.symphony.dto.ErrorDto;
import com.dbs.symphony.dto.QuotaViolationErrorDto;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorDto> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(403)
                .body(error(e.getMessage(), "FORBIDDEN"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403)
                .body(error("Access denied", "FORBIDDEN"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(404)
                .body(error(e.getMessage(), "NOT_FOUND"));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorDto> handleConflict(ConflictException e) {
        return ResponseEntity.status(409)
                .body(error(e.getMessage(), "CONFLICT"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest()
                .body(error(message, "BAD_REQUEST"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    int dot = path.lastIndexOf('.');
                    String field = dot >= 0 ? path.substring(dot + 1) : path;
                    return field + ": " + cv.getMessage();
                })
                .orElse("Invalid request");
        return ResponseEntity.badRequest()
                .body(error(message, "BAD_REQUEST"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(error("Malformed JSON request", "BAD_REQUEST"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409)
                .body(error("Data integrity violation", "CONFLICT"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(error(e.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(QuotaViolationException.class)
    public ResponseEntity<QuotaViolationErrorDto> handleQuotaViolation(QuotaViolationException e) {
        return ResponseEntity.status(422)
                .body(new QuotaViolationErrorDto("QUOTA_VIOLATION", e.getViolations(), e.getSnapshot()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDto> handleRuntime(RuntimeException e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError()
                .body(error("Internal server error", "INTERNAL_ERROR"));
    }

    private static ErrorDto error(String message, String code) {
        String requestId = MDC.get("requestId");
        return new ErrorDto(message, code, requestId != null ? requestId : UUID.randomUUID().toString());
    }
}
