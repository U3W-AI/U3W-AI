package com.wx.fbsir.business.fbs.dto.self;

import java.io.Serializable;

/**
 * 领取场景包请求DTO
 *
 * @author FBSir
 * @date 2026-04-10
 */
public class ScenePackClaimRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 场景包ID */
    private Long packId;

    // ========== getter / setter ==========

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }
}
