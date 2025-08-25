package com.cube.wechat.selfapp.officeaccount.entity;

import java.io.Serializable;

/**
 * 微信消息缓存实体
 * @author AspireLife
 * @date 2024年12月06日
 */
public class WeChatMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息ID
     */
    private String msgId;
    
    /**
     * 发送方OpenID
     */
    private String fromUserName;
    
    /**
     * 接收方微信号
     */
    private String toUserName;
    
    /**
     * 消息创建时间（时间戳）
     */
    private Long createTime;
    
    /**
     * 消息类型
     */
    private String msgType;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 用户UnionID
     */
    private String unionId;
    
    /**
     * 缓存创建时间
     */
    private Long cacheTime;
    
    public WeChatMessage() {}
    
    public WeChatMessage(String msgId, String fromUserName, String toUserName, 
                        Long createTime, String msgType, String content, String unionId) {
        this.msgId = msgId;
        this.fromUserName = fromUserName;
        this.toUserName = toUserName;
        this.createTime = createTime;
        this.msgType = msgType;
        this.content = content;
        this.unionId = unionId;
        this.cacheTime = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getMsgId() {
        return msgId;
    }
    
    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }
    
    public String getFromUserName() {
        return fromUserName;
    }
    
    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }
    
    public String getToUserName() {
        return toUserName;
    }
    
    public void setToUserName(String toUserName) {
        this.toUserName = toUserName;
    }
    
    public Long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
    
    public String getMsgType() {
        return msgType;
    }
    
    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getUnionId() {
        return unionId;
    }
    
    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }
    
    public Long getCacheTime() {
        return cacheTime;
    }
    
    public void setCacheTime(Long cacheTime) {
        this.cacheTime = cacheTime;
    }
    
    @Override
    public String toString() {
        return "WeChatMessage{" +
                "msgId='" + msgId + '\'' +
                ", fromUserName='" + fromUserName + '\'' +
                ", toUserName='" + toUserName + '\'' +
                ", createTime=" + createTime +
                ", msgType='" + msgType + '\'' +
                ", content='" + content + '\'' +
                ", unionId='" + unionId + '\'' +
                ", cacheTime=" + cacheTime +
                '}';
    }
} 