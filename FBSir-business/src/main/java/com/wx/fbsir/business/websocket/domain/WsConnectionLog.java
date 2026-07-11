package com.wx.fbsir.business.websocket.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * WebSocket连接记录实体
 *
 * @author FBSir
 * @date 2025-12-15
 */
public class WsConnectionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sessionId;
    private String hostId;
    private String deviceId;
    private String engineVersion;
    private String remoteIp;
    private Integer remotePort;
    private String requestUri;
    private String osName;
    private String osVersion;
    private String javaVersion;
    private String hostname;
    private String macAddress;
    private LocalDateTime connectTime;
    private LocalDateTime registerTime;
    private LocalDateTime disconnectTime;
    private Long durationSeconds;
    private Integer status;
    private String rejectReason;
    private Integer closeCode;
    private String closeReason;
    private Long messageSent;
    private Long messageReceived;
    private Integer heartbeatCount;
    private Integer errorCount;
    private String lastError;
    private Integer delFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getRemoteIp() { return remoteIp; }
    public void setRemoteIp(String remoteIp) { this.remoteIp = remoteIp; }
    public Integer getRemotePort() { return remotePort; }
    public void setRemotePort(Integer remotePort) { this.remotePort = remotePort; }
    public String getRequestUri() { return requestUri; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }
    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public LocalDateTime getConnectTime() { return connectTime; }
    public void setConnectTime(LocalDateTime connectTime) { this.connectTime = connectTime; }
    public LocalDateTime getRegisterTime() { return registerTime; }
    public void setRegisterTime(LocalDateTime registerTime) { this.registerTime = registerTime; }
    public LocalDateTime getDisconnectTime() { return disconnectTime; }
    public void setDisconnectTime(LocalDateTime disconnectTime) { this.disconnectTime = disconnectTime; }
    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Integer getCloseCode() { return closeCode; }
    public void setCloseCode(Integer closeCode) { this.closeCode = closeCode; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public Long getMessageSent() { return messageSent; }
    public void setMessageSent(Long messageSent) { this.messageSent = messageSent; }
    public Long getMessageReceived() { return messageReceived; }
    public void setMessageReceived(Long messageReceived) { this.messageReceived = messageReceived; }
    public Integer getHeartbeatCount() { return heartbeatCount; }
    public void setHeartbeatCount(Integer heartbeatCount) { this.heartbeatCount = heartbeatCount; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
