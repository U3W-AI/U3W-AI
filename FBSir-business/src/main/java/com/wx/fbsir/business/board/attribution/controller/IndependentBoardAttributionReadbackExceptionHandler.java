package com.wx.fbsir.business.board.attribution.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Prevents global legacy HTTP-200 wrappers and suppresses validation detail. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes = IndependentBoardAttributionReadbackController.class)
public class IndependentBoardAttributionReadbackExceptionHandler {
    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, String>> invalid(Exception error) {
        return response(HttpStatus.BAD_REQUEST, "readback_request_invalid");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> internal(RuntimeException error) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "readback_internal_error");
    }

    private ResponseEntity<Map<String, String>> response(
            HttpStatus status,
            String reason) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of(
                        "status", status.name(),
                        "reason", reason));
    }
}
