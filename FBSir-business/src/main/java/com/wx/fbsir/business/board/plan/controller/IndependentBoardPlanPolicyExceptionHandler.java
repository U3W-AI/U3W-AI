package com.wx.fbsir.business.board.plan.controller;

import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Endpoint-local transport semantics that never reflect request or persistence details. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IndependentBoardPlanPolicyAdminController.class)
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.plan-policy-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardPlanPolicyExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(
            IndependentBoardPlanPolicyExceptionHandler.class);
    private static final Set<Integer> EXPOSED_STATUSES = Set.of(400, 404, 409, 500);
    private static final Pattern STABLE_CODE = Pattern.compile(
            "BOARD_PLAN_POLICY_[A-Z0-9_]{1,95}");

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<AjaxResult> invalidRequest(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, "BOARD_PLAN_POLICY_REQUEST_INVALID");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AjaxResult> accessDenied(AccessDeniedException ignored) {
        return error(HttpStatus.FORBIDDEN, "BOARD_PLAN_POLICY_ACCESS_DENIED");
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<AjaxResult> serviceFailure(
            ServiceException exception, HttpServletRequest request) {
        Integer status = exception.getCode();
        String code = exception.getMessage();
        if (status == null
                || !EXPOSED_STATUSES.contains(status)
                || code == null
                || !STABLE_CODE.matcher(code).matches()) {
            log.error("Plan-policy endpoint rejected an unstable service error at {}",
                    request.getRequestURI());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "BOARD_PLAN_POLICY_FAILED");
        }
        return error(HttpStatus.valueOf(status), code);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AjaxResult> unexpected(
            RuntimeException exception, HttpServletRequest request) {
        log.error("Plan-policy endpoint failed at {} with {}",
                request.getRequestURI(), exception.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "BOARD_PLAN_POLICY_FAILED");
    }

    private static ResponseEntity<AjaxResult> error(
            HttpStatus status, String code) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(AjaxResult.error(status.value(), code));
    }
}
