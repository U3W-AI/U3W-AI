package com.wx.fbsir.engine.playwright.login.persistence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import com.wx.fbsir.engine.playwright.login.model.LoginState;
import com.wx.fbsir.engine.playwright.util.SafePathResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 登录状态持久化工具类
 * 
 * <p>负责将登录状态保存到本地文件系统，以及从文件读取登录状态。
 * 
 * <p>文件存储结构（直接使用配置文件的data-dir）：
 * <pre>
 * {data-dir}/
 * ├── gitee/
 * │   ├── user-1/
 * │   │   └── login-state.json
 * │   └── user-2/
 * │       └── login-state.json
 * ├── deepseek/
 * │   └── user-1/
 * │       └── login-state.json
 * └── yuanqi/
 *     └── user-1/
 *         └── login-state.json
 * </pre>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>直接使用配置文件的data-dir作为根目录，与浏览器数据目录平级</li>
 *   <li>按平台和用户分目录存储，避免冲突</li>
 *   <li>使用JSON格式，便于调试和手动修改</li>
 *   <li>不保存锁文件，确保并发安全</li>
 *   <li>自动创建目录，简化使用</li>
 * </ul>
 * 
 * @author 15年高级Java开发工程师
 * @since 2026-02-03
 */
@Slf4j
@Component
public class LoginStatePersistence {
    
    private static final Logger log = LoggerFactory.getLogger(LoginStatePersistence.class);
    
    /**
     * Playwright配置属性（注入）
     */
    private static PlaywrightProperties properties;
    
    /**
     * 构造函数（Spring注入配置）
     */
    public LoginStatePersistence(PlaywrightProperties properties) {
        LoginStatePersistence.properties = properties;
    }
    
    /**
     * 获取登录状态根目录（直接使用data-dir）
     * 
     * @return 登录状态根目录路径
     */
    private static String getLoginStateRootDir() {
        if (properties == null) {
            // 降级方案：如果配置未注入，使用默认路径
            return "./data/playwright";
        }
        return properties.getDataDir();
    }
    
    /**
     * 登录状态文件名
     */
    private static final String LOGIN_STATE_FILE_NAME = "login-state.json";
    
