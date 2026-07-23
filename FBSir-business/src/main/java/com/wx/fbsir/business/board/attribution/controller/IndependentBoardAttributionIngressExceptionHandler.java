package com.wx.fbsir.business.board.attribution.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(
        assignableTypes = IndependentBoardAttributionIngressController.class)
public class IndependentBoardAttributionIngressExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(
            IllegalArgumentException error) {
        return response(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(
            IllegalStateException error) {
        return response(HttpStatus.CONFLICT, error.getMessage());
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
}
