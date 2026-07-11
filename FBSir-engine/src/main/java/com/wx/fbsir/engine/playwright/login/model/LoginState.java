package com.wx.fbsir.engine.playwright.login.model;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 通用登录状态数据模型
 * 
 * <p>封装浏览器登录状态的所有必要信息，包括：
 * <ul>
 *   <li>Cookies - HTTP Cookie信息</li>
 *   <li>LocalStorage - 本地存储数据</li>
 *   <li>SessionStorage - 会话存储数据</li>
 *   <li>Metadata - 登录元数据（用户名、平台、时间戳等）</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>平台无关：适用于Gitee、DeepSeek等所有需要登录持久化的平台</li>
 *   <li>完整性：包含所有可能影响登录状态的数据</li>
 *   <li>可序列化：支持JSON序列化，便于文件存储</li>
 * </ul>
 * 
 * @author 15年高级Java开发工程师
 * @since 2026-02-03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginState {
    
    /**
     * 平台标识（如：gitee、deepseek、kimi等）
     */
    private String platform;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 平台用户名（登录成功后获取）
     */
    private String userName;
    
    /**
     * 登录状态保存时间戳
     */
    private Long timestamp;
    
    /**
     * Cookies数组（Playwright StorageState格式）
     * 
     * <p>格式示例：
     * <pre>
     * [
     *   {
     *     "name": "session_id",
     *     "value": "xxx",
     *     "domain": ".gitee.com",
     *     "path": "/",
     *     "expires": 1234567890,
     *     "httpOnly": true,
     *     "secure": true,
     *     "sameSite": "Lax"
     *   }
     * ]
     * </pre>
     */
    private JSONArray cookies;
    
    /**
     * LocalStorage数据（按域名分组）
     * 
     * <p>格式示例：
     * <pre>
     * [
     *   {
     *     "origin": "https://chat.gitee.com",
     *     "localStorage": [
     *       {"name": "user_token", "value": "xxx"},
     *       {"name": "theme", "value": "dark"}
     *     ]
     *   }
     * ]
     * </pre>
     */
    private JSONArray origins;
    
    /**
     * 额外的元数据（平台特定信息）
     * 
     * <p>用于存储平台特定的登录信息，如：
     * <ul>
     *   <li>登录方式：扫码、密码、OAuth等</li>
     *   <li>会话ID：平台内部会话标识</li>
     *   <li>过期时间：登录状态预计过期时间</li>
     *   <li>Authorization：HTTP Authorization Header（如Bearer Token）</li>
     *   <li>自定义请求头：其他需要持久化的HTTP请求头</li>
     * </ul>
     * 
     * <p>metadata常用字段说明：
     * <pre>
     * {
     *   "saveTime": "2026-02-03 10:40:10",
     *   "userAgent": "Mozilla/5.0...",
     *   "authorization": "Bearer xxx",
     *   "x-csrf-token": "xxx",
     *   "loginMethod": "qrcode",
     *   "sessionId": "xxx"
     * }
     * </pre>
     */
    private Map<String, Object> metadata;
    
    /**
     * 登录状态是否有效
     */
    private Boolean valid;
    
    /**
     * 备注信息
     */
    private String remark;
    
    /**
     * 从Playwright StorageState JSON构建LoginState
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @param storageStateJson Playwright storageState JSON字符串
     * @return LoginState对象
     */
    public static LoginState fromStorageStateJson(String platform, String userId, String storageStateJson) {
        JSONObject storageState = com.alibaba.fastjson2.JSON.parseObject(storageStateJson);
        
        LoginState loginState = new LoginState();
        loginState.setPlatform(platform);
        loginState.setUserId(userId);
        loginState.setTimestamp(System.currentTimeMillis());
        loginState.setCookies(storageState.getJSONArray("cookies"));
        loginState.setOrigins(storageState.getJSONArray("origins"));
        loginState.setValid(true);
        return loginState;
    }
    
    /**
     * 转换为Playwright StorageState JSON格式
     * 
     * @return StorageState JSON对象
     */
    public JSONObject toStorageStateJson() {
        JSONObject storageState = new JSONObject();
        storageState.put("cookies", this.cookies != null ? this.cookies : new JSONArray());
        storageState.put("origins", this.origins != null ? this.origins : new JSONArray());
        return storageState;
    }
    
    /**
     * 检查登录状态是否过期
     * 
     * @param maxAgeMillis 最大有效期（毫秒）
     * @return 是否过期
     */
    public boolean isExpired(long maxAgeMillis) {
        if (timestamp == null) {
            return true;
        }
        return System.currentTimeMillis() - timestamp > maxAgeMillis;
    }
    
    /**
     * 获取Cookie数量
     * 
     * @return Cookie数量
     */
    public int getCookieCount() {
        return cookies != null ? cookies.size() : 0;
    }
    
    /**
     * 获取Origin数量（LocalStorage域名数）
     * 
     * @return Origin数量
     */
    public int getOriginCount() {
        return origins != null ? origins.size() : 0;
    }
    
    // 显式的getter/setter方法（解决Lombok处理问题）
    public String getPlatform() {
        return platform;
    }
    
    public void setPlatform(String platform) {
        this.platform = platform;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public JSONArray getCookies() {
        return cookies;
    }
    
    public void setCookies(JSONArray cookies) {
        this.cookies = cookies;
    }
    
    public JSONArray getOrigins() {
        return origins;
    }
    
    public void setOrigins(JSONArray origins) {
        this.origins = origins;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public Boolean getValid() {
        return valid;
    }
    
    public void setValid(Boolean valid) {
        this.valid = valid;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
}
