package com.wx.fbsir.business.websocket.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * WebSocket IP黑名单实体
 *
 * @author FBSir
 * @date 2025-12-15
 */
public class WsIpBlacklist implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String ipAddress;
    private String blockReason;
    private Integer blockType;
    private LocalDateTime expireTime;
    private Integer hitCount;
    private LocalDateTime lastHitTime;
    private Integer status;
    private String remark;
    private Integer delFlag;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    public Integer getBlockType() { return blockType; }
    public void setBlockType(Integer blockType) { this.blockType = blockType; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public LocalDateTime getLastHitTime() { return lastHitTime; }
    public void setLastHitTime(LocalDateTime lastHitTime) { this.lastHitTime = lastHitTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
