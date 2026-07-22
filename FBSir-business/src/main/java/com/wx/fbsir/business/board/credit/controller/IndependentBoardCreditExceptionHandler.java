package com.wx.fbsir.business.board.credit.controller;

import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Endpoint-local transport semantics that never reflect exception details or request content. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IndependentBoardCreditAdminController.class)
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.credit-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardCreditExceptionHandler {
    private static final Logger log =
            LoggerFactory.getLogger(IndependentBoardCreditExceptionHandler.class);
    private static final Set<Integer> EXPOSED_SERVICE_STATUSES = Set.of(400, 404, 409, 500);
    private static final Pattern STABLE_CREDIT_CODE =
            Pattern.compile("CREDIT_[A-Z0-9_]{1,95}");

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<AjaxResult> invalidRequest(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, "CREDIT_REQUEST_INVALID");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AjaxResult> accessDenied(AccessDeniedException ignored) {
        return error(HttpStatus.FORBIDDEN, "CREDIT_ACCESS_DENIED");
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<AjaxResult> serviceFailure(
            ServiceException exception, HttpServletRequest request) {
        Integer statusCode = exception.getCode();
        String errorCode = exception.getMessage();
        if (statusCode == null
                || !EXPOSED_SERVICE_STATUSES.contains(statusCode)
                || errorCode == null
                || !STABLE_CREDIT_CODE.matcher(errorCode).matches()) {
            log.error("Credit endpoint rejected an unstable service error at {}",
                    request.getRequestURI());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "CREDIT_LEDGER_FAILED");
        }
        return error(HttpStatus.valueOf(statusCode), errorCode);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AjaxResult> unexpected(
            RuntimeException exception, HttpServletRequest request) {
        log.error("Credit endpoint failed at {} with {}",
                request.getRequestURI(), exception.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "CREDIT_LEDGER_FAILED");
    }

    private static ResponseEntity<AjaxResult> error(
            HttpStatus transportStatus, String errorCode) {
        return ResponseEntity.status(transportStatus)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(AjaxResult.error(transportStatus.value(), errorCode));
    }
}
