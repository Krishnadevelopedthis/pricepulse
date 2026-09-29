package com.pricepulse.web;

import com.pricepulse.dto.ErrorResponse;
import com.pricepulse.util.InvalidPriceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Maps every failure to a safe JSON body. Stack traces are logged, never returned. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return body(e.getStatus(), e.getCode(), e.getMessage(), null, e.getProductId());
    }

    @ExceptionHandler(InvalidPriceException.class)
    ResponseEntity<ErrorResponse> invalid(InvalidPriceException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", e.getMessage(),
                Map.of(e.getField(), e.getMessage()), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), "Invalid value."));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", fields, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> malformed(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request could not be read.", null, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        String ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled error, ref={}", ref, e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on the server. Reference: " + ref, null, null);
    }

    private ResponseEntity<ErrorResponse> body(HttpStatus s, String code, String msg, Map<String, String> fields, String productId) {
        return ResponseEntity.status(s).body(new ErrorResponse(Instant.now(), s.value(), code, msg, fields, productId));
    }
}
