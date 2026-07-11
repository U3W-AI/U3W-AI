package com.wx.fbsir.business.resume.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

public class ResumeMonitor {

    /** 主键ID */
    private Long id;

    /** 简历访问短链接（关联 cv_storage.shortlink） */
    private String shortlink;

    /** 访问日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date date;

    /** 访问量 */
    private Integer count;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;


    public ResumeMonitor() {
    }

    public ResumeMonitor(Long id, String shortlink, Date date, Integer count, Date createTime, Date updateTime) {
        this.id = id;
        this.shortlink = shortlink;
        this.date = date;
        this.count = count;
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
     * @return date
     */
    public Date getDate() {
        return date;
    }

    /**
     * 设置
     * @param date
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * 获取
     * @return count
     */
    public Integer getCount() {
        return count;
    }

    /**
     * 设置
     * @param count
     */
    public void setCount(Integer count) {
        this.count = count;
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

    public String toString() {
        return "ResumeMonitor{id = " + id + ", shortlink = " + shortlink + ", date = " + date + ", count = " + count + ", createTime = " + createTime + ", updateTime = " + updateTime + "}";
    }
}