    /**
     * 保存登录状态到本地文件
     * 
     * @param loginState 登录状态对象
     * @return 是否保存成功
     */
    public static boolean save(LoginState loginState) {
        if (loginState == null) {
            log.warn("[登录状态持久化] 保存失败：loginState为null");
            return false;
        }
        
        if (loginState.getPlatform() == null || loginState.getPlatform().isEmpty()) {
            log.warn("[登录状态持久化] 保存失败：platform为空");
            return false;
        }
        
        if (loginState.getUserId() == null || loginState.getUserId().isEmpty()) {
            log.warn("[登录状态持久化] 保存失败：userId为空");
            return false;
        }
        
        try {
            // 构建文件路径
            Path filePath = getLoginStateFilePath(loginState.getPlatform(), loginState.getUserId());
            
            // 确保目录存在
            Files.createDirectories(filePath.getParent());
            
            // 序列化为JSON（格式化输出，便于调试）
            String json = JSON.toJSONString(loginState, JSONWriter.Feature.PrettyFormat);
            
            // 先写同目录临时文件，再原子替换，避免进程中断留下半个 JSON。
            Path tempFile = Files.createTempFile(filePath.getParent(), "login-state-", ".tmp");
            try {
                Files.writeString(tempFile, json, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(tempFile, filePath,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
            
            log.info("[登录状态持久化] ✅ 保存成功 - 平台: {}, 用户: {}, 路径: {}, Cookies: {}, Origins: {}", 
                loginState.getPlatform(), 
                loginState.getUserId(), 
                filePath.toAbsolutePath(),
                loginState.getCookieCount(),
                loginState.getOriginCount());
            
            return true;
            
        } catch (IOException | IllegalArgumentException e) {
            log.error("[登录状态持久化] ❌ 保存失败 - 平台: {}, 用户: {}, 错误: {}", 
                loginState.getPlatform(), loginState.getUserId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 从本地文件加载登录状态
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 登录状态对象，不存在或加载失败返回null
     */
    public static LoginState load(String platform, String userId) {
        if (platform == null || platform.isEmpty()) {
            log.warn("[登录状态持久化] 加载失败：platform为空");
            return null;
        }
        
        if (userId == null || userId.isEmpty()) {
            log.warn("[登录状态持久化] 加载失败：userId为空");
            return null;
        }
        
        try {
            // 构建文件路径
            Path filePath = getLoginStateFilePath(platform, userId);
            
            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                log.debug("[登录状态持久化] 文件不存在 - 平台: {}, 用户: {}, 路径: {}", 
                    platform, userId, filePath.toAbsolutePath());
                return null;
            }
            
            // 读取文件内容
            String json = Files.readString(filePath);
            
            // 反序列化为LoginState对象
            LoginState loginState = JSON.parseObject(json, LoginState.class);
            
            log.info("[登录状态持久化] ✅ 加载成功 - 平台: {}, 用户: {}, Cookies: {}, Origins: {}, 保存时间: {}", 
                platform, userId, 
                loginState.getCookieCount(),
                loginState.getOriginCount(),
                loginState.getTimestamp() != null ? 
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(loginState.getTimestamp())) : "未知");
            
            return loginState;
            
        } catch (IOException | IllegalArgumentException e) {
            log.error("[登录状态持久化] ❌ 加载失败 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 检查登录状态文件是否存在
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否存在
     */
    public static boolean exists(String platform, String userId) {
        if (platform == null || platform.isEmpty() || userId == null || userId.isEmpty()) {
            return false;
        }
        
        final Path filePath;
        try {
            filePath = getLoginStateFilePath(platform, userId);
        } catch (IllegalArgumentException e) {
            log.warn("[登录状态持久化] 检查失败 - 平台或用户标识非法: {}", e.getMessage());
            return false;
        }
        boolean exists = Files.exists(filePath);
        
        log.debug("[登录状态持久化] 检查文件 - 平台: {}, 用户: {}, 存在: {}, 路径: {}", 
            platform, userId, exists, filePath.toAbsolutePath());
        
        return exists;
    }
    
    /**
     * 删除登录状态文件
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 是否删除成功
     */
    public static boolean delete(String platform, String userId) {
        if (platform == null || platform.isEmpty() || userId == null || userId.isEmpty()) {
            log.warn("[登录状态持久化] 删除失败：platform或userId为空");
            return false;
        }
        
        try {
            Path filePath = getLoginStateFilePath(platform, userId);
            
            if (!Files.exists(filePath)) {
                log.debug("[登录状态持久化] 文件不存在，无需删除 - 平台: {}, 用户: {}", platform, userId);
                return true;
            }
            
            Files.delete(filePath);
            log.info("[登录状态持久化] ✅ 删除成功 - 平台: {}, 用户: {}, 路径: {}", 
                platform, userId, filePath.toAbsolutePath());
            
            return true;
            
        } catch (IOException | IllegalArgumentException e) {
            log.error("[登录状态持久化] ❌ 删除失败 - 平台: {}, 用户: {}, 错误: {}", 
                platform, userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取登录状态文件路径
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 文件路径
     */
    private static Path getLoginStateFilePath(String platform, String userId) {
        return getLoginStateDirectory(platform, userId).resolve(LOGIN_STATE_FILE_NAME);
    }
    
    /**
     * 获取登录状态目录路径
     * 
     * @param platform 平台标识
     * @param userId 用户ID
     * @return 目录路径
     */
    public static Path getLoginStateDirectory(String platform, String userId) {
        String safePlatform = SafePathResolver.requireSafeSegment(platform, "platform");
        String safeUserId = SafePathResolver.requireSafeSegment(userId, "userId");
        return SafePathResolver.resolveUnder(
            Paths.get(getLoginStateRootDir()), safePlatform, "user-" + safeUserId);
    }
}
