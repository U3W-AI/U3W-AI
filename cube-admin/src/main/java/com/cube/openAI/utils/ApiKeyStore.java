package com.cube.openAI.utils;

import com.cube.wechat.selfapp.app.util.UserInfoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiKeyStore {
    private final UserInfoUtil userInfoUtil;
    // 验证密钥是否有效
    public String isValid(String apiKey) {
        try {
            String userId = userInfoUtil.getUserIdByUnionId(apiKey);
            if(userId != null && !userId.isEmpty()) {
                return userId;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}