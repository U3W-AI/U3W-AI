package com.wx.fbsir.business.board.attribution.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(
        assignableTypes = IndependentBoardAttributionAdminController.class)
public class IndependentBoardAttributionAdminExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(
            IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(Map.of(
                        "status", HttpStatus.BAD_REQUEST.name(),
                        "reason", error.getMessage()));
    }
}
