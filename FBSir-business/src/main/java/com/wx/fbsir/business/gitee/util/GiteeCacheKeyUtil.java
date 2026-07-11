package com.wx.fbsir.business.gitee.util;

/**
 * Gitee缓存Key工具类
 *
 * @author FBSir
 * @date 2026-01-03
 */
public final class GiteeCacheKeyUtil {
    /** Gitee访问令牌缓存前缀 */
    public static final String ACCESS_TOKEN_KEY_PREFIX = "gitee:access:token:";
    /** 授权state缓存前缀 */
    public static final String AUTH_STATE_KEY_PREFIX = "gitee:auth:state:";

    private GiteeCacheKeyUtil() {
    }

    /**
     * 获取访问令牌缓存Key
     *
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String getAccessTokenKey(Long userId) {
        return ACCESS_TOKEN_KEY_PREFIX + userId;
    }

    /**
     * 获取授权state缓存Key
     *
     * @param state state值
     * @return 缓存Key
     */
    public static String getAuthStateKey(String state) {
        return AUTH_STATE_KEY_PREFIX + state;
    }
}
