package com.tencent.wework;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @usage 输出日志到stdout，仅供参考，非必须
 * @format {datetime},{log_level},{reqid},{filename},{line_number},{msg}
 *          datetime: %Y-%d-%m %H:%M:%S 格式，不含毫秒
 *          log_level: INFO/ERROR/DEBUG（提供对应的日志接口名）
 *          reqid: 请求id
 *          file_name: 打日志的文件名
 *          line_number: 行号
 *          msg: 自定义的数据
 */
public class LogUtil {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static enum LogLevel {
        INFO,
        ERROR,
        DEBUG
    }

    private static Map<LogLevel, String> logFormat = new HashMap<LogLevel, String>(){{
        put(LogLevel.INFO, "\033[32m%s,%s,%s,%s,%d,\033[0m%s");
        put(LogLevel.ERROR, "\033[31m%s,%s,%s,%s,%d,\033[0m%s");
        put(LogLevel.DEBUG, "\033[33m%s,%s,%s,%s,%d,\033[0m%s");
    }};

    public static enum ErrorCode {
        SUCCESS,
        FUNC_CALL_ERROR,
        TOO_FEW_ARGS_ERROR,
        UNSUPPORTED_LOG_LEVEL
    }

    public static ErrorCode CommLog(LogLevel logLevel, String reqId, String... args) {
        if (args.length < 1) {
            CommLog(LogLevel.ERROR, null, "commlog too few args");
            return ErrorCode.TOO_FEW_ARGS_ERROR;
        }

        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length < 4) {
            CommLog(LogLevel.ERROR, null, "commlog stacktrace length < 4");
            return ErrorCode.FUNC_CALL_ERROR;
        }
        StackTraceElement element = stackTraceElements[3];

        System.out.println(
            String.format(logFormat.get(logLevel),
                dateFormat.format(new Date()),
                logLevel,
                reqId,
                element.getFileName(),
                element.getLineNumber(),
                String.join(",", args)
            )
        );

        return ErrorCode.SUCCESS;
    }

    public static ErrorCode LogInfo(String reqId, String... args) {
        return CommLog(LogLevel.INFO, reqId, args);
    }

    public static ErrorCode LogError(String reqId, String... args) {
        return CommLog(LogLevel.ERROR, reqId, args);
    }

    public static ErrorCode LogDebug(String reqId, String... args) {
        return CommLog(LogLevel.DEBUG, reqId, args);
    }
}
