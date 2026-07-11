package com.wx.fbsir.engine.util;

import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;

/**
 * 根据主机资源推导 Engine 的线程配置。
 */
public class SystemPerformanceDetector {

    private static final Logger log = LoggerFactory.getLogger(SystemPerformanceDetector.class);

    public enum PerformanceLevel {
        HIGH,
        MEDIUM,
        LOW_CPU,
        MOBILE,
        VERY_LOW
    }

    public static class PerformanceConfig {
        private final PerformanceLevel level;
        private final int coreThreads;
        private final int maxThreads;
        private final int queueCapacity;
        private final String reason;

        public PerformanceConfig(PerformanceLevel level, int coreThreads, int maxThreads,
                                 int queueCapacity, String reason) {
            this.level = level;
            this.coreThreads = coreThreads;
            this.maxThreads = maxThreads;
            this.queueCapacity = queueCapacity;
            this.reason = reason;
        }

        public PerformanceLevel getLevel() {
            return level;
        }

        public int getCoreThreads() {
            return coreThreads;
        }

        public int getMaxThreads() {
            return maxThreads;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public String getReason() {
            return reason;
        }
    }

    public static PerformanceConfig detectPerformance() {
        int processors = Runtime.getRuntime().availableProcessors();
        long maxMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        String cpuModel = detectCPUModel();
        boolean isLowFrequencyCPU = isLowFrequencyCPU(cpuModel, processors);
        boolean isAppleSilicon = isAppleSilicon(cpuModel);
        boolean isMobileCPU = isMobileCPU(cpuModel);

        log.info("[性能检测] CPU核心: {}, 内存: {}MB, 型号: {}", processors, maxMemoryMB, cpuModel);

        if (processors <= 2 || maxMemoryMB < 3072) {
            return new PerformanceConfig(
                PerformanceLevel.VERY_LOW,
                2,
                2,
                500,
                String.format("极低性能 - %d核/%dMB", processors, maxMemoryMB)
            );
        }

        if (isLowFrequencyCPU && maxMemoryMB >= 8192) {
            int coreThreads = Math.max(2, processors / 2);
            int maxThreads = Math.max(4, processors);
            return new PerformanceConfig(
                PerformanceLevel.LOW_CPU,
                coreThreads,
                maxThreads,
                1000,
                String.format("低频CPU高内存 - %s (%d核/%dGB)", cpuModel, processors, maxMemoryMB / 1024)
            );
        }

        if (isAppleSilicon) {
            return new PerformanceConfig(
                PerformanceLevel.HIGH,
                processors,
                processors * 2,
                1000,
                String.format("Apple Silicon - %s (%d核)", cpuModel, processors)
            );
        }

        if (isMobileCPU && processors >= 4) {
            return new PerformanceConfig(
                PerformanceLevel.MOBILE,
                Math.min(4, processors),
                Math.min(8, processors * 2),
                1000,
                String.format("移动端CPU - %s (%d核)", cpuModel, processors)
            );
        }

        if (maxMemoryMB < 4096) {
            int coreThreads = Math.max(2, processors / 2);
            int maxThreads = Math.max(4, processors);
            return new PerformanceConfig(
                PerformanceLevel.VERY_LOW,
                coreThreads,
                maxThreads,
                500,
                String.format("低内存 - %dMB", maxMemoryMB)
            );
        }

        PerformanceLevel level = processors >= 8 ? PerformanceLevel.HIGH : PerformanceLevel.MEDIUM;
        return new PerformanceConfig(
            level,
            processors,
            processors * 2,
            1000,
            String.format("%s - %d核/%dGB",
                level == PerformanceLevel.HIGH ? "高性能" : "标准性能",
                processors, maxMemoryMB / 1024)
        );
    }

    private static String detectCPUModel() {
        return CpuModelDetector.detect();
    }

    private static boolean isLowFrequencyCPU(String cpuModel, int processors) {
        if (cpuModel == null || cpuModel.equals("Unknown")) {
            return false;
        }

        String model = cpuModel.toLowerCase();
        if (model.contains("n100") || model.contains("n95") || model.contains("n97")
            || model.contains("n200") || model.contains("n305")
            || model.contains("celeron") || model.contains("atom")
            || model.contains("pentium silver") || model.contains("pentium gold")) {
            return true;
        }

        if (model.contains("athlon") && model.contains("silver")
            || model.contains("3020e") || model.contains("3015e")) {
            return true;
        }

        return processors == 4 && (model.contains("1.0 ghz") || model.contains("1.1 ghz")
            || model.contains("1.2 ghz") || model.contains("1.3 ghz"));
    }

    private static boolean isAppleSilicon(String cpuModel) {
        if (cpuModel == null || cpuModel.equals("Unknown")) {
            String arch = System.getProperty("os.arch");
            return arch != null && arch.toLowerCase().contains("aarch64");
        }

        String model = cpuModel.toLowerCase();
        return model.contains("apple m1") || model.contains("apple m2")
            || model.contains("apple m3") || model.contains("apple m4");
    }

    private static boolean isMobileCPU(String cpuModel) {
        if (cpuModel == null || cpuModel.equals("Unknown")) {
            return false;
        }

        String model = cpuModel.toLowerCase();
        if (model.matches(".*i[357]-\\d{4,5}u.*") || model.matches(".*i[357]-\\d{4,5}y.*")) {
            return true;
        }

        return model.contains("ryzen") && (model.contains("u") || model.contains("15w"));
    }

    public static double getSystemLoad() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            return osBean.getSystemCpuLoad();
        } catch (Exception e) {
            return -1;
        }
    }
}
