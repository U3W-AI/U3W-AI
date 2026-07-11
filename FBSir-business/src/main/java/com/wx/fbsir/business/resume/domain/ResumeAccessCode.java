package com.wx.fbsir.business.resume.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class ResumeAccessCode {

    /** 主键ID */
    private Long id;

    /** 简历访问短链接（关联 cv_storage.shortlink） */
    private String shortlink;

    /** 简历访问码 */
    private String accessCode;

    /** 可访问次数 */
    private Integer accessibleCount;

    /** 截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deadline;

    /** 是否启用：0-禁用，1-启用 */
    private Byte available;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;


    public ResumeAccessCode() {
    }

    public ResumeAccessCode(Long id, String shortlink, String accessCode, Integer accessibleCount, Date deadline, Byte available, Date createTime, Date updateTime) {
        this.id = id;
        this.shortlink = shortlink;
        this.accessCode = accessCode;
        this.accessibleCount = accessibleCount;
        this.deadline = deadline;
        this.available = available;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 获取
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置
     * @param id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取
     * @return shortlink
     */
    public String getShortlink() {
        return shortlink;
    }

    /**
     * 设置
     * @param shortlink
     */
    public void setShortlink(String shortlink) {
        this.shortlink = shortlink;
    }

    /**
     * 获取
     * @return accessCode
     */
    public String getAccessCode() {
        return accessCode;
    }

    /**
     * 设置
     * @param accessCode
     */
    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    /**
     * 获取
     * @return accessibleCount
     */
    public Integer getAccessibleCount() {
        return accessibleCount;
    }

    /**
     * 设置
     * @param accessibleCount
     */
    public void setAccessibleCount(Integer accessibleCount) {
        this.accessibleCount = accessibleCount;
    }

    /**
     * 获取
     * @return deadline
     */
    public Date getDeadline() {
        return deadline;
    }

    /**
     * 设置
     * @param deadline
     */
    public void setDeadline(Date deadline) {
        this.deadline = deadline;
    }

    /**
     * 获取
     * @return available
     */
    public Byte getAvailable() {
        return available;
    }

    /**
     * 设置
     * @param available
     */
    public void setAvailable(Byte available) {
        this.available = available;
    }

    /**
     * 获取
     * @return createTime
     */
    public Date getCreateTime() {
        return createTime;
    }

    /**
     * 设置
     * @param createTime
     */
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取
     * @return updateTime
     */
    public Date getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置
     * @param updateTime
     */
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }


}
