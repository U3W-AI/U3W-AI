package com.wx.fbsir.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class CpuModelDetector {

    private static final Logger log = LoggerFactory.getLogger(CpuModelDetector.class);

    private CpuModelDetector() {
    }

    static String detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                return firstCpuModelLine(readCommandOutput("sysctl", "-n", "machdep.cpu.brand_string"));
            }
            if (os.contains("linux")) {
                return readLinuxCpuModel();
            }
            if (os.contains("win")) {
                return readWindowsCpuModel();
            }
        } catch (Exception e) {
            log.debug("[CPU信息] 获取CPU型号失败: {}", e.getMessage());
        }
        return "Unknown";
    }

    static String firstCpuModelLine(List<String> lines) {
        for (String line : lines) {
            String normalized = normalizeCpuModelLine(line);
            if (normalized != null) {
                return normalized;
            }
        }
        return "Unknown";
    }

    static String normalizeCpuModelLine(String line) {
        if (line == null) {
            return null;
        }

        String normalized = line.replace('\u0000', ' ').trim();
        if (normalized.isEmpty()) {
            return null;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("name".equals(lower) || "caption".equals(lower)) {
            return null;
        }

        if (lower.startsWith("name=")) {
            normalized = normalized.substring(5).trim();
            lower = normalized.toLowerCase(Locale.ROOT);
        }

        if (lower.startsWith("model name")) {
            int colonIndex = normalized.indexOf(':');
            if (colonIndex >= 0 && colonIndex + 1 < normalized.length()) {
                normalized = normalized.substring(colonIndex + 1).trim();
            }
        }

        return normalized.isEmpty() ? null : normalized;
    }

    private static String readLinuxCpuModel() throws Exception {
        Path cpuInfoPath = Path.of("/proc/cpuinfo");
        if (!Files.exists(cpuInfoPath)) {
            return "Unknown";
        }
        return firstCpuModelLine(Files.readAllLines(cpuInfoPath));
    }

    private static String readWindowsCpuModel() throws Exception {
        List<String[]> commands = List.of(
            new String[] {
                "powershell", "-NoProfile", "-Command",
                "Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name"
            },
            new String[] {
                "powershell", "-NoProfile", "-Command",
                "Get-WmiObject Win32_Processor | Select-Object -First 1 -ExpandProperty Name"
            },
            new String[] {"cmd", "/c", "wmic cpu get name"}
        );

        for (String[] command : commands) {
            String cpuModel = firstCpuModelLine(readCommandOutput(command));
            if (!"Unknown".equals(cpuModel)) {
                return cpuModel;
            }
        }
        return "Unknown";
    }

    private static List<String> readCommandOutput(String... command) throws Exception {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            List<String> lines = reader.lines().toList();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.debug("[CPU信息] 命令执行超时: {}", Arrays.toString(command));
                return List.of();
            }
            return lines;
        }
    }
}
