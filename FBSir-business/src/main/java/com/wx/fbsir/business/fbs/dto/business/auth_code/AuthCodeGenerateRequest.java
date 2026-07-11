package com.wx.fbsir.business.fbs.dto.business.auth_code;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 授权码生成请求
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class AuthCodeGenerateRequest {

    /** 关联目标类型：SCENE_PACK/GENERIC */
    private String targetType;

    /** 关联目标ID（如场景包ID） */
    private Long targetId;

    /** 关联场景包编码（前端传入，后端据此查 targetId，优先于 targetId） */
    private String targetPackCode;

    /** 发放者类型：1=平台, 2=企业, 3=用户 */
    private Integer issuerType;

    /** 发放者ID */
    private Long issuerId;

    /** 最大激活次数（默认1） */
    private Integer maxActivations;

    /** 截止时间，NULL=不限 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deadline;

    /** 说明/备注 */
    private String description;

    /** 生成数量（批量生成） */
    private Integer count;

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getTargetPackCode() { return targetPackCode; }
    public void setTargetPackCode(String targetPackCode) { this.targetPackCode = targetPackCode; }

    public Integer getIssuerType() { return issuerType; }
    public void setIssuerType(Integer issuerType) { this.issuerType = issuerType; }

    public Long getIssuerId() { return issuerId; }
    public void setIssuerId(Long issuerId) { this.issuerId = issuerId; }

    public Integer getMaxActivations() { return maxActivations; }
    public void setMaxActivations(Integer maxActivations) { this.maxActivations = maxActivations; }

    public Date getDeadline() { return deadline; }
    public void setDeadline(Date deadline) { this.deadline = deadline; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}
