package com.wx.fbsir.business.fbs.dto.self;

import java.io.Serializable;

/**
 * 可领取场景包查询条件DTO
 *
 * @author wxfbsir
 * @date 2026-04-10
 */
public class MyScenePacksQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码（默认1） */
    private Integer pageNum = 1;

    /** 每页条数（默认10，最大100） */
    private Integer pageSize = 10;

    /** 搜索关键字（packName/packCode 模糊） */
    private String keyword;

    // ========== getter / setter ==========

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() {
        if (pageSize == null || pageSize > 100) {
            return 100;
        }
        return pageSize;
    }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
