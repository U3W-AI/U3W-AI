package com.cube.openAI.interceptor;

import com.cube.common.entity.UserSimpleInfo;
import com.cube.openAI.utils.ApiKeyStore;
import com.cube.openAI.utils.ThreadUserInfo;
import com.cube.wechat.selfapp.app.mapper.SysHostWhitelistMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/9 13:46
 */
@Component
public class OpenAiApiKeyInterceptor implements HandlerInterceptor {

    private final ApiKeyStore apiKeyStore;
    private final ObjectMapper objectMapper;
    private final SysHostWhitelistMapper whitelistMapper;

    public OpenAiApiKeyInterceptor(ApiKeyStore apiKeyStore, ObjectMapper objectMapper,SysHostWhitelistMapper whitelistMapper) {
        this.apiKeyStore = apiKeyStore;
        this.objectMapper = objectMapper;
        this.whitelistMapper = whitelistMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 2. 获取Authorization请求头
        String authHeader = request.getHeader("Authorization");

        // 3. 校验请求头格式（必须是Bearer + 空格 + 密钥）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "invalid_request_error",
                    "未提供有效的API密钥，请使用Authorization: Bearer <api-key>格式");
            return false;
        }

        // 4. 提取API密钥（去除Bearer前缀和空格）
        String apiKey = authHeader.substring("Bearer ".length()).trim();
        if (apiKey.isEmpty()) {
            sendErrorResponse(response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "invalid_request_error",
                    "API密钥不能为空");
            return false;
        }

        // 5. 校验密钥有效性
        String[] split = apiKey.split("-");
        if(split.length < 2) {
            sendErrorResponse(response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "invalid_api_key",
                    "提供的API密钥无效，请检查后重试");
            return false;
        }
        String cropId = split[0];
//        检查主机ID是否有效
        int i = whitelistMapper.selectActiveByHostId(cropId);
        String unionId = split[1];
        String userId = apiKeyStore.isValid(unionId);
        if (cropId == null || userId == null || i == 0) {
            sendErrorResponse(response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "invalid_api_key",
                    "提供的API密钥无效，请检查后重试");
            return false;
        }
        UserSimpleInfo userInfo = new UserSimpleInfo();
        userInfo.setCropId(cropId);
        userInfo.setUserId(userId);
        ThreadUserInfo.setUserInfo(userInfo);
        // 6. 校验通过，继续处理请求
        return true;
    }
    // 发送符合OpenAI规范的错误响应
    private void sendErrorResponse(HttpServletResponse response, int status, String type, String message) throws Exception {
        response.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("message", message);
        error.put("type", type);
        error.put("param", null);
        error.put("code", type);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", error);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ThreadUserInfo.removeUserInfo();
    }
}
