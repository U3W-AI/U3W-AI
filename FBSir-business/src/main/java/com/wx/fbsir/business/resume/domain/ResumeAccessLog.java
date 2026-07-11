package com.wx.fbsir.business.resume.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Date;

public class ResumeAccessLog {

    /** ID */
    private Long id;

    /** 短链接 */
    private String shortlink;

    /** IP */
    private String ip;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 访问设备 */
    private String device;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;


    public ResumeAccessLog() {
    }

    public ResumeAccessLog(Long id, String shortlink, String ip, String browser, String os, String device, Date createTime) {
        this.id = id;
        this.shortlink = shortlink;
        this.ip = ip;
        this.browser = browser;
        this.os = os;
        this.device = device;
        this.createTime = createTime;
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
     * @return ip
     */
    public String getIp() {
        return ip;
    }

    /**
     * 设置
     * @param ip
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * 获取
     * @return browser
     */
    public String getBrowser() {
        return browser;
    }

    /**
     * 设置
     * @param browser
     */
    public void setBrowser(String browser) {
        this.browser = browser;
    }

    /**
     * 获取
     * @return os
     */
    public String getOs() {
        return os;
    }

    /**
     * 设置
     * @param os
     */
    public void setOs(String os) {
        this.os = os;
    }

    /**
     * 获取
     * @return device
     */
    public String getDevice() {
        return device;
    }

    /**
     * 设置
     * @param device
     */
    public void setDevice(String device) {
        this.device = device;
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

    public String toString() {
        return "ResumeAccessLog{id = " + id + ", shortlink = " + shortlink + ", ip = " + ip + ", browser = " + browser + ", os = " + os + ", device = " + device + ", createTime = " + createTime + "}";
    }
}
