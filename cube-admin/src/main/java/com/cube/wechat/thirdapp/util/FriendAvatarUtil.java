package com.cube.wechat.thirdapp.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.cube.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;

/**
 * @author 张云龙
 * 获取好友头像
 */
@Slf4j
public class FriendAvatarUtil {


    /**
     * 根据外部联系人id获取头像
     * @param accessToken
     * @param externalUserId
     * @return
     */
    public static String getExternalAvatar(String accessToken, String externalUserId) {

        String url = String.format(ZQUtil.getExternalAvatar, externalUserId, accessToken);
        String externalAvatarStr = HttpUtil.get(url);
        Map externalAvatarMap = JSON.parseObject(externalAvatarStr, Map.class);

        String avatarUrl = MapUtils.getString(externalAvatarMap, "data");
        return avatarUrl;
    }



    /**
     * 根据外部联系人id获取头像
     * @param accessToken
     * @param externalUserId
     * @return
     */
    public static String getExternalAvatarByCorpIdAndExternalUserId(String accessToken, String externalUserId,String corpId) {

        String url = String.format(ZQUtil.getExternalAvatarByCorpIdAndExternalUserId, externalUserId, corpId,accessToken);
        String externalAvatarStr = HttpUtil.get(url);
        Map externalAvatarMap = JSON.parseObject(externalAvatarStr, Map.class);
        String avatarUrl = MapUtils.getString(externalAvatarMap, "data");
        return avatarUrl;
    }



    /**
     * 获取第三方登录Token
     *
     * @return
     */
    public static String getAccessToken() {
        String url = ZQUtil.getExternalAccessToken;
        HttpResponse httpResponse = HttpRequest.post(url).execute();
        Map resultMap = JSON.parseObject(httpResponse.body(), Map.class);
        if (MapUtils.getInteger(resultMap, "code") != R.SUCCESS && !MapUtils.getBoolean(resultMap, "success")) {
            log.error("获取获取第三方登录Token失败，失败原因:" + JSON.toJSONString(resultMap));
            return null;
        }
        return MapUtils.getString(resultMap, "data");
    }


}
