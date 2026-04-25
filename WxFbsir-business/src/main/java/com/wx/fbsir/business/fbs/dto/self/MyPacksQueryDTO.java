package com.wx.fbsir.business.fbs.dto.self;

import java.io.Serializable;

/**
 * 我的权益查询条件DTO
 *
 * @author wxfbsir
 * @date 2026-04-10
 */
public class MyPacksQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码（默认1） */
    private Integer pageNum = 1;

    /** 每页条数（默认10） */
    private Integer pageSize = 10;

    /** 权益状态过滤（1=有效, 2=已过期, 3=已撤销，可为null表示全部） */
    private Integer status;

    /** 场景包ID过滤（可为null） */
    private Long packId;

    /** 来源类型过滤（1=平台分发, 2=企业分发, 3=用户激活，可为null表示全部） */
    private Integer sourceType;

    // ========== getter / setter ==========

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }
}
