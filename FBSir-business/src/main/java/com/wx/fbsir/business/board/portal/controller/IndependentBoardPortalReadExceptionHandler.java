package com.wx.fbsir.business.board.portal.controller;

import com.wx.fbsir.business.board.portal.BoardPortalBadRequestException;
import com.wx.fbsir.business.board.portal.BoardPortalDataDriftException;
import com.wx.fbsir.business.board.portal.BoardPortalForbiddenException;
import com.wx.fbsir.common.constant.HttpStatus;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        IndependentBoardPortalMeReadController.class,
        IndependentBoardPortalAdminReadController.class
})
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.portal-candidate",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardPortalReadExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(IndependentBoardPortalReadExceptionHandler.class);

    @ExceptionHandler(BoardPortalBadRequestException.class)
    public ResponseEntity<AjaxResult> badRequest(BoardPortalBadRequestException exception) {
        return error(org.springframework.http.HttpStatus.BAD_REQUEST,
                HttpStatus.BAD_REQUEST, exception.getErrorCode());
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<AjaxResult> invalidRequest(Exception ignored) {
        return error(org.springframework.http.HttpStatus.BAD_REQUEST,
                HttpStatus.BAD_REQUEST, "INVALID_QUERY_PARAMETER");
    }

    @ExceptionHandler({BoardPortalForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<AjaxResult> forbidden(Exception exception) {
        String errorCode = exception instanceof BoardPortalForbiddenException portalException
                ? portalException.getErrorCode()
                : "PORTAL_READ_FORBIDDEN";
        return error(org.springframework.http.HttpStatus.FORBIDDEN,
                HttpStatus.FORBIDDEN, errorCode);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<AjaxResult> methodNotAllowed() {
        return error(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED,
                HttpStatus.BAD_METHOD, "METHOD_NOT_ALLOWED");
    }

    @ExceptionHandler(BoardPortalDataDriftException.class)
    public ResponseEntity<AjaxResult> dataDrift(
            BoardPortalDataDriftException exception, HttpServletRequest request) {
        log.error("Portal read data drift at {}: {}",
                request.getRequestURI(), exception.getErrorCode());
        return error(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.ERROR, "PORTAL_READ_DATA_DRIFT");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<AjaxResult> unavailable(
            DataAccessException ignored, HttpServletRequest request) {
        log.error("Portal read data source unavailable at {}", request.getRequestURI());
        return error(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                503, "PORTAL_READ_UNAVAILABLE");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<AjaxResult> unexpected(
            RuntimeException exception, HttpServletRequest request) {
        log.error("Portal read failed at {} with {}",
                request.getRequestURI(), exception.getClass().getSimpleName());
        return error(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.ERROR, "PORTAL_READ_FAILED");
    }

    private static ResponseEntity<AjaxResult> error(
            org.springframework.http.HttpStatus transportStatus,
            int bodyStatus,
            String errorCode) {
        return ResponseEntity.status(transportStatus)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(AjaxResult.error(bodyStatus, errorCode));
    }
}
