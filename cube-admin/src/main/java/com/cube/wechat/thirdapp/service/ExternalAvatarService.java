package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;

import java.util.Map;

/**
 * @author 张云龙
 */
public interface ExternalAvatarService {

    R<String> getExternalAvatar(String externalUserId);
    String getExternalAvatar(String externalUserId,String corpId);

    R qrtzExternalAvatar(Map<String, String> paramMap);

    R selectUserNameByUserIds();
}
