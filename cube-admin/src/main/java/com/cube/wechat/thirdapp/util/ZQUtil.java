package com.cube.wechat.thirdapp.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;

/**
 * @author 张云龙
 *
 * 获取三方接口   增强组件
 */
public class ZQUtil {
    private static final String baseUrl = "https://qunfa.tengxinkeji.com.cn/";
    //获取第三方登录Token
    public static final String getExternalAccessToken  = baseUrl+"third/user/login?accountNum=itth&password=LNd56ZKUp2@198";

    //根据外部联系人id获取头像
    public static final String getExternalAvatar = baseUrl+"third/external/avatar?externalUserId=%s&token=%s";

    //根据外部联系人id和corpId 实时获取头像
    public static final String getExternalAvatarByCorpIdAndExternalUserId = baseUrl+"third/external/getExternaleAvatarByExternalUserId?externalUserId=%s&corpId=%s&token=%s";

    //根据openUserIds获取用户名称
    public static final String getNameByOpenUserIds = "http://10.0.1.7:8001/self/getNameByUserIds";

    public static List<Map> getNameByOpenUserIds(List<String> openUserIds){

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userIds",openUserIds);

        Map resultMap = RestUtilsTwo.post(ZQUtil.getNameByOpenUserIds, jsonObject);
        List<Map> userInfoMaps = (List<Map>) MapUtils.getObject(resultMap, "data");
        return userInfoMaps;
    }

}
