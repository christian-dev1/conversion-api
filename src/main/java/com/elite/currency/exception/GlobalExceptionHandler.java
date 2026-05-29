package com.elite.currency.exception;

import com.elite.currency.dto.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        List<ErrorResponse.FieldViolation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldViolation.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .toList();

        log.warn("Validation failed on {}: {}", req.getRequestURI(), violations);

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .status(400)
                .error("VALIDATION_ERROR")
                .message("Request validation failed — please review the 'violations' list.")
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .violations(violations)
                .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {

        List<ErrorResponse.FieldViolation> violations = ex.getConstraintViolations()
                .stream()
                .map(cv -> ErrorResponse.FieldViolation.builder()
                        .field(cv.getPropertyPath().toString())
                        .message(cv.getMessage())
                        .rejectedValue(cv.getInvalidValue())
                        .build())
                .toList();

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .status(400)
                .error("CONSTRAINT_VIOLATION")
                .message("One or more parameters did not pass validation.")
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .violations(violations)
                .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .status(400)
                .error("TYPE_MISMATCH")
                .message("Parameter '" + ex.getName() + "' has an invalid type: " + ex.getMessage())
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .status(400)
                .error("MISSING_PARAMETER")
                .message("Required parameter '" + ex.getParameterName() + "' is missing.")
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(InvalidCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCurrency(
            InvalidCurrencyException ex, HttpServletRequest req) {

        log.warn("Invalid currency requested: {}", ex.getCurrencyCode());

        return ResponseEntity.unprocessableEntity().body(ErrorResponse.builder()
                .status(422)
                .error("INVALID_CURRENCY")
                .message(ex.getMessage())
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApi(
            ExternalApiException ex, HttpServletRequest req) {

        log.error("External exchange-rate API failure: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.builder()
                .status(503)
                .error("EXTERNAL_API_UNAVAILABLE")
                .message("The exchange-rate provider is currently unavailable. Please retry later.")
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(
            CallNotPermittedException ex, HttpServletRequest req) {

        log.warn("Circuit breaker OPEN — blocking call to exchange-rate API");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.builder()
                .status(503)
                .error("CIRCUIT_BREAKER_OPEN")
                .message("Exchange-rate service is temporarily suspended due to repeated failures. "
                         + "The circuit will attempt recovery in ~30 seconds.")
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {

        log.error("Unhandled exception on {}", req.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.builder()
                .status(500)
                .error("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Our team has been notified.")
                .path(req.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }
}
