package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.WecomCliResult;
import com.wx.fbsir.business.fbs.service.WecomCliService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * wecom-cli 命令执行服务实现
 *
 * <p>核心约束：不写日志。日志由上层 WecomSyncService 统一负责。</p>
 *
 * @author wxfbsir
 * @date 2026-04-13
 */
@Service
public class WecomCliServiceImpl implements WecomCliService {

    @Value("${wecom.cli.path:}")
    private String cliPath;

    @Value("${wecom.cli.timeout-ms:30000}")
    private long timeoutMs;

    /** 需要重试的错误码前缀 */
    private static final String NET_ERROR_PREFIX = "NET_";

    /** 重试延迟（毫秒） */
    private static final long RETRY_DELAY_MS = 500;

    /** 最大重试次数（首次 + 1 次重试） */
    private static final int MAX_ATTEMPTS = 2;

    @Override
    public WecomCliResult execute(String category, String method, String params) {
        // 运行时检测 CLI 文件存在性（不阻塞启动）
        if (!isAvailable()) {
            return WecomCliResult.fail("CLI_NOT_FOUND",
                    "wecom-cli 不存在: " + cliPath, -1, 0);
        }

        WecomCliResult lastResult = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            lastResult = doExecute(category, method, params);

            // 成功则直接返回
            if (lastResult.isSuccess()) {
                return lastResult;
            }

            // 仅 NET_ 错误重试，其他错误不重试
            String errorCode = lastResult.getErrorCode();
            if (errorCode != null && errorCode.startsWith(NET_ERROR_PREFIX) && attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return WecomCliResult.fail("EXEC_INTERRUPTED", "重试被中断", -1, lastResult.getDurationMs());
                }
                continue;
            }

            // 非网络错误或已达最大重试次数，直接返回
            return lastResult;
        }

        return lastResult;
    }

    @Override
    public boolean isAvailable() {
        return cliPath != null && !cliPath.trim().isEmpty()
                && new File(cliPath).exists();
    }

    @Override
    public String getCliPath() {
        return cliPath != null ? cliPath : "";
    }

    /**
     * 实际执行 wecom-cli 命令（单次尝试）
     * package-private 以便单元测试 spy
     */
    WecomCliResult doExecute(String category, String method, String params) {
        long startMs = System.currentTimeMillis();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    cliPath, category, method, escapeForWindows(params));
            pb.redirectErrorStream(true);  // 合并 stderr 到 stdout
            pb.directory(null);             // 继承工作目录
            // shell=false 是默认值，不显式设置（安全最佳实践）

            Process process = pb.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                // 超时：强制销毁进程
                process.destroyForcibly();
                long durationMs = System.currentTimeMillis() - startMs;
                return WecomCliResult.fail("EXEC_TIMEOUT",
                        "wecom-cli 执行超时 (" + timeoutMs + "ms)", -1, durationMs);
            }

            int exitCode = process.exitValue();
            String rawOutput = readOutput(process);
            long durationMs = System.currentTimeMillis() - startMs;

            if (exitCode != 0) {
                // 进程返回非零退出码
                String errorCode = detectErrorCode(rawOutput, exitCode);
                return WecomCliResult.fail(errorCode, "wecom-cli exit code: " + exitCode,
                        exitCode, durationMs);
            }

            return WecomCliResult.success(rawOutput, durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            String errorCode = "UNKNOWN";
            if (e instanceof InterruptedException) {
                errorCode = "EXEC_INTERRUPTED";
                Thread.currentThread().interrupt();
            } else if (e.getMessage() != null && e.getMessage().contains("Cannot run program")) {
                errorCode = "CLI_NOT_FOUND";
            }
            return WecomCliResult.fail(errorCode, e.getMessage(), -1, durationMs);
        }
    }

    /**
     * 读取进程 stdout
     */
    private String readOutput(Process process) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line);
            }
        } catch (Exception e) {
            // 读取失败不影响主流程
        }
        return sb.toString();
    }

    /**
     * Windows CreateProcessW 参数转义。
     *
     * <p>ProcessBuilder 在 Windows 上将参数拼接为命令行字符串时，
     * 对含空格的参数会加双引号，但不会转义参数内部的 "。
     * 当参数本身是 JSON（含大量 "）时，Windows 解析器会提前截断参数边界。
     *
     * <p>标准做法：将每个 " 转义为 \"（反斜杠 + 双引号）。
     * 仅在 Windows 平台生效，Linux/macOS 无需转义。
     */
    private static String escapeForWindows(String arg) {
        if (arg == null) return "";
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return arg;
        }
        return arg.replace("\"", "\\\"");
    }

    /**
     * 根据原始输出检测错误码
     */
    private String detectErrorCode(String rawOutput, int exitCode) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return "CLI_ERROR";
        }
        String upper = rawOutput.toUpperCase();
        if (upper.contains("NET_TIMEOUT") || upper.contains("TIMEOUT")) {
            return "NET_TIMEOUT";
        }
        if (upper.contains("NET_CONNECTION") || upper.contains("CONNECTION_REFUSED")
                || upper.contains("CONNECTION_FAILED")) {
            return "NET_CONNECTION_FAILED";
        }
        if (upper.contains("AUTH") || upper.contains("UNAUTHORIZED")
                || upper.contains("LOGIN") || upper.contains("扫码")) {
            return "AUTH_REQUIRED";
        }
        return "CLI_ERROR";
    }
}
