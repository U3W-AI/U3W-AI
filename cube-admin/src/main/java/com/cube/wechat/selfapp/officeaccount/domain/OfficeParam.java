package com.cube.wechat.selfapp.officeaccount.domain;

import com.alibaba.fastjson.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年08月14日 09:32
 */
public class OfficeParam {
    private static final Map<String, String> subscribeSceneMap = new HashMap<>();

    static {
        subscribeSceneMap.put("ADD_SCENE_SEARCH", "公众号搜索");
        subscribeSceneMap.put("ADD_SCENE_ACCOUNT_MIGRATION", "公众号迁移");
        subscribeSceneMap.put("ADD_SCENE_PROFILE_CARD", "名片分享");
        subscribeSceneMap.put("ADD_SCENE_QR_CODE", "扫描二维码");
        subscribeSceneMap.put("ADD_SCENE_PROFILE_LINK", "图文页内名称点击");
        subscribeSceneMap.put("ADD_SCENE_PROFILE_ITEM", "图文页右上角菜单");
        subscribeSceneMap.put("ADD_SCENE_PAID", "支付后关注");
        subscribeSceneMap.put("ADD_SCENE_WECHAT_ADVERTISEMENT", "微信广告");
        subscribeSceneMap.put("ADD_SCENE_REPRINT", "他人转载");
        subscribeSceneMap.put("ADD_SCENE_LIVESTREAM", "视频号直播");
        subscribeSceneMap.put("ADD_SCENE_CHANNELS", "视频号");
        subscribeSceneMap.put("ADD_SCENE_WXA", "小程序关注");
        subscribeSceneMap.put("ADD_SCENE_OTHERS", "其他");
    }
    public static void updateSubscribeScene(JSONObject fansInfoRes) {
        String oldScene = fansInfoRes.getString("subscribe_scene");
        String newScene = subscribeSceneMap.getOrDefault(oldScene, oldScene);
        fansInfoRes.put("subscribe_scene", newScene);
    }


}
