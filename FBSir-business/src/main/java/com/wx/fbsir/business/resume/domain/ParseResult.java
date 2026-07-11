package com.wx.fbsir.business.resume.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class ParseResult {

    private static final long serialVersionUID = 1L;
    /**
     * 解析结果
     */
    private String success;

    /**
     * 学校
     */
    private String school;

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


    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 解析内容（来自腾讯元器智能体）
     */
    private String parseContent;

    /**
     * 处理状态：0-处理中，1-已完成，2-失败
     */
    private Integer processStatus;


    public ParseResult() {
    }

    public ParseResult(long serialVersionUID, String success, String school, String cvName, String name, String phone, String mail, String shortlink, Date updateTime, String parseContent, Integer processStatus) {

        this.success = success;
        this.school = school;
        this.cvName = cvName;
        this.name = name;
        this.phone = phone;
        this.mail = mail;
        this.shortlink = shortlink;
        this.updateTime = updateTime;
        this.parseContent = parseContent;
        this.processStatus = processStatus;
    }

    /**
     * 获取
     * @return success
     */
    public String getSuccess() {
        return success;
    }

    /**
     * 设置
     * @param success
     */
    public void setSuccess(String success) {
        this.success = success;
    }

    /**
     * 获取
     * @return school
     */
    public String getSchool() {
        return school;
    }

    /**
     * 设置
     * @param school
     */
    public void setSchool(String school) {
        this.school = school;
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

    public String toString() {
        return "ParseResult{serialVersionUID = " + serialVersionUID + ", success = " + success + ", school = " + school + ", cvName = " + cvName + ", name = " + name + ", phone = " + phone + ", mail = " + mail + ", shortlink = " + shortlink + ", updateTime = " + updateTime + ", parseContent = " + parseContent + ", processStatus = " + processStatus + "}";
    }
}
