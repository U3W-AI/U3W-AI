package com.wx.fbsir.business.fbs.dto.business.scene_pack;

/**
 * 场景包分页请求
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class ScenePackPageRequest {

    /** 状态：0=草稿, 1=已发布, 2=已下架 */
    private Integer status;

    /** 场景包类型：1=平台包, 2=企业包, 3=自定义包 */
    private Integer packType;

    /** 所属者类型：1=平台, 2=企业, 3=个人 */
    private Integer ownerType;

    /** 页码（由BaseController startPage自动注入） */
    private Integer pageNum;

    /** 每页大小（由BaseController startPage自动注入） */
    private Integer pageSize;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getPackType() { return packType; }
    public void setPackType(Integer packType) { this.packType = packType; }

    public Integer getOwnerType() { return ownerType; }
    public void setOwnerType(Integer ownerType) { this.ownerType = ownerType; }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
