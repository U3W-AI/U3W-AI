package com.cube.wechat.selfapp.app.domain;

import java.time.LocalDateTime;

/**
 * 提示词配置实体类
 * 
 * @author fuchen
 * @version 1.0
 * @date 2025-01-14
 */
public class CallWord {
    
    /** 平台标识 */
    private String platformId;
    
    /** 提示词内容 */
    private String wordContent;
    
    /** 更新时间 */
    private LocalDateTime updateTime;

    public CallWord() {
    }

    public CallWord(String platformId, String wordContent) {
        this.platformId = platformId;
        this.wordContent = wordContent;
    }

    public String getPlatformId() {
        return platformId;
    }

    public void setPlatformId(String platformId) {
        this.platformId = platformId;
    }

    public String getWordContent() {
        return wordContent;
    }

    public void setWordContent(String wordContent) {
        this.wordContent = wordContent;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "CallWord{" +
                "platformId='" + platformId + '\'' +
                ", wordContent='" + wordContent + '\'' +
                ", updateTime=" + updateTime +
                '}';
    }
} 