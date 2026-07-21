package com.wx.fbsir.framework.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.wx.fbsir.common.constant.HttpStatus;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.text.Convert;
import com.wx.fbsir.common.exception.DemoModeException;
import com.wx.fbsir.common.exception.ServiceException;
import com.wx.fbsir.common.utils.StringUtils;
import com.wx.fbsir.common.utils.html.EscapeUtil;

/**
 * 全局异常处理器
 * 
 * @author FBSir
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 权限校验异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public AjaxResult handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限校验失败'{}'", requestURI, e.getMessage());
        return AjaxResult.error(HttpStatus.FORBIDDEN, "没有权限，请联系管理员授权");
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<AjaxResult> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return ResponseEntity.status(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED)
                .body(AjaxResult.error(HttpStatus.BAD_METHOD, e.getMessage()));
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',业务异常: {}", requestURI, e.getMessage());
        Integer code = e.getCode();
        return StringUtils.isNotNull(code) ? AjaxResult.error(code, e.getMessage()) : AjaxResult.error(e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public AjaxResult handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        String value = Convert.toStr(e.getValue());
        if (StringUtils.isNotEmpty(value))
        {
            value = EscapeUtil.clean(value);
        }
        log.error("请求参数类型不匹配'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'", e.getName(), e.getRequiredType().getName(), value));
    }

    /**
     * 请求体为空或格式错误（Fail-Closed: 400）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AjaxResult> handleHttpMessageNotReadable(HttpMessageNotReadableException e)
    {
        log.error("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                .body(AjaxResult.error("请求体不能为空或格式错误"));
    }

    /**
     * 缺少必需的请求参数（Fail-Closed: 400）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<AjaxResult> handleMissingServletRequestParameter(MissingServletRequestParameterException e)
    {
        log.error("缺少必需参数: {}", e.getMessage());
        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                .body(AjaxResult.error(String.format("缺少必需参数[%s]", e.getParameterName())));
    }

    /**
     * 静态资源或未映射地址不存在。404 属于正常客户端结果，不能被兜底处理器
     * 伪装成 200/500，也不应在每次可用性探测时污染错误日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<AjaxResult> handleNoResourceFoundException(NoResourceFoundException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.debug("请求资源不存在: '{}'", requestURI);
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(AjaxResult.error(HttpStatus.NOT_FOUND, "请求资源不存在"));
    }

    /**
     * 未启用静态资源处理器的 MVC 配置会用 NoHandlerFoundException 表示未映射地址。
     * 其传输状态和响应体必须与 NoResourceFoundException 保持一致。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<AjaxResult> handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request)
    {
        log.debug("请求地址未映射: '{}'", request.getRequestURI());
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(AjaxResult.error(HttpStatus.NOT_FOUND, "请求资源不存在"));
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        // 只记录错误消息，不打印完整堆栈（简化日志）
        log.error("请求地址'{}',发生运行时异常: {}", requestURI, e.getMessage());
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        // 只记录错误消息，不打印完整堆栈（简化日志）
        log.error("请求地址'{}',发生系统异常: {}", requestURI, e.getMessage());
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e)
    {
        String message = e.getAllErrors().get(0).getDefaultMessage();
        log.error("参数验证异常: {}", message);
        return AjaxResult.error(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        log.error("参数验证异常: {}", message);
        return AjaxResult.error(message);
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public AjaxResult handleDemoModeException(DemoModeException e)
    {
        return AjaxResult.error("演示模式，不允许操作");
    }
}
