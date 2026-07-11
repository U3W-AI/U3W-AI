package com.wx.fbsir.engine.playwright.login.manager;

import com.alibaba.fastjson2.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.wx.fbsir.engine.playwright.login.model.LoginState;
import com.wx.fbsir.engine.playwright.login.persistence.LoginStatePersistence;
import com.wx.fbsir.engine.playwright.login.restorer.LoginStateRestorer;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录状态管理器（门面模式）
 * 
 * <p>统一管理登录状态的保存、加载、恢复、删除等操作，提供简洁的API。
 * 
 * <p>核心功能：
 * <ul>
 *   <li>保存登录状态：从BrowserSession提取并保存到文件</li>
 *   <li>恢复登录状态：从文件加载并注入到BrowserSession</li>
 *   <li>检查登录状态：判断是否存在有效的登录状态</li>
 *   <li>清除登录状态：删除保存的登录状态文件</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>
 * // 1. 登录成功后保存状态
 * LoginStateManager.saveLoginState(session, "gitee", userId, userName);
 * 
 * // 2. 下次使用时恢复状态
 * LoginStateManager.restoreLoginState(session, "gitee", userId);
 * 
 * // 3. 检查是否有保存的登录状态
 * boolean hasLogin = LoginStateManager.hasLoginState("gitee", userId);
 * 
 * // 4. 清除登录状态
 * LoginStateManager.clearLoginState("gitee", userId);
 * </pre>
 * 
 * @author 15年高级Java开发工程师
 * @since 2026-02-03
 */
@Slf4j
public class LoginStateManager {
    
    /**
     * 登录状态有效期策略：依赖平台自身管理
     * 
     * <p>不在框架层面检查有效期，由各平台自身的登录状态管理机制决定：
     * <ul>
     *   <li>如果平台管理严格，登录状态失效时，登录检测会返回未登录</li>
     *   <li>用户检测到未登录时，会自动调用登录逻辑重新登录</li>
     *   <li>框架只负责保存和恢复登录状态，不判断是否过期</li>
     * </ul>
     */
    
