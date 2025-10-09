package com.cube.common.utils;

import com.cube.common.entity.UserSimpleInfo;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/9 13:35
 */
public class ThreadUserInfo {
    public static ThreadLocal<UserSimpleInfo> userInfo = new ThreadLocal<>();

    public static UserSimpleInfo getUserInfo() {
        return userInfo.get();
    }
    public static void setUserInfo(UserSimpleInfo userSimpleInfo) {
        ThreadUserInfo.userInfo.set(userSimpleInfo);
    }
    public static void removeUserInfo() {
        userInfo.remove();
    }
}
