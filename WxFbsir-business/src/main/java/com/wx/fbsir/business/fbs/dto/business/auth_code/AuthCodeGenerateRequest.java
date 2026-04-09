package com.wx.fbsir.business.fbs.dto.business.auth_code;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 鎺堟潈鐮佺敓鎴愯姹?
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class AuthCodeGenerateRequest {

    /** 鍏宠仈鐩爣绫诲瀷锛歋CENE_PACK/GENERIC */
    private String targetType;

    /** 鍏宠仈鐩爣ID锛堝鍦烘櫙鍖匢D锛?*/
    private Long targetId;

    /** 鍙戞斁鑰呯被鍨嬶細1=骞冲彴, 2=浼佷笟, 3=鐢ㄦ埛 */
    private Integer issuerType;

    /** 鍙戞斁鑰匢D */
    private Long issuerId;

    /** 鏈€澶ф縺娲绘鏁帮紙榛樿1锛?*/
    private Integer maxActivations;

    /** 鎴鏃堕棿锛孨ULL=涓嶉檺 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deadline;

    /** 璇存槑/澶囨敞 */
    private String description;

    /** 鐢熸垚鏁伴噺锛堟壒閲忕敓鎴愶級 */
    private Integer count;

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

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
