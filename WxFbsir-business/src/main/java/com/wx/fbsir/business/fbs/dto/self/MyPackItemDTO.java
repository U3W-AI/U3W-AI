package com.wx.fbsir.business.fbs.dto.self;

import java.io.Serializable;
import java.util.Date;

/**
 * 我的权益列表项DTO
 *
 * @author wxfbsir
 * @date 2026-04-10
 */
public class MyPackItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 权益记录ID */
    private Long id;

    /** 场景包ID */
    private Long packId;

    /** 场景包名称 */
    private String packName;

    /** 场景包编码 */
    private String packCode;

    /** 激活时版本号 */
    private String packVersion;

    /** 授权码ID（sourceType=3时有值） */
    private Long authCodeId;

    /** 激活时间 */
    private Date activatedAt;

    /** 过期时间（null=永不过期） */
    private Date expiresAt;

    /** 来源类型：1=平台分发, 2=企业分发, 3=用户激活 */
    private Integer sourceType;

    /** 权益状态：1=有效, 2=已过期, 3=已撤销 */
    private Integer status;

    /** 场景包状态：1=上架, 0=草稿, 2=已下架（null=场景包已删除） */
    private Integer packStatus;

    /** 创建时间 */
    private Date createTime;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getPackVersion() { return packVersion; }
    public void setPackVersion(String packVersion) { this.packVersion = packVersion; }

    public Long getAuthCodeId() { return authCodeId; }
    public void setAuthCodeId(Long authCodeId) { this.authCodeId = authCodeId; }

    public Date getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Date activatedAt) { this.activatedAt = activatedAt; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getPackStatus() { return packStatus; }
    public void setPackStatus(Integer packStatus) { this.packStatus = packStatus; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    // ========== 枚举描述方法 ==========

    /** 来源类型描述 */
    public String getSourceTypeDesc() {
        if (sourceType == null) return "未知";
        switch (sourceType) {
            case 1: return "平台分发";
            case 2: return "企业分发";
            case 3: return "用户激活";
            default: return "未知";
        }
    }

    /** 权益状态描述（综合考虑权益状态和场景包状态） */
    public String getStatusDesc() {
        // 场景包已下架/已删除时，优先显示
        if (packStatus == null) return "场景包已删除";
        if (packStatus != 1 && status != null && status == 1) return "场景包已下架";
        if (status == null) return "未知";
        switch (status) {
            case 1: return "有效";
            case 2: return "已过期";
            case 3: return "已撤销";
            default: return "未知";
        }
    }
}
