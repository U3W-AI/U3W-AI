package com.wx.fbsir.engine.playwright.util;

import com.wx.fbsir.engine.config.EngineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 截图上传HTTP客户端
 * 
 * 用于将Playwright截图上传到Admin服务器并获取访问链接
 *
 * @author wxfbsir
 * @date 2025-12-19
 */
@Component
public class ScreenshotUploadClient {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotUploadClient.class);

    private final EngineProperties properties;

    /**
     * 上传超时时间（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    /**
     * Admin服务器基础URL
     */
    private String adminBaseUrl;

    public ScreenshotUploadClient(EngineProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        // 从WebSocket URL推导HTTP URL，保留路径前缀
        String wsUrl = properties.getWsUrl();

        
        String protocol;
        String urlWithoutProtocol;
        
        if (wsUrl.startsWith("ws://")) {
            protocol = "http://";
            urlWithoutProtocol = wsUrl.substring(5); // 移除 "ws://"
        } else if (wsUrl.startsWith("wss://")) {
            protocol = "https://";
            urlWithoutProtocol = wsUrl.substring(6); // 移除 "wss://"
        } else {
            adminBaseUrl = "http://localhost:8080";
            log.info("[截图上传] 初始化完成 - Admin地址: {}", adminBaseUrl);
            return;
        }
        
        // 移除WebSocket特定的路径部分 (/ws/engine)
        // 例如: wx.fbsir.com/fbsir/ws/engine -> wx.fbsir.com/fbsir
        int wsPathIndex = urlWithoutProtocol.indexOf("/ws/");
        if (wsPathIndex > 0) {
            // 保留 /ws/ 之前的所有路径
            adminBaseUrl = protocol + urlWithoutProtocol.substring(0, wsPathIndex);
        } else {
            // 如果没有 /ws/ 路径，只保留域名
            int firstSlash = urlWithoutProtocol.indexOf('/');
            if (firstSlash > 0) {
                adminBaseUrl = protocol + urlWithoutProtocol.substring(0, firstSlash);
            } else {
                adminBaseUrl = protocol + urlWithoutProtocol;
            }
        }
        
        log.info("[截图上传] 初始化完成 - Admin地址: {}", adminBaseUrl);
    }

    /**
     * 上传截图（字节数组）
     * 
     * @param userId 用户ID
     * @param fileName 文件名（可选，不含扩展名）
     * @param imageBytes 图片字节数组
     * @return 上传结果，包含url等信息
     */
    public UploadResult uploadScreenshot(String userId, String fileName, byte[] imageBytes) {
        String hostId = properties.getHostId();
        String uploadUrl = adminBaseUrl + "/engine/screenshot/upload";
        
        try {
            String boundary = "----" + UUID.randomUUID().toString().replace("-", "");
            
            URL url = new URL(uploadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            try (OutputStream os = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {
                
                // hostId参数
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"hostId\"\r\n\r\n");
                writer.append(hostId).append("\r\n");
                
                // userId参数
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"userId\"\r\n\r\n");
                writer.append(userId).append("\r\n");
                
                // fileName参数（可选）
                if (fileName != null && !fileName.isEmpty()) {
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"fileName\"\r\n\r\n");
                    writer.append(fileName).append("\r\n");
                }
                
                // 文件内容
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"screenshot.png\"\r\n");
                writer.append("Content-Type: image/png\r\n\r\n");
                writer.flush();
                
                os.write(imageBytes);
                os.flush();
                
                writer.append("\r\n");
                writer.append("--").append(boundary).append("--\r\n");
                writer.flush();
            }
            
            int responseCode = conn.getResponseCode();
            String responseBody = readResponse(conn);
            
            if (responseCode == 200) {
                return parseResponse(responseBody);
            } else {
                log.error("[截图上传] HTTP错误 - 状态码: {}, 响应: {}", responseCode, responseBody);
                return UploadResult.failure("HTTP错误: " + responseCode);
            }
            
        } catch (Exception e) {
            log.error("[截图上传] 上传失败 - 用户: {}, 错误: {}", userId, e.getMessage());
            return UploadResult.failure("上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传截图（Base64编码）
     */
    public UploadResult uploadScreenshotBase64(String userId, String fileName, String base64Image) {
        byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
        return uploadScreenshot(userId, fileName, imageBytes);
    }

    /**
     * 读取HTTP响应
     */
    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is;
        try {
            is = conn.getInputStream();
        } catch (IOException e) {
            is = conn.getErrorStream();
        }
        
        if (is == null) {
            return "";
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * 解析响应JSON
     */
    private UploadResult parseResponse(String json) {
        try {
            // 简单JSON解析（避免引入额外依赖）
            if (json.contains("\"success\":true") || json.contains("\"success\": true")) {
                String url = extractJsonValue(json, "url");
                String fileName = extractJsonValue(json, "fileName");
                return UploadResult.success(url, fileName);
            } else {
                String message = extractJsonValue(json, "message");
                log.warn("[截图上传] 上传失败 - {}", message != null ? message : "未知错误");
                return UploadResult.failure(message != null ? message : "上传失败");
            }
        } catch (Exception e) {
            log.error("[截图上传] 解析响应异常 - {}", e.getMessage(), e);
            return UploadResult.failure("解析响应失败: " + e.getMessage());
        }
    }

    /**
     * 简单提取JSON值（精确匹配 key）
     */
    private String extractJsonValue(String json, String key) {
        // 精确匹配 "key":"value" 或 "key": "value"
        String pattern1 = "\"" + key + "\":\"";
        String pattern2 = "\"" + key + "\": \"";
        
        int start = -1;
        int patternLen = 0;
        
        // 尝试两种模式
        start = json.indexOf(pattern1);
        if (start >= 0) {
            patternLen = pattern1.length();
        } else {
            start = json.indexOf(pattern2);
            if (start >= 0) {
                patternLen = pattern2.length();
            }
        }
        
        if (start < 0) {
            return null;
        }
        
        // 确保前面是 { 或 ,（避免匹配到值中的内容）
        if (start > 0) {
            char before = json.charAt(start - 1);
            if (before != '{' && before != ',' && before != ' ' && before != '\n') {
                // 不是有效的 JSON 键，继续查找
                int nextStart = json.indexOf(pattern1, start + 1);
                if (nextStart < 0) {
                    nextStart = json.indexOf(pattern2, start + 1);
                }
                if (nextStart < 0) {
                    return null;
                }
                start = nextStart;
            }
        }
        
        start += patternLen;
        
        // 查找结束引号
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"') {
                // 检查是否是转义的引号
                if (end > 0 && json.charAt(end - 1) == '\\') {
                    end++;
                    continue;
                }
                break;
            }
            end++;
        }
        
        if (end >= json.length()) {
            return null;
        }
        
        return json.substring(start, end);
    }

    /**
     * 上传结果
     */
    public static class UploadResult {
        private final boolean success;
        private final String url;
        private final String fileName;
        private final String errorMessage;

        private UploadResult(boolean success, String url, String fileName, String errorMessage) {
            this.success = success;
            this.url = url;
            this.fileName = fileName;
            this.errorMessage = errorMessage;
        }

        public static UploadResult success(String url, String fileName) {
            return new UploadResult(true, url, fileName, null);
        }

        public static UploadResult failure(String errorMessage) {
            return new UploadResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getUrl() {
            return url;
        }

        public String getFileName() {
            return fileName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (success) {
                return "UploadResult{success=true, url='" + url + "', fileName='" + fileName + "'}";
            } else {
                return "UploadResult{success=false, error='" + errorMessage + "'}";
            }
        }
    }
}
