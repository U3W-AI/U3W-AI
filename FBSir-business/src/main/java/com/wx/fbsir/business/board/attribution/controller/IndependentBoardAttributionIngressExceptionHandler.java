package com.wx.fbsir.business.board.attribution.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.Map;
import java.util.Set;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes = IndependentBoardAttributionIngressController.class)
public class IndependentBoardAttributionIngressExceptionHandler {
    private static final Set<String> IDENTITY_CONFLICT_REASONS = Set.of(
            "official_identity_mismatch", "listed_identity_mismatch");
    private static final Set<String> STATE_CONFLICT_REASONS = Set.of(
            "event_or_receipt_collision", "journey_identity_conflict",
            "event_out_of_order", "journey_head_conflict",
            "intent_family_conflict");
    private static final Set<String> STATE_UNAVAILABLE_REASONS = Set.of(
            "attribution_writer_disabled");

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(
            IllegalArgumentException error) {
        String reason = reason(error);
        HttpStatus status = IDENTITY_CONFLICT_REASONS.contains(reason)
                ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return response(status, reason);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(
            IllegalStateException error) {
        String reason = reason(error);
        if (STATE_CONFLICT_REASONS.contains(reason)) {
            return response(HttpStatus.CONFLICT, reason);
        }
        if (STATE_UNAVAILABLE_REASONS.contains(reason)) {
            return response(HttpStatus.SERVICE_UNAVAILABLE,
                    "receiver_unavailable");
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "receiver_internal_error");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> malformedBody(
            HttpMessageNotReadableException error) {
        return response(HttpStatus.BAD_REQUEST, "request_body_malformed");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> methodNotAllowed(
            HttpRequestMethodNotSupportedException error) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(HttpMethod.POST)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of(
                        "status", HttpStatus.METHOD_NOT_ALLOWED.name(),
                        "reason", "method_not_allowed"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> unsupportedMediaType(
            HttpMediaTypeNotSupportedException error) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "media_type_not_supported");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, String>> notAcceptable(
            HttpMediaTypeNotAcceptableException error) {
        return response(HttpStatus.NOT_ACCEPTABLE, "media_type_not_acceptable");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> persistenceUnavailable(
            DataAccessException error) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "receiver_persistence_unavailable");
    }

    /**
     * The application-wide legacy advice serializes RuntimeException as an
     * AjaxResult without a non-2xx status.  Keep a receiver-local final fence
     * so an unexpected persistence/mapper/runtime failure can never masquerade
     * as a successful receipt.  The internal message is deliberately withheld.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> internal(RuntimeException error) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "receiver_internal_error");
    }

    private ResponseEntity<Map<String, String>> response(
            HttpStatus status, String reason) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of(
                        "status", status.name(),
                        "reason", reason == null ? "rejected" : reason));
    }

    private String reason(RuntimeException error) {
        return error == null || error.getMessage() == null
                ? "rejected" : error.getMessage();
    }
}
