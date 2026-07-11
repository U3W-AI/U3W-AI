package com.wx.fbsir.business.point.domain;

/**
 * 积分操作结果封装类
 * 用于前置校验和异常处理
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public class PointsResult {
    /** 是否成功 */
    private boolean success;
    
    /** 错误码 */
    private String code;
    
    /** 错误消息 */
    private String msg;
    
    /** 积分变动值 */
    private Integer delta;
    
    /** 变动后余额 */
    private Integer balanceAfter;

    private PointsResult() {
    }

    /**
     * 创建成功结果
     */
    public static PointsResult ok(Integer delta, Integer balanceAfter) {
        PointsResult r = new PointsResult();
        r.success = true;
        r.delta = delta;
        r.balanceAfter = balanceAfter;
        return r;
    }

    /**
     * 创建失败结果
     */
    public static PointsResult fail(String code, String msg) {
        PointsResult r = new PointsResult();
        r.success = false;
        r.code = code;
        r.msg = msg;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public Integer getDelta() {
        return delta;
    }

    public Integer getBalanceAfter() {
        return balanceAfter;
    }
}

