package com.cube.wechat.thirdapp.entiy;

import com.cube.common.core.domain.entity.SysUser;
import lombok.Data;

/**
 @author sjl
  * @Created date 2024/3/8 13:30
 */
@Data
public class LoginUser {
    private static ThreadLocal<LoginUser> userThreadLocal = new ThreadLocal<>();
    //用户系统id
    private String id;
    //企业微信UserId
    private String userId;
    //企业微信openUserId
    private String openUserid;
    //服务商应用id
    private String suiteId;
    //企业id
    private String corpId;

    private String openId;

    private SysUser user;

    public static LoginUser getCurrentUser() {
        return userThreadLocal.get();
    }

    public static void setCurrentUser(LoginUser user) {
        userThreadLocal.set(user);
    }

    public static void clearCurrentUser() {
        userThreadLocal.remove();
    }
}
