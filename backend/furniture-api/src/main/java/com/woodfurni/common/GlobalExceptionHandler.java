package com.woodfurni.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

import com.woodfurni.inventory.exception.InsufficientStockException;
import com.woodfurni.review.exception.DuplicateReviewException;
import com.woodfurni.review.exception.OrderNotFoundException;
import com.woodfurni.review.exception.OrderOwnershipException;

/**
 * Global exception handler for the WOODFURNI API.
 * Ensures all error responses follow the standard ApiResponse format.
 *
 * Error format:
 * { "success": false, "message": "Validation failed",
 *   "errors": [ { "field": "price", "message": "must be > 0" } ],
 *   "timestamp": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation errors from @Valid annotations.
     * Returns field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        List<com.woodfurni.common.FieldError> fieldErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String fieldName = error instanceof org.springframework.validation.FieldError
                            ? ((org.springframework.validation.FieldError) error).getField()
                            : error.getObjectName();
                    String message = error.getDefaultMessage();
                    return new com.woodfurni.common.FieldError(fieldName, message);
                })
                .collect(Collectors.toList());

        ApiResponse<Object> response = ApiResponse.error("Validation failed", fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles entity not found exceptions.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleEntityNotFoundException(
            EntityNotFoundException ex) {

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles order not found (CHECK 1: order does not exist).
     * → HTTP 404 Not Found
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleOrderNotFoundException(
            OrderNotFoundException ex) {
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles order ownership violation (CHECK 2: order belongs to another user).
     * → HTTP 403 Forbidden
     */
    @ExceptionHandler(OrderOwnershipException.class)
    public ResponseEntity<ApiResponse<Object>> handleOrderOwnershipException(
            OrderOwnershipException ex) {
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handles duplicate review (CHECK 5: already reviewed).
     * → HTTP 409 Conflict
     */
    @ExceptionHandler(DuplicateReviewException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateReviewException(
            DuplicateReviewException ex) {
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles illegal argument exceptions (CHECK 3 & 4: business rule violations).
     * → HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles insufficient stock exceptions.
     * Returns 409 Conflict when reservation cannot be fulfilled.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientStockException(
            InsufficientStockException ex) {

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handles malformed JSON request bodies (HttpMessageNotReadableException).
     * Common causes:
     *   - JSON syntax error (trailing comma, missing brace, // comments, …)
     *   - Field with the wrong type (e.g. null where @NotNull is required)
     *   - Date/time string in a format Jackson can't deserialise into Instant
     *     (e.g. "2026-12-31T23:59" without offset — use "2026-12-31T23:59:00Z"
     *     or rely on the DTO's @JsonFormat, which for PromotionRequest
     *     accepts "yyyy-MM-dd'T'HH:mm" in Asia/Ho_Chi_Minh).
     *
     * Returns 400 with a short, actionable message instead of leaking the
     * 30-line nested stack trace Spring sends by default.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        // Root cause is usually one level down (JsonParseException,
        // InvalidFormatException, …) and contains the real hint.
        Throwable cause = ex.getMostSpecificCause();
        String rootMsg = cause != null ? cause.getMessage() : ex.getMessage();
        // Keep the message short — stack frames confuse Swagger UI testers.
        String message = "Malformed request body: "
                + (rootMsg != null ? rootMsg.split("\\R", 2)[0] : "unknown");
        // Log full stack for ops, but only return the cause's first line
        // to the client so the response stays small.
        log.warn("Malformed request body: {}", rootMsg);
        ApiResponse<Object> response = ApiResponse.error(message);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles all other unhandled exceptions.
     * Returns a generic error message for security (don't expose internal details).
     * Logs full stack trace at ERROR level so we can actually debug NPEs etc.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        // 2026-08-20: previously this swallowed everything silently. Adding a
        // full stack-trace log so the next incident gives us an actionable line
        // number instead of just "NullPointerException" in the response body.
        log.error("Unhandled exception caught by GlobalExceptionHandler", ex);
        ApiResponse<Object> response = ApiResponse.error(
                "An unexpected error occurred: " + ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
