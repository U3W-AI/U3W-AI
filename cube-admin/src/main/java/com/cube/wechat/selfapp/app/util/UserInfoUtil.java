package com.cube.wechat.selfapp.app.util;

import com.cube.wechat.selfapp.app.service.AIGCService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author 孔德权
 * description:  TODO
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/23 11:12
 */
@Component
@RequiredArgsConstructor
public class UserInfoUtil {
    private final AIGCService aigcService;

    public String getUserIdByUnionId(String unionId) {
        if (unionId == null || unionId.trim().isEmpty()) {
            return null;
        }
        String userId = aigcService.getUserIdByUnionId(unionId);
        if(userId != null) {
            return userId;
        }
        return null;
    }
}
