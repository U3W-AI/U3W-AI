package com.wx.fbsir.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统性能监控工具类。
 */
public class SystemPerformanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(SystemPerformanceMonitor.class);

    private static String cachedCpuModel;
    private static Integer cachedCpuCores;
    private static Long cachedTotalMemory;
    private static Long cachedTotalDiskSpace;

    public static Map<String, Object> getHardwareInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            info.put("cpuModel", getCpuModel());
            info.put("cpuCores", getCpuCores());
            info.put("cpuLogicalCores", Runtime.getRuntime().availableProcessors());

            long totalMemory = getTotalMemory();
            info.put("totalMemoryMB", totalMemory / (1024 * 1024));
            info.put("totalMemoryGB", String.format("%.2f", totalMemory / (1024.0 * 1024 * 1024)));

            long totalDisk = getTotalDiskSpace();
            info.put("totalDiskMB", totalDisk / (1024 * 1024));
            info.put("totalDiskGB", String.format("%.2f", totalDisk / (1024.0 * 1024 * 1024)));
        } catch (Exception e) {
            log.warn("[性能监控] 获取硬件信息失败: {}", e.getMessage());
        }
        return info;
    }

    public static Map<String, Object> getPerformanceData() {
        Map<String, Object> data = new HashMap<>();
        try {
            double cpuUsage = getCpuUsage();
            data.put("cpuUsagePercent", String.format("%.1f", cpuUsage));

            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                long totalMemory = sunOsBean.getTotalPhysicalMemorySize();
                long freeMemory = sunOsBean.getFreePhysicalMemorySize();
                long usedMemory = totalMemory - freeMemory;
                double memoryUsage = totalMemory <= 0 ? 0.0 : (usedMemory * 100.0) / totalMemory;

                data.put("usedMemoryMB", usedMemory / (1024 * 1024));
                data.put("freeMemoryMB", freeMemory / (1024 * 1024));
                data.put("memoryUsagePercent", String.format("%.1f", memoryUsage));
            }

            Runtime runtime = Runtime.getRuntime();
            long jvmTotalMemory = runtime.totalMemory();
            long jvmFreeMemory = runtime.freeMemory();
            long jvmUsedMemory = jvmTotalMemory - jvmFreeMemory;
            long jvmMaxMemory = runtime.maxMemory();

            data.put("jvmUsedMemoryMB", jvmUsedMemory / (1024 * 1024));
            data.put("jvmMaxMemoryMB", jvmMaxMemory / (1024 * 1024));
            data.put("jvmUsagePercent", String.format("%.1f",
                jvmMaxMemory <= 0 ? 0.0 : (jvmUsedMemory * 100.0) / jvmMaxMemory));

            double systemLoad = osBean.getSystemLoadAverage();
            if (systemLoad >= 0) {
                data.put("systemLoad", String.format("%.2f", systemLoad));
            }

            File root = resolveDiskRoot();
            long totalDisk = root.getTotalSpace();
            long freeDisk = root.getFreeSpace();
            long usedDisk = totalDisk - freeDisk;
            double diskUsage = totalDisk <= 0 ? 0.0 : (usedDisk * 100.0) / totalDisk;

            data.put("usedDiskGB", String.format("%.2f", usedDisk / (1024.0 * 1024 * 1024)));
            data.put("freeDiskGB", String.format("%.2f", freeDisk / (1024.0 * 1024 * 1024)));
            data.put("diskUsagePercent", String.format("%.1f", diskUsage));
            data.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("[性能监控] 获取性能数据失败: {}", e.getMessage());
        }
        return data;
    }

    private static String getCpuModel() {
        if (cachedCpuModel != null) {
            return cachedCpuModel;
        }
        cachedCpuModel = CpuModelDetector.detect();
        return cachedCpuModel;
    }

    private static int getCpuCores() {
        if (cachedCpuCores != null) {
            return cachedCpuCores;
        }

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            String command;

            if (os.contains("mac")) {
                command = "sysctl -n hw.physicalcpu";
            } else if (os.contains("linux")) {
                command = "lscpu | grep '^Core(s) per socket:' | awk '{print $4}'";
            } else {
                cachedCpuCores = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
                return cachedCpuCores;
            }

            Process process = Runtime.getRuntime().exec(new String[] {"sh", "-c", command});
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    cachedCpuCores = Integer.parseInt(line.trim());
                    return cachedCpuCores;
                }
            }
        } catch (Exception e) {
            log.debug("[性能监控] 获取CPU核心数失败: {}", e.getMessage());
        }

        cachedCpuCores = Runtime.getRuntime().availableProcessors();
        return cachedCpuCores;
    }

    private static long getTotalMemory() {
        if (cachedTotalMemory != null) {
            return cachedTotalMemory;
        }

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                cachedTotalMemory = sunOsBean.getTotalPhysicalMemorySize();
                return cachedTotalMemory;
            }
        } catch (Exception e) {
            log.debug("[性能监控] 获取总内存失败: {}", e.getMessage());
        }

        cachedTotalMemory = Runtime.getRuntime().maxMemory();
        return cachedTotalMemory;
    }

    private static long getTotalDiskSpace() {
        if (cachedTotalDiskSpace != null) {
            return cachedTotalDiskSpace;
        }

        try {
            cachedTotalDiskSpace = resolveDiskRoot().getTotalSpace();
            return cachedTotalDiskSpace;
        } catch (Exception e) {
            log.debug("[性能监控] 获取磁盘容量失败: {}", e.getMessage());
            cachedTotalDiskSpace = 0L;
            return cachedTotalDiskSpace;
        }
    }

    private static double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                double cpuLoad = sunOsBean.getSystemCpuLoad();
                if (cpuLoad >= 0) {
                    return cpuLoad * 100.0;
                }
            }
        } catch (Exception e) {
            log.debug("[性能监控] 获取CPU使用率失败: {}", e.getMessage());
        }
        return -1.0;
    }

    private static File resolveDiskRoot() {
        try {
            Path root = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().getRoot();
            if (root != null) {
                return root.toFile();
            }
        } catch (Exception e) {
            log.debug("[性能监控] 解析磁盘根目录失败: {}", e.getMessage());
        }
        return new File("/");
    }

    public static void clearCache() {
        cachedCpuModel = null;
        cachedCpuCores = null;
        cachedTotalMemory = null;
        cachedTotalDiskSpace = null;
    }
}