    /**
     * 保存登录状态
     * 
     * <p>从BrowserSession中提取完整的登录状态（Cookies、LocalStorage等），
     * 并保存到本地文件系统。
     * 
     * @param session 浏览器会话
     * @param platform 平台标识（如：gitee、deepseek、kimi）
     * @param userId 用户ID
     * @param userName 平台用户名（可选）
     * @return 是否保存成功
     */
    public static boolean saveLoginState(BrowserSession session, String platform, String userId, String userName) {
        if (session == null) {
            log.warn("[登录状态管理] 保存失败：session为null");
            return false;
        }
        
        if (platform == null || platform.isEmpty()) {
            log.warn("[登录状态管理] 保存失败：platform为空");
            return false;
        }
        
        if (userId == null || userId.isEmpty()) {
            log.warn("[登录状态管理] 保存失败：userId为空");
            return false;
        }
        
        try {
            log.info("[登录状态管理] 开始保存登录状态 - 平台: {}, 用户: {}, 用户名: {}", 
                platform, userId, userName);
            
            BrowserContext context = session.getContext();
            
            // 1. 获取StorageState（包含Cookies和Origins）
            String storageStateJson = context.storageState();
            
            // 2. 构建LoginState对象
            LoginState loginState = LoginState.fromStorageStateJson(platform, userId, storageStateJson);
            loginState.setUserName(userName);
            loginState.setValid(true);
            
            // 3. 添加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("saveTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            // 安全获取浏览器版本（context.browser()可能为null）
            if (context.browser() != null) {
                metadata.put("userAgent", context.browser().version());
            }
            loginState.setMetadata(metadata);
            
            // 4. 注意：Authorization Header需要在调用此方法后手动添加
            // 使用 addAuthorizationHeader() 方法添加Bearer Token等认证信息
            
            // 4. 持久化到文件
            boolean success = LoginStatePersistence.save(loginState);
            
            if (success) {
                log.info("[登录状态管理] ✅ 保存成功 - 平台: {}, 用户: {}, Cookies: {}, Origins: {}", 
                    platform, userId, loginState.getCookieCount(), loginState.getOriginCount());
            } else {
                log.error("[登录状态管理] ❌ 保存失败 - 平台: {}, 用户: {}", platform, userId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 保存异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 保存登录状态（不指定用户名）
     * 
     * @param session 浏览器会话
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否保存成功
     */
    public static boolean saveLoginState(BrowserSession session, String platform, String userId) {
        return saveLoginState(session, platform, userId, null);
    }
    
    /**
     * 恢复登录状态
     * 
     * <p>从本地文件加载登录状态，并注入到BrowserSession中。
     * 
     * @param session 浏览器会话
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否恢复成功
     */
    public static boolean restoreLoginState(BrowserSession session, String platform, String userId) {
        if (session == null) {
            log.warn("[登录状态管理] 恢复失败：session为null");
            return false;
        }
        
        if (platform == null || platform.isEmpty()) {
            log.warn("[登录状态管理] 恢复失败：platform为空");
            return false;
        }
        
        if (userId == null || userId.isEmpty()) {
            log.warn("[登录状态管理] 恢复失败：userId为空");
            return false;
        }
        
        try {
            log.info("[登录状态管理] 开始恢复登录状态 - 平台: {}, 用户: {}", platform, userId);
            
            // 1. 从文件加载LoginState
            LoginState loginState = LoginStatePersistence.load(platform, userId);
            
            if (loginState == null) {
                log.warn("[登录状态管理] ⚠️ 未找到登录状态 - 平台: {}, 用户: {}", platform, userId);
                return false;
            }
            
            // 2. 恢复到BrowserSession（不检查过期，由平台自身管理）
            boolean success = LoginStateRestorer.restore(session, loginState);
            
            if (success) {
                log.info("[登录状态管理] ✅ 恢复成功 - 平台: {}, 用户: {}, 用户名: {}, Cookies: {}", 
                    platform, userId, loginState.getUserName(), loginState.getCookieCount());
            } else {
                log.error("[登录状态管理] ❌ 恢复失败 - 平台: {}, 用户: {}", platform, userId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 恢复异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 恢复登录状态到页面（包含Storage）
     * 
     * <p>在页面导航完成后调用，恢复LocalStorage和SessionStorage。
     * 
     * @param page 页面对象
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否恢复成功
     */
    public static boolean restoreStorageToPage(Page page, String platform, String userId) {
        if (page == null) {
            log.warn("[登录状态管理] 恢复Storage失败：page为null");
            return false;
        }
        
        try {
            // 加载LoginState
            LoginState loginState = LoginStatePersistence.load(platform, userId);
            
            if (loginState == null) {
                log.debug("[登录状态管理] 未找到登录状态，跳过Storage恢复 - 平台: {}, 用户: {}", platform, userId);
                return false;
            }
            
            // 恢复Storage到页面
            return LoginStateRestorer.restoreStorageToPage(page, loginState);
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 恢复Storage异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查是否存在登录状态
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否存在
     */
    public static boolean hasLoginState(String platform, String userId) {
        return LoginStatePersistence.exists(platform, userId);
    }
    
    /**
     * 获取登录状态信息
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 登录状态对象，不存在返回null
     */
    public static LoginState getLoginState(String platform, String userId) {
        return LoginStatePersistence.load(platform, userId);
    }
    
    /**
     * 清除登录状态
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否清除成功
     */
    public static boolean clearLoginState(String platform, String userId) {
        log.info("[登录状态管理] 清除登录状态 - 平台: {}, 用户: {}", platform, userId);
        return LoginStatePersistence.delete(platform, userId);
    }
    
    /**
     * 标记登录状态为无效（不删除文件）
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否标记成功
     */
    public static boolean invalidateLoginState(String platform, String userId) {
        try {
            LoginState loginState = LoginStatePersistence.load(platform, userId);
            
            if (loginState == null) {
                log.debug("[登录状态管理] 登录状态不存在，无需标记 - 平台: {}, 用户: {}", platform, userId);
                return false;
            }
            
            loginState.setValid(false);
            loginState.setRemark("已标记为无效");
            
            boolean success = LoginStatePersistence.save(loginState);
            
            if (success) {
                log.info("[登录状态管理] ✅ 已标记为无效 - 平台: {}, 用户: {}", platform, userId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 标记无效异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 保存Authorization Header到登录状态
     * 
     * <p>用于保存Bearer Token、API Key等认证信息。
     * 这些信息会存储在metadata中，下次恢复时可以通过getAuthorizationHeader()获取。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>某些平台使用Bearer Token认证</li>
     *   <li>需要在HTTP请求头中添加Authorization信息</li>
     *   <li>登录后获取Token并保存，下次使用时恢复</li>
     * </ul>
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @param authorizationHeader Authorization Header值（如：Bearer xxx）
     * @return 是否保存成功
     */
    public static boolean saveAuthorizationHeader(String platform, String userId, String authorizationHeader) {
        try {
            LoginState loginState = LoginStatePersistence.load(platform, userId);
            
            if (loginState == null) {
                log.warn("[登录状态管理] 登录状态不存在，无法保存Authorization - 平台: {}, 用户: {}", platform, userId);
                return false;
            }
            
            if (loginState.getMetadata() == null) {
                loginState.setMetadata(new HashMap<>());
            }
            
            loginState.getMetadata().put("authorization", authorizationHeader);
            
            boolean success = LoginStatePersistence.save(loginState);
            
            if (success) {
                log.info("[登录状态管理] ✅ Authorization Header已保存 - 平台: {}, 用户: {}", platform, userId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 保存Authorization异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取保存的Authorization Header
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return Authorization Header值，不存在返回null
     */
    public static String getAuthorizationHeader(String platform, String userId) {
        try {
            LoginState loginState = LoginStatePersistence.load(platform, userId);
            
            if (loginState == null || loginState.getMetadata() == null) {
                return null;
            }
            
            Object auth = loginState.getMetadata().get("authorization");
            return auth != null ? auth.toString() : null;
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 获取Authorization异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 保存自定义HTTP请求头
     * 
     * <p>用于保存任何需要持久化的HTTP请求头（如X-CSRF-Token、X-API-Key等）。
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @param headerName 请求头名称
     * @param headerValue 请求头值
     * @return 是否保存成功
     */
    public static boolean saveCustomHeader(String platform, String userId, String headerName, String headerValue) {
        try {
            LoginState loginState = LoginStatePersistence.load(platform, userId);
            
            if (loginState == null) {
                log.warn("[登录状态管理] 登录状态不存在，无法保存自定义请求头 - 平台: {}, 用户: {}", platform, userId);
                return false;
            }
            
            if (loginState.getMetadata() == null) {
                loginState.setMetadata(new HashMap<>());
            }
            
            loginState.getMetadata().put(headerName, headerValue);
            
            boolean success = LoginStatePersistence.save(loginState);
            
            if (success) {
                log.debug("[登录状态管理] ✅ 自定义请求头已保存 - 平台: {}, 用户: {}, 请求头: {}", 
                    platform, userId, headerName);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 保存自定义请求头异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取保存的自定义HTTP请求头
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @param headerName 请求头名称
     * @return 请求头值，不存在返回null
     */
    public static String getCustomHeader(String platform, String userId, String headerName) {
        try {
            LoginState loginState = LoginStatePersistence.load(platform, userId);
            
            if (loginState == null || loginState.getMetadata() == null) {
                return null;
            }
            
            Object value = loginState.getMetadata().get(headerName);
            return value != null ? value.toString() : null;
            
        } catch (Exception e) {
            log.error("[登录状态管理] ❌ 获取自定义请求头异常 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return null;
        }
    }
}
