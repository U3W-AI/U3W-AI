package com.wx.fbsir.business.interviewbot.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 面试记录实体类
 * 对应数据库表：interview_record
 * * @author WxFbsir Team
 * @date 2026-01-22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InterviewRecord {
    /** 主键ID */
    private Long id;

    /** 文档唯一标识 */
    private String documentId;

    /** 姓名 */
    private String name;

    /** 学校 */
    private String school;

    /** 年级 */
    private String grade;

    /** 技术栈 */
    private String techStack;

    /** 简历摘要 */
    private String summary;

    /** 面试/录入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime interviewTime;

    /** 手机号 */
    private String phoneNumber;

    /** 身份证号 */
    private String idCard;

    /* --- 以下为数据库规范必备字段 --- */

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;

    /**
     * 简要信息格式化
     */
    public String toSimpleString() {
        return String.format("%s｜%s %s｜%s", name, school, grade, techStack);
    }

    /**
     * 详细信息格式化（含脱敏）
     */
    public String toDetailString() {
        return String.format(" 姓名 %s｜%s %s\n 技术栈 %s\n 摘要 %s\n 手机号 %s\n 身份证 %s\n 面试时间 %s",
                name, school, grade,
                techStack,
                summary,
                maskPhoneNumber(phoneNumber),
                maskIdCard(idCard),
                interviewTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
    }

    /**
     * 手机号脱敏：保留前3后4，中间4位掩盖
     */
    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1xxxx$2");
    }

    /**
     * 身份证脱敏：保留前6后4，中间掩盖
     */
    private String maskIdCard(String id) {
        if (id == null || id.length() < 15) {
            return id;
        }
        return id.replaceAll("(\\w{6})\\w+(\\w{4})", "$1xxxxxxxx$2");
    }
}