package com.wx.fbsir.business.certificate.util;

/**
 * 认证申请状态工具类
 * 定义统一的状态码及其含义
 *
 * @author FBSir
 */
public class ApplicationStatusUtil {
    
    // 状态码定义 - 使用数字代码以兼容数据库char(1)字段
    public static final String DRAFT = "0";        // 草稿
    public static final String PENDING_REVIEW = "1"; // 待审核 (Pending Review)
    public static final String APPROVED = "2";     // 已批准 (Approved)
    public static final String REJECTED = "3";     // 已驳回 (Rejected)
    public static final String ISSUED = "4";       // 已发放
    
    /**
     * 获取状态描述
     */
    public static String getStatusDescription(String statusCode) {
        switch (statusCode) {
            case DRAFT:
                return "草稿";
            case PENDING_REVIEW:
                return "待审核";
            case APPROVED:
                return "已批准";
            case REJECTED:
                return "已驳回";
            case ISSUED:
                return "已发放";
            default:
                return "未知状态";
        }
    }
    
    /**
     * 检查是否为终态（不可再变更的状态）
     */
    public static boolean isFinalState(String statusCode) {
        return APPROVED.equals(statusCode) || REJECTED.equals(statusCode) || ISSUED.equals(statusCode);
    }
    
    /**
     * 检查是否为活跃状态（需要进一步处理的状态）
     */
    public static boolean isActiveState(String statusCode) {
        return PENDING_REVIEW.equals(statusCode);
    }
}