package com.wx.fbsir.business.resume.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;


public class CV extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 简历ID（自动生成）
     */
    private String cvId;

    /**
     * 简历名称
     */
    private String cvName;

    /**
     * 解析后的姓名
     */
    private String name;

    /**
     * 解析后的手机号
     */
    private String phone;

    /**
     * 解析后的邮箱
     */
    private String mail;

    /**
     * 简历访问短链接
     */
    private String shortlink;

    /**
     * 解析内容（来自腾讯元器智能体）
     */
    private String parseContent;

    /**
     * 处理状态：0-处理中，1-已完成，2-失败
     */
    private Integer processStatus;

    /**
     * 简历文件实际存储位置
     */
    private String fileUrl;

    /**
     * 是否启用：0-禁用，1-启用
     */
    private Integer available;

    /**
     * 截止时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deadline;

    /**
     * 是否启用访问码：0-禁用，1-启用
     */
    private Integer accessCodeAvailable;


    public CV() {
    }

    public CV(long serialVersionUID, Long id, Long userId, String cvId, String cvName, String name, String phone, String mail, String shortlink, String parseContent, Integer processStatus, String fileUrl, Integer available, Date deadline, Integer accessCodeAvailable) {

        this.id = id;
        this.userId = userId;
        this.cvId = cvId;
        this.cvName = cvName;
        this.name = name;
        this.phone = phone;
        this.mail = mail;
        this.shortlink = shortlink;
        this.parseContent = parseContent;
        this.processStatus = processStatus;
        this.fileUrl = fileUrl;
        this.available = available;
        this.deadline = deadline;
        this.accessCodeAvailable = accessCodeAvailable;
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
     * @return userId
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置
     * @param userId
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取
     * @return cvId
     */
    public String getCvId() {
        return cvId;
    }

    /**
     * 设置
     * @param cvId
     */
    public void setCvId(String cvId) {
        this.cvId = cvId;
    }

    /**
     * 获取
     * @return cvName
     */
    public String getCvName() {
        return cvName;
    }

    /**
     * 设置
     * @param cvName
     */
    public void setCvName(String cvName) {
        this.cvName = cvName;
    }

    /**
     * 获取
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * 设置
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取
     * @return phone
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置
     * @param phone
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 获取
     * @return mail
     */
    public String getMail() {
        return mail;
    }

    /**
     * 设置
     * @param mail
     */
    public void setMail(String mail) {
        this.mail = mail;
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
     * @return parseContent
     */
    public String getParseContent() {
        return parseContent;
    }

    /**
     * 设置
     * @param parseContent
     */
    public void setParseContent(String parseContent) {
        this.parseContent = parseContent;
    }

    /**
     * 获取
     * @return processStatus
     */
    public Integer getProcessStatus() {
        return processStatus;
    }

    /**
     * 设置
     * @param processStatus
     */
    public void setProcessStatus(Integer processStatus) {
        this.processStatus = processStatus;
    }

    /**
     * 获取
     * @return fileUrl
     */
    public String getFileUrl() {
        return fileUrl;
    }

    /**
     * 设置
     * @param fileUrl
     */
    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    /**
     * 获取
     * @return available
     */
    public Integer getAvailable() {
        return available;
    }

    /**
     * 设置
     * @param available
     */
    public void setAvailable(Integer available) {
        this.available = available;
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
     * @return accessCodeAvailable
     */
    public Integer getAccessCodeAvailable() {
        return accessCodeAvailable;
    }

    /**
     * 设置
     * @param accessCodeAvailable
     */
    public void setAccessCodeAvailable(Integer accessCodeAvailable) {
        this.accessCodeAvailable = accessCodeAvailable;
    }

    public String toString() {
        return "CV{serialVersionUID = " + serialVersionUID + ", id = " + id + ", userId = " + userId + ", cvId = " + cvId + ", cvName = " + cvName + ", name = " + name + ", phone = " + phone + ", mail = " + mail + ", shortlink = " + shortlink + ", parseContent = " + parseContent + ", processStatus = " + processStatus + ", fileUrl = " + fileUrl + ", available = " + available + ", deadline = " + deadline + ", accessCodeAvailable = " + accessCodeAvailable + "}";
    }
}
