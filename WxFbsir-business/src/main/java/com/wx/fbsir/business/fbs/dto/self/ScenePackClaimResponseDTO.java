package com.wx.fbsir.business.fbs.dto.self;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 领取场景包响应DTO
 *
 * @author wxfbsir
 * @date 2026-04-10
 */
public class ScenePackClaimResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 场景包ID */
    private Long packId;

    /** 场景包名称 */
    private String packName;

    /**
     * 过期时间（null=永不过期）
     * <p>MVP 阶段固定为 null，暂不设置过期时间。</p>
     */
    private LocalDateTime expiresAt;

    /** 消息（成功/幂等信息） */
    private String msg;

    // ========== getter / setter ==========

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
}
