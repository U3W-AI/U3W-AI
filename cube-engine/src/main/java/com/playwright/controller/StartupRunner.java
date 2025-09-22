package com.playwright.controller;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年02月06日 14:52
 */
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class StartupRunner {

    @Autowired
    private BrowserController browserController;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() throws InterruptedException {
        // 输出系统基本信息
        printSystemInfo();
//         原有的登录检查（已注释）
    }
    
    private void printSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        int processors = runtime.availableProcessors();
        long maxMemory = runtime.maxMemory() / 1024 / 1024; // MB
        long totalMemory = runtime.totalMemory() / 1024 / 1024; // MB
        long freeMemory = runtime.freeMemory() / 1024 / 1024; // MB
        long usedMemory = totalMemory - freeMemory;
        
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        
        System.out.println("========================================");
        System.out.println("🚀 U3W Cube Engine 系统信息");
        System.out.println("========================================");
        System.out.printf("💻 系统: %s (%s)%n", osName, osArch);
        System.out.printf("☕ Java: %s%n", javaVersion);
        System.out.printf("🔧 CPU核心数: %d | 最大线程数: %d%n", processors, processors * 2);
        System.out.printf("💾 内存: 已用 %dMB / 总计 %dMB / 最大 %dMB%n", usedMemory, totalMemory, maxMemory);
        System.out.println("========================================");
        System.out.println("✅ 应用启动完成，准备处理AI任务");
        System.out.println("========================================");
    }
}
