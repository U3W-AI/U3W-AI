package com.wx.fbsir.business.fbs.dto.business.user_pack;

/**
 * 用户-场景包分页请求
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class UserPackPageRequest {

    /** 用户ID */
    private Long userId;

    /** 权益状态：1=有效, 2=已过期, 3=已撤销 */
    private Integer status;

    /** 页码（由BaseController startPage自动注入） */
    private Integer pageNum;

    /** 每页大小（由BaseController startPage自动注入） */
    private Integer pageSize;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
