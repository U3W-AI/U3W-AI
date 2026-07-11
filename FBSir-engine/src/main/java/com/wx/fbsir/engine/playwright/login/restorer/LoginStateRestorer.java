package com.wx.fbsir.engine.playwright.login.restorer;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;
import com.wx.fbsir.engine.playwright.login.model.LoginState;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录状态恢复工具类
 * 
 * <p>负责将登录状态从LoginState对象恢复到浏览器会话中，包括：
 * <ul>
 *   <li>恢复Cookies到BrowserContext</li>
 *   <li>恢复LocalStorage到页面</li>
 *   <li>恢复SessionStorage到页面</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>平台无关：适用于所有需要登录恢复的平台</li>
 *   <li>容错性：单个Cookie或Storage恢复失败不影响整体</li>
 *   <li>详细日志：记录恢复过程，便于调试</li>
 * </ul>
 * 
 * @author 15年高级Java开发工程师
 * @since 2026-02-03
 */
@Slf4j
public class LoginStateRestorer {
    
    /**
     * 恢复登录状态到浏览器会话
     * 
     * @param session 浏览器会话
     * @param loginState 登录状态对象
     * @return 是否恢复成功
     */
    public static boolean restore(BrowserSession session, LoginState loginState) {
        if (session == null) {
            log.warn("[登录状态恢复] 恢复失败：session为null");
            return false;
        }
        
        if (loginState == null) {
            log.warn("[登录状态恢复] 恢复失败：loginState为null");
            return false;
        }
        
        try {
            log.info("[登录状态恢复] 开始恢复 - 平台: {}, 用户: {}", 
                loginState.getPlatform(), loginState.getUserId());
            
            BrowserContext context = session.getContext();
            
            // 1. 恢复Cookies
            int cookieCount = restoreCookies(context, loginState);
            
            // 2. 恢复LocalStorage和SessionStorage（需要在页面加载后执行）
            // 注意：这部分需要在页面导航后调用 restoreStorageToPage 方法
            
            log.info("[登录状态恢复] ✅ 恢复完成 - 平台: {}, 用户: {}, Cookies: {}/{}", 
                loginState.getPlatform(), 
                loginState.getUserId(),
                cookieCount,
                loginState.getCookieCount());
            
            return true;
            
        } catch (Exception e) {
            log.error("[登录状态恢复] ❌ 恢复失败 - 平台: {}, 用户: {}, 错误: {}", 
                loginState.getPlatform(), loginState.getUserId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 恢复Cookies到BrowserContext
     * 
     * @param context 浏览器上下文
     * @param loginState 登录状态对象
     * @return 成功恢复的Cookie数量
     */
    private static int restoreCookies(BrowserContext context, LoginState loginState) {
        JSONArray cookies = loginState.getCookies();
        if (cookies == null || cookies.isEmpty()) {
            log.debug("[登录状态恢复] 无Cookies需要恢复");
            return 0;
        }
        
        int successCount = 0;
        List<Cookie> cookieList = new ArrayList<>();
        
        for (int i = 0; i < cookies.size(); i++) {
            try {
                JSONObject cookieJson = cookies.getJSONObject(i);
                
                // 构建Playwright Cookie对象
                Cookie cookie = new Cookie(
                    cookieJson.getString("name"),
                    cookieJson.getString("value")
                );
                
                // 设置可选属性
                if (cookieJson.containsKey("domain")) {
                    cookie.setDomain(cookieJson.getString("domain"));
                }
                if (cookieJson.containsKey("path")) {
                    cookie.setPath(cookieJson.getString("path"));
                }
                if (cookieJson.containsKey("expires")) {
                    cookie.setExpires(cookieJson.getDoubleValue("expires"));
                }
                if (cookieJson.containsKey("httpOnly")) {
                    cookie.setHttpOnly(cookieJson.getBooleanValue("httpOnly"));
                }
                if (cookieJson.containsKey("secure")) {
                    cookie.setSecure(cookieJson.getBooleanValue("secure"));
                }
                if (cookieJson.containsKey("sameSite")) {
                    String sameSite = cookieJson.getString("sameSite");
                    if ("Strict".equalsIgnoreCase(sameSite)) {
                        cookie.setSameSite(SameSiteAttribute.STRICT);
                    } else if ("Lax".equalsIgnoreCase(sameSite)) {
                        cookie.setSameSite(SameSiteAttribute.LAX);
                    } else if ("None".equalsIgnoreCase(sameSite)) {
                        cookie.setSameSite(SameSiteAttribute.NONE);
                    }
                }
                
                cookieList.add(cookie);
                successCount++;
                
            } catch (Exception e) {
                log.warn("[登录状态恢复] Cookie恢复失败 - 索引: {}, 错误: {}", i, e.getMessage());
            }
        }
        
        // 批量添加Cookies
        if (!cookieList.isEmpty()) {
            try {
                context.addCookies(cookieList);
                log.debug("[登录状态恢复] 已添加 {} 个Cookies到BrowserContext", cookieList.size());
            } catch (Exception e) {
                log.error("[登录状态恢复] 批量添加Cookies失败: {}", e.getMessage(), e);
                return 0;
            }
        }
        
        return successCount;
    }
    
    /**
     * 在页面导航前恢复LocalStorage和SessionStorage（通过初始化脚本）
     * 
     * <p>此方法在页面导航前调用，通过BrowserContext的初始化脚本恢复Storage数据。
     * 这样可以确保页面加载时就能获取到LocalStorage/SessionStorage数据。
     * 
     * <p>使用场景：某些平台在页面加载前就需要LocalStorage数据
     * 
     * @param context 浏览器上下文
     * @param loginState 登录状态对象
     * @return 是否恢复成功
     */
    public static boolean restoreStorageBeforeNavigation(BrowserContext context, LoginState loginState) {
        if (context == null) {
            log.warn("[登录状态恢复] 恢复Storage失败：context为null");
            return false;
        }
        
        if (loginState == null || loginState.getOrigins() == null || loginState.getOrigins().isEmpty()) {
            log.debug("[登录状态恢复] 无Storage需要恢复");
            return true;
        }
        
        try {
            JSONArray origins = loginState.getOrigins();
            StringBuilder initScript = new StringBuilder();
            
            // 构建初始化脚本，在页面加载前执行
            for (int i = 0; i < origins.size(); i++) {
                JSONObject origin = origins.getJSONObject(i);
                
                // 恢复LocalStorage
                JSONArray localStorage = origin.getJSONArray("localStorage");
                if (localStorage != null && !localStorage.isEmpty()) {
                    for (int j = 0; j < localStorage.size(); j++) {
                        JSONObject item = localStorage.getJSONObject(j);
                        String name = item.getString("name");
                        String value = item.getString("value");
                        
                        initScript.append(String.format(
                            "window.localStorage.setItem('%s', '%s');",
                            escapeJavaScript(name),
                            escapeJavaScript(value)
                        ));
                    }
                }
                
                // 恢复SessionStorage
                JSONArray sessionStorage = origin.getJSONArray("sessionStorage");
                if (sessionStorage != null && !sessionStorage.isEmpty()) {
                    for (int j = 0; j < sessionStorage.size(); j++) {
                        JSONObject item = sessionStorage.getJSONObject(j);
                        String name = item.getString("name");
                        String value = item.getString("value");
                        
                        initScript.append(String.format(
                            "window.sessionStorage.setItem('%s', '%s');",
                            escapeJavaScript(name),
                            escapeJavaScript(value)
                        ));
                    }
                }
            }
            
            // 添加初始化脚本到BrowserContext
            if (initScript.length() > 0) {
                context.addInitScript(initScript.toString());
                log.debug("[登录状态恢复] ✅ 已添加Storage初始化脚本到BrowserContext");
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("[登录状态恢复] ❌ Storage初始化脚本添加失败 - 错误: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 恢复LocalStorage和SessionStorage到页面
     * 
     * <p>注意：此方法必须在页面导航完成后调用。
     * 如果需要在页面加载前就恢复Storage，请使用 restoreStorageBeforeNavigation() 方法。
     * 
     * @param page 页面对象
     * @param loginState 登录状态对象
     * @return 是否恢复成功
     */
    public static boolean restoreStorageToPage(Page page, LoginState loginState) {
        if (page == null) {
            log.warn("[登录状态恢复] 恢复Storage失败：page为null");
            return false;
        }
        
        if (loginState == null || loginState.getOrigins() == null || loginState.getOrigins().isEmpty()) {
            log.debug("[登录状态恢复] 无Storage需要恢复");
            return true;
        }
        
        try {
            JSONArray origins = loginState.getOrigins();
            String currentUrl = page.url();
            
            for (int i = 0; i < origins.size(); i++) {
                try {
                    JSONObject origin = origins.getJSONObject(i);
                    String originUrl = origin.getString("origin");
                    
                    // 只恢复当前页面所属域名的Storage
                    if (currentUrl != null && currentUrl.startsWith(originUrl)) {
                        
                        // 恢复LocalStorage
                        JSONArray localStorage = origin.getJSONArray("localStorage");
                        if (localStorage != null && !localStorage.isEmpty()) {
                            for (int j = 0; j < localStorage.size(); j++) {
                                JSONObject item = localStorage.getJSONObject(j);
                                String name = item.getString("name");
                                String value = item.getString("value");
                                
                                page.evaluate(String.format(
                                    "window.localStorage.setItem('%s', '%s')",
                                    escapeJavaScript(name),
                                    escapeJavaScript(value)
                                ));
                            }
                            log.debug("[登录状态恢复] 已恢复 {} 个LocalStorage项 - Origin: {}", 
                                localStorage.size(), originUrl);
                        }
                        
                        // 恢复SessionStorage
                        JSONArray sessionStorage = origin.getJSONArray("sessionStorage");
                        if (sessionStorage != null && !sessionStorage.isEmpty()) {
                            for (int j = 0; j < sessionStorage.size(); j++) {
                                JSONObject item = sessionStorage.getJSONObject(j);
                                String name = item.getString("name");
                                String value = item.getString("value");
                                
                                page.evaluate(String.format(
                                    "window.sessionStorage.setItem('%s', '%s')",
                                    escapeJavaScript(name),
                                    escapeJavaScript(value)
                                ));
                            }
                            log.debug("[登录状态恢复] 已恢复 {} 个SessionStorage项 - Origin: {}", 
                                sessionStorage.size(), originUrl);
                        }
                    }
                    
                } catch (Exception e) {
                    log.warn("[登录状态恢复] Origin恢复失败 - 索引: {}, 错误: {}", i, e.getMessage());
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("[登录状态恢复] ❌ Storage恢复失败 - 错误: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 转义JavaScript字符串（防止注入）
     * 
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeJavaScript(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
}
