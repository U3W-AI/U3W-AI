package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.common.core.domain.AjaxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Wecom 模块异常处理器
 *
 * <p>将 IllegalArgumentException 转为 HTTP 400 响应。
 * 使用 {@code @Order(Ordered.HIGHEST_PRECEDENCE)} 确保优先于全局 {@code GlobalExceptionHandler}。</p>
 *
 * @author wxfbsir
 * @date 2026-04-15
 */
@RestControllerAdvice(basePackages = "com.wx.fbsir.business.fbs.controller.business")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WecomExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(WecomExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AjaxResult> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("参数校验失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AjaxResult.error(e.getMessage()));
    }
}
