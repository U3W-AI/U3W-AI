package com.wx.fbsir.engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;

/**
 * Engine 配置验证器
 * 
 * 在应用启动后、连接主节点前验证必要配置，支持终端交互式输入和配置持久化
 * 
 * @author FBSir
 * @date 2026-01-15
 */
@Component
@Order(0)
public class EngineConfigValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EngineConfigValidator.class);
    
    private static final String CONFIG_FILE = "engine-runtime.properties";
    private static final String LOG_DIR = "logs";
    private static final String DATA_DIR = "data";
    
    @Autowired
    private EngineProperties engineProperties;

    @Override
    public void run(String... args) throws Exception {
        try {
            log.info("[配置验证] 开始验证配置...");
            
            // 1. 检查目录权限
            checkDirectoryPermissions();
            
            // 2. 验证配置
            validateAndPromptConfig();
            
            log.info("[配置验证] 配置验证完成");
            
        } catch (Exception e) {
            log.error("配置验证失败", e);
            System.err.println("\n❌ 配置验证失败: " + e.getMessage());
            System.err.println("请检查配置后重新启动");
            System.exit(1);
        }
    }
    
    /**
     * 检查目录权限
     */
    private void checkDirectoryPermissions() {
        checkAndCreateDirectory(LOG_DIR, "日志");
        checkAndCreateDirectory(DATA_DIR, "数据");
    }
    
    /**
     * 检查并创建目录
     */
    private void checkAndCreateDirectory(String dirName, String description) {
        try {
            Path dirPath = Paths.get(dirName);
            
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("✅ 已创建{}目录: {}", description, dirPath.toAbsolutePath());
            } else {
                Path testFile = dirPath.resolve(".permission_test");
                try {
                    Files.write(testFile, "test".getBytes(StandardCharsets.UTF_8));
                    Files.delete(testFile);
                } catch (IOException e) {
                    throw new IOException("没有写入权限");
                }
            }
        } catch (IOException e) {
            String errorMsg = String.format(
                "\n╔════════════════════════════════════════════════════════════╗\n" +
                "║  ⚠️  目录权限不足                                            ║\n" +
                "╠════════════════════════════════════════════════════════════╣\n" +
                "║  无法创建或写入 %s 目录                                ║\n" +
                "║  目录路径: %s                                          ║\n" +
                "║                                                            ║\n" +
                "║  解决方案：                                                ║\n" +
                "║  1. 使用管理员权限运行程序                                 ║\n" +
                "║  2. 或将程序移动到有写入权限的目录                         ║\n" +
                "║  3. 或手动创建目录并授予写入权限                           ║\n" +
                "╚════════════════════════════════════════════════════════════╝\n",
                description, dirName, Paths.get(dirName).toAbsolutePath()
            );
            System.err.println(errorMsg);
            log.error("目录权限检查失败: {}", e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * 验证并提示配置
     */
    private void validateAndPromptConfig() throws IOException {
        Properties runtimeConfig = loadRuntimeConfig();
        
        String wsUrl = engineProperties.getWsUrl();
        String hostId = engineProperties.getHostId();
        String engineToken = engineProperties.getEngineToken();

        if (engineToken == null || engineToken.length() < 32) {
            throw new IllegalStateException(
                "fbsir.engine.engine-token must be configured with at least 32 characters");
        }
        
        log.info("[配置验证] 从application.yml读取 - ws-url: '{}', host-id: '{}'", wsUrl, hostId);
        
        boolean wsUrlFromYaml = (wsUrl != null && !wsUrl.trim().isEmpty());
        boolean hostIdFromYaml = (hostId != null && !hostId.trim().isEmpty());
        
        String runtimeWsUrl = runtimeConfig.getProperty("ws-url");
        String runtimeHostId = runtimeConfig.getProperty("host-id");
        
        boolean hasRuntimeConfig = (runtimeWsUrl != null || runtimeHostId != null);
        
        // 如果application.yml中配置为空，但运行时配置存在，询问用户是否使用
        if (hasRuntimeConfig && (!wsUrlFromYaml || !hostIdFromYaml)) {
            System.out.println("\n╔════════════════════════════════════════════════════════════╗");
            System.out.println("║          检测到运行时配置                                  ║");
            System.out.println("╠════════════════════════════════════════════════════════════╣");
            if (runtimeWsUrl != null && !wsUrlFromYaml) {
                System.out.println("║  主节点地址: " + padRight(runtimeWsUrl, 42) + "║");
            }
            if (runtimeHostId != null && !hostIdFromYaml) {
                System.out.println("║  主机ID: " + padRight(runtimeHostId, 46) + "║");
            }
            System.out.println("╚════════════════════════════════════════════════════════════╝\n");
            
            Scanner scanner = new Scanner(System.in);
            System.out.print("是否使用运行时配置？(y/n，默认y): ");
            String useRuntime = scanner.nextLine().trim().toLowerCase();
            
            if (!"n".equals(useRuntime) && !"no".equals(useRuntime)) {
                if (wsUrl == null || wsUrl.trim().isEmpty()) {
                    wsUrl = runtimeWsUrl;
                    log.info("[配置验证] 使用运行时配置 ws-url: '{}'", wsUrl);
                }
                if (hostId == null || hostId.trim().isEmpty()) {
                    hostId = runtimeHostId;
                    log.info("[配置验证] 使用运行时配置 host-id: '{}'", hostId);
                }
            } else {
                System.out.println("\n✅ 将重新配置，运行时配置将被覆盖\n");
            }
        }
        
        boolean needPrompt = false;
        boolean wsUrlMissing = (wsUrl == null || wsUrl.trim().isEmpty());
        boolean hostIdMissing = (hostId == null || hostId.trim().isEmpty());
        
        if (wsUrlMissing || hostIdMissing) {
            needPrompt = true;
            printConfigPromptHeader();
            
            Scanner scanner = new Scanner(System.in);
            
            if (wsUrlMissing) {
                System.out.print("请输入主节点地址 (如 ws://localhost:8080/ws/engine): ");
                wsUrl = scanner.nextLine().trim();
                
                if (wsUrl.isEmpty()) {
                    throw new IllegalArgumentException("主节点地址不能为空");
                }
                
                if (!wsUrl.startsWith("ws://") && !wsUrl.startsWith("wss://")) {
                    System.out.print("是否启用HTTPS/WSS？(y/n，默认n): ");
                    String useHttps = scanner.nextLine().trim().toLowerCase();
                    if ("y".equals(useHttps) || "yes".equals(useHttps)) {
                        wsUrl = "wss://" + wsUrl;
                    } else {
                        wsUrl = "ws://" + wsUrl;
                    }
                }
                
                if (!wsUrl.contains("/ws/engine")) {
                    wsUrl = wsUrl.replaceAll("/$", "") + "/ws/engine";
                }
            }
            
            if (hostIdMissing) {
                System.out.println("\n⚠️  主机ID说明：");
                System.out.println("   - 一个主机ID同时只能被一个Engine实例使用");
                System.out.println("   - 如果没有主机ID，请联系管理员申请");
                System.out.print("\n请输入主机ID: ");
                hostId = scanner.nextLine().trim();
                
                if (hostId.isEmpty()) {
                    throw new IllegalArgumentException("主机ID不能为空");
                }
            }
            
            saveRuntimeConfig(wsUrl, hostId);
            
            engineProperties.setWsUrl(wsUrl);
            engineProperties.setHostId(hostId);
            
            System.out.println("\n✅ 配置已保存到 " + CONFIG_FILE);
            System.out.println("下次启动将自动使用此配置\n");
        }
        
        if (wsUrl == null || wsUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("主节点地址不能为空");
        }
        if (hostId == null || hostId.trim().isEmpty()) {
            throw new IllegalArgumentException("主机ID不能为空");
        }
        
        if (engineProperties.getWsUrl() == null || engineProperties.getWsUrl().trim().isEmpty()) {
            engineProperties.setWsUrl(wsUrl);
        }
        if (engineProperties.getHostId() == null || engineProperties.getHostId().trim().isEmpty()) {
            engineProperties.setHostId(hostId);
        }
        
        if (!needPrompt) {
            log.info("✅ 配置验证通过 - 主节点: {}, 主机ID: {}", wsUrl, hostId);
        }
    }
    
    private Properties loadRuntimeConfig() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        
        if (configFile.exists()) {
            try (InputStream is = new FileInputStream(configFile)) {
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                log.debug("已加载运行时配置: {}", CONFIG_FILE);
            } catch (IOException e) {
                log.warn("加载运行时配置失败: {}", e.getMessage());
            }
        }
        
        return props;
    }
    
    private void saveRuntimeConfig(String wsUrl, String hostId) throws IOException {
        Properties props = new Properties();
        props.setProperty("ws-url", wsUrl);
        props.setProperty("host-id", hostId);
        props.setProperty("last-update", String.valueOf(System.currentTimeMillis()));
        
        try (OutputStream os = new FileOutputStream(CONFIG_FILE)) {
            props.store(new OutputStreamWriter(os, StandardCharsets.UTF_8), 
                "FBSir Engine Runtime Configuration\n" +
                "Auto-generated, do not edit manually\n" +
                "If you need to change config, please edit application.yml");
        }
    }
    
    private void printConfigPromptHeader() {
        System.out.println("\n╔════════════════════════════════════════════════════════════╗");
        System.out.println("║          FBSir Engine 配置向导                          ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║  检测到配置文件中缺少必要配置，请按提示输入：             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");
    }
    
    private String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
}
